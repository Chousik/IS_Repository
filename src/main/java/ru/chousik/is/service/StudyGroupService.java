package ru.chousik.is.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.chousik.is.api.model.CoordinatesAddRequest;
import ru.chousik.is.api.model.LocationAddRequest;
import ru.chousik.is.api.model.PersonAddRequest;
import ru.chousik.is.api.model.StudyGroupAddRequest;
import ru.chousik.is.api.model.StudyGroupExpelledTotalResponse;
import ru.chousik.is.api.model.StudyGroupResponse;
import ru.chousik.is.api.model.StudyGroupShouldBeExpelledGroupResponse;
import ru.chousik.is.api.model.StudyGroupUpdateRequest;
import ru.chousik.is.cache.TrackCacheStats;
import ru.chousik.is.dto.mapper.CoordinatesMapper;
import ru.chousik.is.dto.mapper.LocationMapper;
import ru.chousik.is.dto.mapper.StudyGroupMapper;
import ru.chousik.is.entity.Coordinates;
import ru.chousik.is.entity.FormOfEducation;
import ru.chousik.is.entity.Location;
import ru.chousik.is.entity.Person;
import ru.chousik.is.entity.Semester;
import ru.chousik.is.entity.StudyGroup;
import ru.chousik.is.event.EntityChangeNotifier;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.exception.NotFoundException;
import ru.chousik.is.repository.CoordinatesRepository;
import ru.chousik.is.repository.LocationRepository;
import ru.chousik.is.repository.PersonRepository;
import ru.chousik.is.repository.StudyGroupRepository;
import ru.chousik.is.repository.projection.ShouldBeExpelledGroupProjection;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class StudyGroupService {

    private final StudyGroupRepository studyGroupRepository;
    private final CoordinatesRepository coordinatesRepository;
    private final PersonRepository personRepository;
    private final LocationRepository locationRepository;
    private final StudyGroupMapper studyGroupMapper;
    private final CoordinatesMapper coordinatesMapper;
    private final LocationMapper locationMapper;
    private final EntityChangeNotifier entityChangeNotifier;
    private final ConcurrentMap<String, Object> nameLocks = new ConcurrentHashMap<>();

    private static final Map<FormOfEducation, Long> MIN_STUDENTS = Map.of(
            FormOfEducation.DISTANCE_EDUCATION, 20L,
            FormOfEducation.EVENING_CLASSES, 6L,
            FormOfEducation.FULL_TIME_EDUCATION, 10L);

    private static final Map<FormOfEducation, Long> MAX_STUDENTS = Map.of(
            FormOfEducation.DISTANCE_EDUCATION, 100L,
            FormOfEducation.EVENING_CLASSES, 25L,
            FormOfEducation.FULL_TIME_EDUCATION, 30L);

    public Page<StudyGroupResponse> getAll(
            Pageable pageable, String sortBy, Sort.Direction direction) {
        Pageable sortedPageable = applySorting(pageable, sortBy, direction);
        Page<StudyGroup> page = studyGroupRepository.findAll(sortedPageable);
        return page.map(studyGroupMapper::toStudyGroupResponse);
    }

    @TrackCacheStats
    public StudyGroupResponse getById(Long id) {
        StudyGroup studyGroup = studyGroupRepository
                .findById(id)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Учебная группа с идентификатором %d не найдена".formatted(id)));
        return studyGroupMapper.toStudyGroupResponse(studyGroup);
    }

    @TrackCacheStats
    public List<StudyGroupResponse> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<StudyGroup> studyGroups = studyGroupRepository.findAllById(ids);
        return studyGroups.stream().map(studyGroupMapper::toStudyGroupResponse).toList();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public StudyGroupResponse create(StudyGroupAddRequest request) {
        if (request == null) {
            throw new BadRequestException("Тело запроса отсутствует");
        }
        validateCoordinatesInput(request.getCoordinatesId(), request.getCoordinates());
        validateGroupAdminInput(request.getGroupAdminId(), request.getGroupAdmin(), false);
        validateCourseValue(request.getCourse());
        validateStudentsBounds(request.getFormOfEducation(), request.getStudentsCount(), false);

        Coordinates coordinates = resolveCoordinatesForCreate(request.getCoordinatesId(), request.getCoordinates());
        if (coordinates == null) {
            throw new BadRequestException("Координаты обязательны для создания учебной группы");
        }

        Person groupAdmin = resolveGroupAdminForCreate(request.getGroupAdminId(), request.getGroupAdmin());
        ensureGroupAdminAvailable(groupAdmin, null);

        StudyGroup studyGroup = StudyGroup.builder()
                .name("")
                .coordinates(coordinates)
                .studentsCount(request.getStudentsCount())
                .expelledStudents(request.getExpelledStudents())
                .course(request.getCourse())
                .transferredStudents(request.getTransferredStudents())
                .formOfEducation(request.getFormOfEducation())
                .shouldBeExpelled(request.getShouldBeExpelled())
                .averageMark(request.getAverageMark())
                .semesterEnum(request.getSemesterEnum())
                .groupAdmin(groupAdmin)
                .build();

        assignGeneratedName(studyGroup, true);

        StudyGroup saved;
        try {
            saved = studyGroupRepository.save(studyGroup);
        } catch (DataIntegrityViolationException ex) {
            throw translateConstraintViolation(ex);
        }
        StudyGroupResponse response = studyGroupMapper.toStudyGroupResponse(saved);
        entityChangeNotifier.publish("STUDY_GROUP", "CREATED", response);
        return response;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public StudyGroupResponse update(Long id, StudyGroupUpdateRequest request) {
        validateUpdateRequest(request);

        StudyGroup studyGroup = studyGroupRepository
                .findById(id)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Учебная группа с идентификатором %d не найдена".formatted(id)));

        applyUpdates(studyGroup, request);

        StudyGroup saved;
        try {
            saved = studyGroupRepository.saveAndFlush(studyGroup);
        } catch (DataIntegrityViolationException ex) {
            throw translateConstraintViolation(ex);
        }
        StudyGroupResponse response = studyGroupMapper.toStudyGroupResponse(saved);
        if (tryDissolveGroupIfNeeded(saved)) {
            entityChangeNotifier.publish("STUDY_GROUP", "DELETED", response);
        } else {
            entityChangeNotifier.publish("STUDY_GROUP", "UPDATED", response);
        }
        return response;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<StudyGroupResponse> updateMany(List<Long> ids, StudyGroupUpdateRequest request) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        validateUpdateRequest(request);

        Collection<StudyGroup> studyGroups = studyGroupRepository.findAllById(ids);
        validateAllIdsPresent(ids, studyGroups);

        Coordinates resolvedCoordinates = null;
        if (request.getCoordinatesId() != null) {
            resolvedCoordinates = resolveExistingCoordinates(request.getCoordinatesId());
        }

        Person resolvedAdmin = null;
        if (request.getGroupAdminId() != null) {
            resolvedAdmin = resolveExistingPerson(request.getGroupAdminId());
        }

        for (StudyGroup studyGroup : studyGroups) {
            applyUpdates(studyGroup, request, resolvedCoordinates, resolvedAdmin);
        }

        List<StudyGroup> saved;
        try {
            saved = studyGroupRepository.saveAllAndFlush(studyGroups);
        } catch (DataIntegrityViolationException ex) {
            throw translateConstraintViolation(ex);
        }
        List<StudyGroupResponse> responses = new ArrayList<>();
        for (StudyGroup updated : saved) {
            StudyGroupResponse response = studyGroupMapper.toStudyGroupResponse(updated);
            responses.add(response);
            if (tryDissolveGroupIfNeeded(updated)) {
                entityChangeNotifier.publish("STUDY_GROUP", "DELETED", response);
            } else {
                entityChangeNotifier.publish("STUDY_GROUP", "UPDATED", response);
            }
        }
        return responses;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public StudyGroupResponse delete(Long id) {
        StudyGroup studyGroup = studyGroupRepository.findById(id).orElse(null);
        if (studyGroup == null) {
            entityChangeNotifier.publish("STUDY_GROUP", "DELETED", new DeletedPayload(id));
            return null;
        }
        Long coordinateId = studyGroup.getCoordinates() != null ? studyGroup.getCoordinates().getId() : null;
        studyGroupRepository.delete(studyGroup);
        studyGroupRepository.flush();
        if (coordinateId != null && !studyGroupRepository.existsByCoordinatesId(coordinateId)) {
            coordinatesRepository.deleteById(coordinateId);
        }
        StudyGroupResponse response = studyGroupMapper.toStudyGroupResponse(studyGroup);
        entityChangeNotifier.publish("STUDY_GROUP", "DELETED", response);
        return response;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deleteMany(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Collection<StudyGroup> studyGroups = studyGroupRepository.findAllById(ids);
        validateAllIdsPresent(ids, studyGroups);
        Set<Long> coordinateIds = studyGroups.stream()
                .map(StudyGroup::getCoordinates)
                .filter(Objects::nonNull)
                .map(Coordinates::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<StudyGroupResponse> responses = studyGroups.stream().map(studyGroupMapper::toStudyGroupResponse).toList();
        studyGroupRepository.deleteAllInBatch(studyGroups);
        if (!coordinateIds.isEmpty()) {
            List<Long> removableIds = coordinateIds.stream()
                    .filter(id -> !studyGroupRepository.existsByCoordinatesId(id))
                    .toList();
            if (!removableIds.isEmpty()) {
                coordinatesRepository.deleteAllByIdInBatch(removableIds);
            }
        }
        responses.forEach(response -> entityChangeNotifier.publish("STUDY_GROUP", "DELETED", response));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public long deleteAllBySemester(Semester semesterEnum) {
        if (semesterEnum == null) {
            throw new BadRequestException("Не указан semesterEnum");
        }
        List<StudyGroup> studyGroups = studyGroupRepository.findAllBySemesterEnum(semesterEnum);
        if (studyGroups.isEmpty()) {
            throw new NotFoundException(
                    "Учебные группы с семестром %s не найдены".formatted(semesterEnum));
        }
        Set<Long> coordinateIds = studyGroups.stream()
                .map(StudyGroup::getCoordinates)
                .filter(Objects::nonNull)
                .map(Coordinates::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<StudyGroupResponse> responses = studyGroups.stream().map(studyGroupMapper::toStudyGroupResponse).toList();
        studyGroupRepository.deleteAllInBatch(studyGroups);
        if (!coordinateIds.isEmpty()) {
            List<Long> removableIds = coordinateIds.stream()
                    .filter(id -> !studyGroupRepository.existsByCoordinatesId(id))
                    .toList();
            if (!removableIds.isEmpty()) {
                coordinatesRepository.deleteAllByIdInBatch(removableIds);
            }
        }
        responses.forEach(response -> entityChangeNotifier.publish("STUDY_GROUP", "DELETED", response));
        return responses.size();
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public StudyGroupResponse deleteOneBySemester(Semester semesterEnum) {
        if (semesterEnum == null) {
            throw new BadRequestException("Не указан semesterEnum");
        }
        StudyGroup studyGroup = studyGroupRepository
                .findFirstBySemesterEnum(semesterEnum)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Учебные группы с семестром %s не найдены".formatted(semesterEnum)));
        Long coordinateId = studyGroup.getCoordinates() != null ? studyGroup.getCoordinates().getId() : null;
        studyGroupRepository.delete(studyGroup);
        studyGroupRepository.flush();
        if (coordinateId != null && !studyGroupRepository.existsByCoordinatesId(coordinateId)) {
            coordinatesRepository.deleteById(coordinateId);
        }
        StudyGroupResponse response = studyGroupMapper.toStudyGroupResponse(studyGroup);
        entityChangeNotifier.publish("STUDY_GROUP", "DELETED", response);
        return response;
    }

    private record DeletedPayload(Long id) {
    }

    public List<StudyGroupShouldBeExpelledGroupResponse> groupByShouldBeExpelled() {
        List<ShouldBeExpelledGroupProjection> stats = studyGroupRepository.countGroupedByShouldBeExpelled();
        return stats.stream()
                .map(
                        item -> new StudyGroupShouldBeExpelledGroupResponse()
                                .shouldBeExpelled(item.getShouldBeExpelled())
                                .count(item.getTotal()))
                .toList();
    }

    public StudyGroupExpelledTotalResponse totalExpelledStudents() {
        Long total = studyGroupRepository.sumExpelledStudents();
        return new StudyGroupExpelledTotalResponse().totalExpelledStudents(total == null ? 0 : total);
    }

    private RuntimeException translateConstraintViolation(DataIntegrityViolationException ex) {
        String message = Optional.ofNullable(ex.getMostSpecificCause()).map(Throwable::getMessage).orElse("");
        if (message.contains("uq_coordinates_xy")) {
            return new BadRequestException("Координаты с такими значениями уже используются", ex);
        }
        if (message.contains("uq_study_group_admin")) {
            return new BadRequestException("Администратор уже закреплён за другой учебной группой", ex);
        }
        return new BadRequestException("Нарушено ограничение уникальности учебной группы", ex);
    }

    private void applyUpdates(StudyGroup studyGroup, StudyGroupUpdateRequest request) {
        applyUpdates(
                studyGroup,
                request,
                request.getCoordinatesId() != null
                        ? resolveExistingCoordinates(request.getCoordinatesId())
                        : null,
                request.getGroupAdminId() != null
                        ? resolveExistingPerson(request.getGroupAdminId())
                        : null);
    }

    private void applyUpdates(
            StudyGroup studyGroup,
            StudyGroupUpdateRequest request,
            Coordinates coordinatesFromId,
            Person groupAdminFromId) {
        boolean formChanged = false;
        boolean courseChanged = false;

        if (request.getFormOfEducation() != null) {
            formChanged = !request.getFormOfEducation().equals(studyGroup.getFormOfEducation());
            studyGroup.setFormOfEducation(request.getFormOfEducation());
        }

        if (request.getCourse() != null) {
            validateCourseValue(request.getCourse());
            courseChanged = !request.getCourse().equals(studyGroup.getCourse());
            studyGroup.setCourse(request.getCourse());
        }

        if (request.getStudentsCount() != null) {
            studyGroup.setStudentsCount(request.getStudentsCount());
        }

        if (request.getExpelledStudents() != null) {
            studyGroup.setExpelledStudents(request.getExpelledStudents());
        }

        if (request.getTransferredStudents() != null) {
            studyGroup.setTransferredStudents(request.getTransferredStudents());
        }

        if (request.getShouldBeExpelled() != null) {
            studyGroup.setShouldBeExpelled(request.getShouldBeExpelled());
        }

        if (request.getAverageMark() != null) {
            studyGroup.setAverageMark(request.getAverageMark());
        } else if (Boolean.TRUE.equals(request.getClearAverageMark())) {
            studyGroup.setAverageMark(null);
        }

        if (request.getSemesterEnum() != null) {
            studyGroup.setSemesterEnum(request.getSemesterEnum());
        }

        if (request.getCoordinatesId() != null) {
            Coordinates coordinates = coordinatesFromId != null
                    ? coordinatesFromId
                    : resolveExistingCoordinates(request.getCoordinatesId());
            studyGroup.setCoordinates(coordinates);
        } else if (request.getCoordinates() != null) {
            Coordinates coordinates = mapNewCoordinates(request.getCoordinates());
            studyGroup.setCoordinates(coordinates);
        }

        if (Boolean.TRUE.equals(request.getRemoveGroupAdmin())) {
            studyGroup.setGroupAdmin(null);
        } else if (request.getGroupAdminId() != null) {
            Person admin = groupAdminFromId != null
                    ? groupAdminFromId
                    : resolveExistingPerson(request.getGroupAdminId());
            ensureGroupAdminAvailable(admin, studyGroup.getId());
            studyGroup.setGroupAdmin(admin);
        } else if (request.getGroupAdmin() != null) {
            studyGroup.setGroupAdmin(buildPersonEntity(request.getGroupAdmin()));
        }

        validateStudentsBounds(studyGroup.getFormOfEducation(), studyGroup.getStudentsCount(), true);

        if (formChanged || courseChanged) {
            assignGeneratedName(studyGroup, true);
        }
    }

    private Coordinates resolveCoordinatesForCreate(
            Long coordinatesId, CoordinatesAddRequest coordinatesDto) {
        if (coordinatesId != null) {
            return resolveExistingCoordinates(coordinatesId);
        }
        if (coordinatesDto != null) {
            return mapNewCoordinates(coordinatesDto);
        }
        return null;
    }

    private Coordinates resolveExistingCoordinates(Long coordinatesId) {
        return coordinatesRepository
                .findById(coordinatesId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Координаты с идентификатором %d не найдены".formatted(coordinatesId)));
    }

    private Person resolveGroupAdminForCreate(Long groupAdminId, PersonAddRequest groupAdminDto) {
        if (groupAdminId != null) {
            return resolveExistingPerson(groupAdminId);
        }
        if (groupAdminDto != null) {
            return buildPersonEntity(groupAdminDto);
        }
        return null;
    }

    private Coordinates mapNewCoordinates(CoordinatesAddRequest coordinatesDto) {
        Coordinates coordinates = coordinatesMapper.toEntity(coordinatesDto);
        ensureCoordinatesUnique(coordinates);
        return coordinates;
    }

    private void ensureCoordinatesUnique(Coordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        boolean exists = coordinatesRepository.findByXAndY(coordinates.getX(), coordinates.getY()).isPresent();
        if (exists) {
            throw new BadRequestException(
                    "Координаты с такими значениями уже существуют. "
                            + "Выберите существующую запись по идентификатору или укажите уникальные значения");
        }
    }

    private Person resolveExistingPerson(Long personId) {
        return personRepository
                .findById(personId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Человек с идентификатором %d не найден".formatted(personId)));
    }

    private void ensureGroupAdminAvailable(Person person, Long currentGroupId) {
        if (person == null || person.getId() == null) {
            return;
        }
        boolean alreadyAssigned = currentGroupId == null
                ? studyGroupRepository.existsByGroupAdminId(person.getId())
                : studyGroupRepository.existsByGroupAdminIdAndIdNot(person.getId(), currentGroupId);
        if (alreadyAssigned) {
            throw new BadRequestException("Администратор уже закреплён за другой учебной группой");
        }
    }

    private Person buildPersonEntity(PersonAddRequest request) {
        validatePersonLocationInput(request.getLocationId(), request.getLocation());
        Location location = resolveLocationForPerson(request.getLocationId(), request.getLocation());
        Person person = Person.builder()
                .name(request.getName())
                .eyeColor(request.getEyeColor())
                .hairColor(request.getHairColor())
                .location(location)
                .height(request.getHeight())
                .weight(request.getWeight())
                .nationality(request.getNationality())
                .build();
        return person;
    }

    private Location resolveLocationForPerson(Long locationId, LocationAddRequest locationDto) {
        if (locationId != null) {
            return locationRepository
                    .findById(locationId)
                    .orElseThrow(
                            () -> new NotFoundException(
                                    "Локация с идентификатором %d не найдена".formatted(locationId)));
        }
        if (locationDto != null) {
            return locationMapper.toEntity(locationDto);
        }
        return null;
    }

    private void validateUpdateRequest(StudyGroupUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Тело запроса отсутствует");
        }

        boolean hasAnyField = request.getCoordinatesId() != null
                || request.getCoordinates() != null
                || request.getStudentsCount() != null
                || request.getExpelledStudents() != null
                || request.getTransferredStudents() != null
                || request.getFormOfEducation() != null
                || request.getCourse() != null
                || request.getShouldBeExpelled() != null
                || request.getAverageMark() != null
                || Boolean.TRUE.equals(request.getClearAverageMark())
                || request.getSemesterEnum() != null
                || request.getGroupAdminId() != null
                || request.getGroupAdmin() != null
                || Boolean.TRUE.equals(request.getRemoveGroupAdmin());

        if (!hasAnyField) {
            throw new BadRequestException("Не переданы поля для обновления учебной группы");
        }

        if (request.getClearAverageMark() != null
                && request.getAverageMark() != null
                && request.getClearAverageMark()) {
            throw new BadRequestException("Нельзя одновременно задавать и очищать поле averageMark");
        }

        if (request.getCourse() != null) {
            validateCourseValue(request.getCourse());
        }

        validateCoordinatesInput(request.getCoordinatesId(), request.getCoordinates());
        validateGroupAdminInput(
                request.getGroupAdminId(),
                request.getGroupAdmin(),
                Boolean.TRUE.equals(request.getRemoveGroupAdmin()));
    }

    private void validateCoordinatesInput(Long coordinatesId, CoordinatesAddRequest coordinatesDto) {
        if (coordinatesId != null && coordinatesDto != null) {
            throw new BadRequestException(
                    "Нельзя одновременно указать идентификатор координат и данные новых координат");
        }
    }

    private void validateGroupAdminInput(
            Long personId, PersonAddRequest personDto, boolean removeRequested) {
        if (personId != null && personDto != null) {
            throw new BadRequestException(
                    "Нельзя одновременно указать идентификатор администратора и данные нового администратора");
        }
        if (removeRequested && (personId != null || personDto != null)) {
            throw new BadRequestException(
                    "Нельзя удалить администратора группы и одновременно передавать его данные");
        }
    }

    private void validatePersonLocationInput(Long locationId, LocationAddRequest locationDto) {
        if (locationId != null && locationDto != null) {
            throw new BadRequestException(
                    "Нельзя одновременно указать идентификатор локации и данные новой локации");
        }
    }

    private void validateCourseValue(Integer course) {
        if (course == null) {
            throw new BadRequestException("Поле course не может быть пустым");
        }
        if (course < 1) {
            throw new BadRequestException("Курс должен быть положительным числом");
        }
    }

    private void validateStudentsBounds(
            FormOfEducation formOfEducation, Long studentsCount, boolean allowBelowMinimum) {
        if (formOfEducation == null) {
            throw new BadRequestException("Поле formOfEducation обязательно для применения ограничений");
        }
        if (studentsCount == null) {
            throw new BadRequestException("Поле studentsCount не может быть пустым");
        }
        Long min = MIN_STUDENTS.get(formOfEducation);
        Long max = MAX_STUDENTS.get(formOfEducation);
        if (min != null && !allowBelowMinimum && studentsCount < min) {
            throw new BadRequestException(
                    "Количество студентов не может быть меньше %d для формы %s"
                            .formatted(min, formOfEducation));
        }
        if (max != null && studentsCount > max) {
            throw new BadRequestException(
                    "Количество студентов не может превышать %d для формы %s"
                            .formatted(max, formOfEducation));
        }
    }

    private Pageable applySorting(Pageable pageable, String sortBy, Sort.Direction direction) {
        String sortField = Objects.requireNonNullElse(sortBy, "id");
        Sort.Direction sortDirection = direction == null ? Sort.Direction.ASC : direction;
        try {
            Sort sort = Sort.by(sortDirection, sortField);
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        } catch (PropertyReferenceException e) {
            throw new BadRequestException("Неизвестное поле сортировки '%s'".formatted(sortField), e);
        }
    }

    private void validateAllIdsPresent(
            List<Long> requestedIds, Collection<StudyGroup> foundEntities) {
        Set<Long> foundIds = foundEntities.stream().map(StudyGroup::getId).collect(Collectors.toSet());
        List<Long> missing = requestedIds.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new NotFoundException(
                    "Учебные группы с идентификаторами %s не найдены".formatted(missing));
        }
    }

    private boolean tryDissolveGroupIfNeeded(StudyGroup studyGroup) {
        Long studentsCount = studyGroup.getStudentsCount();
        Long minAllowed = MIN_STUDENTS.get(studyGroup.getFormOfEducation());
        if (studentsCount == null || minAllowed == null || studentsCount >= minAllowed) {
            return false;
        }

        List<FormOfEducation> allowedTargets = resolveAllowedTargetForms(studyGroup.getFormOfEducation());
        if (allowedTargets.isEmpty()) {
            return false;
        }

        List<StudyGroup> candidates = studyGroupRepository
                .findByCourseAndFormOfEducationIn(studyGroup.getCourse(), allowedTargets)
                .stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), studyGroup.getId()))
                .sorted(Comparator.comparingLong(this::availableCapacity).reversed())
                .toList();

        long remaining = studentsCount;
        List<StudyGroup> changedGroups = new ArrayList<>();
        for (StudyGroup candidate : candidates) {
            long capacity = availableCapacity(candidate);
            if (capacity <= 0) {
                continue;
            }
            long delta = Math.min(capacity, remaining);
            candidate.setStudentsCount(candidate.getStudentsCount() + delta);
            remaining -= delta;
            changedGroups.add(candidate);
            if (remaining == 0) {
                break;
            }
        }

        if (remaining > 0) {
            if (candidates.isEmpty()) {
                return false;
            }
            StudyGroup overflowTarget = candidates.get(0);
            overflowTarget.setStudentsCount(overflowTarget.getStudentsCount() + remaining);
            if (!changedGroups.contains(overflowTarget)) {
                changedGroups.add(overflowTarget);
            }
            remaining = 0;
        }

        List<StudyGroup> spawnedGroups = new ArrayList<>();
        for (StudyGroup changed : changedGroups) {
            spawnedGroups.addAll(splitOverloadedGroupIfNeeded(changed));
        }

        studyGroupRepository.saveAll(changedGroups);
        studyGroupRepository.flush();
        changedGroups.stream()
                .map(studyGroupMapper::toStudyGroupResponse)
                .forEach(response -> entityChangeNotifier.publish("STUDY_GROUP", "UPDATED", response));

        if (!spawnedGroups.isEmpty()) {
            List<StudyGroupResponse> createdResponses = new ArrayList<>();
            for (StudyGroup splitGroup : spawnedGroups) {
                assignGeneratedName(splitGroup, true);
                StudyGroup persisted = studyGroupRepository.saveAndFlush(splitGroup);
                createdResponses.add(studyGroupMapper.toStudyGroupResponse(persisted));
            }
            createdResponses.forEach(
                    response -> entityChangeNotifier.publish("STUDY_GROUP", "CREATED", response));
        }

        Long coordinateId = studyGroup.getCoordinates() != null ? studyGroup.getCoordinates().getId() : null;
        studyGroupRepository.delete(studyGroup);
        studyGroupRepository.flush();
        if (coordinateId != null && !studyGroupRepository.existsByCoordinatesId(coordinateId)) {
            coordinatesRepository.deleteById(coordinateId);
        }
        return true;
    }

    private List<StudyGroup> splitOverloadedGroupIfNeeded(StudyGroup group) {
        Long max = MAX_STUDENTS.get(group.getFormOfEducation());
        Long min = MIN_STUDENTS.get(group.getFormOfEducation());
        Long total = group.getStudentsCount();
        if (max == null || total == null || total <= max) {
            return List.of();
        }

        long resolvedMin = min == null ? 1 : min;
        List<Long> partitions = partitionStudents(total, resolvedMin, max);
        Iterator<Long> iterator = partitions.iterator();
        group.setStudentsCount(iterator.next());

        List<StudyGroup> splitGroups = new ArrayList<>();
        int offset = 1;
        while (iterator.hasNext()) {
            splitGroups.add(cloneGroupForSplit(group, iterator.next(), offset++));
        }
        return splitGroups;
    }

    private StudyGroup cloneGroupForSplit(StudyGroup template, long studentsCount, int offset) {
        Coordinates coordinates = cloneCoordinates(template.getCoordinates(), offset);
        StudyGroup clone = StudyGroup.builder()
                .name("")
                .coordinates(coordinates)
                .studentsCount(studentsCount)
                .expelledStudents(template.getExpelledStudents())
                .course(template.getCourse())
                .transferredStudents(template.getTransferredStudents())
                .formOfEducation(template.getFormOfEducation())
                .shouldBeExpelled(template.getShouldBeExpelled())
                .averageMark(template.getAverageMark())
                .semesterEnum(template.getSemesterEnum())
                .groupAdmin(null)
                .build();
        return clone;
    }

    private List<Long> partitionStudents(long total, long min, long max) {
        List<Long> parts = new ArrayList<>();
        long remaining = total;
        while (remaining > 0) {
            long groupsLeft = (long) Math.ceil((double) remaining / (double) max);
            long minReserve = min * (Math.max(0, groupsLeft - 1));
            long candidate = Math.min(max, remaining - minReserve);
            if (candidate < min && remaining > 0) {
                candidate = Math.min(remaining, min);
            }
            if (candidate <= 0) {
                candidate = remaining;
            }
            parts.add(candidate);
            remaining -= candidate;
        }
        return parts;
    }

    private Coordinates cloneCoordinates(Coordinates source, int offset) {
        if (source == null) {
            throw new BadRequestException(
                    "Невозможно клонировать координаты для разделения группы: запись отсутствует");
        }
        long candidateX = source.getX() + offset;
        Float baseY = source.getY();
        while (coordinatesRepository.findByXAndY(candidateX, baseY).isPresent()) {
            candidateX++;
        }
        return Coordinates.builder().x(candidateX).y(baseY).build();
    }

    private long availableCapacity(StudyGroup studyGroup) {
        Long max = MAX_STUDENTS.get(studyGroup.getFormOfEducation());
        if (max == null || studyGroup.getStudentsCount() == null) {
            return 0;
        }
        return Math.max(0, max - studyGroup.getStudentsCount());
    }

    private List<FormOfEducation> resolveAllowedTargetForms(FormOfEducation source) {
        return switch (source) {
            case FULL_TIME_EDUCATION -> List.of(
                    FormOfEducation.FULL_TIME_EDUCATION,
                    FormOfEducation.DISTANCE_EDUCATION,
                    FormOfEducation.EVENING_CLASSES);
            case EVENING_CLASSES -> List.of(
                    FormOfEducation.FULL_TIME_EDUCATION, FormOfEducation.DISTANCE_EDUCATION);
            case DISTANCE_EDUCATION -> List.of(FormOfEducation.DISTANCE_EDUCATION);
        };
    }

    private void assignGeneratedName(StudyGroup studyGroup, boolean regenerateSequence) {
        FormOfEducation form = studyGroup.getFormOfEducation();
        int course = studyGroup.getCourse();
        Object lock = nameLocks.computeIfAbsent(nameLockKey(form, course), key -> new Object());
        synchronized (lock) {
            if (regenerateSequence || studyGroup.getSequenceNumber() <= 0) {
                int nextSequence = studyGroupRepository.findMaxSequenceNumber(form, course) + 1;
                studyGroup.setSequenceNumber(nextSequence);
            }
            String prefix = resolveFormPrefix(form);
            String suffix = String.format("%02d", studyGroup.getSequenceNumber());
            studyGroup.setName("%s-%d-%s".formatted(prefix, course, suffix));
        }
    }

    private String nameLockKey(FormOfEducation form, int course) {
        return form.name() + "-" + course;
    }

    private String resolveFormPrefix(FormOfEducation form) {
        return switch (form) {
            case DISTANCE_EDUCATION -> "DE";
            case FULL_TIME_EDUCATION -> "FTE";
            case EVENING_CLASSES -> "EV";
        };
    }
}
