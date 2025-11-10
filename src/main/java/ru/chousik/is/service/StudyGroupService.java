package ru.chousik.is.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.chousik.is.dto.mapper.CoordinatesMapper;
import ru.chousik.is.dto.mapper.LocationMapper;
import ru.chousik.is.dto.mapper.StudyGroupMapper;
import ru.chousik.is.dto.request.CoordinatesAddRequest;
import ru.chousik.is.dto.request.LocationAddRequest;
import ru.chousik.is.dto.request.PersonAddRequest;
import ru.chousik.is.dto.request.StudyGroupAddRequest;
import ru.chousik.is.dto.request.StudyGroupUpdateRequest;
import ru.chousik.is.dto.response.StudyGroupExpelledTotalResponse;
import ru.chousik.is.dto.response.StudyGroupResponse;
import ru.chousik.is.dto.response.StudyGroupShouldBeExpelledGroupResponse;
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
import ru.chousik.is.repository.StudyGroupRepository.ShouldBeExpelledGroupProjection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

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
            FormOfEducation.FULL_TIME_EDUCATION, 10L
    );

    private static final Map<FormOfEducation, Long> MAX_STUDENTS = Map.of(
            FormOfEducation.DISTANCE_EDUCATION, 100L,
            FormOfEducation.EVENING_CLASSES, 25L,
            FormOfEducation.FULL_TIME_EDUCATION, 30L
    );

    public Page<StudyGroupResponse> getAll(Pageable pageable, String sortBy, Sort.Direction direction) {
        Pageable sortedPageable = applySorting(pageable, sortBy, direction);
        Page<StudyGroup> page = studyGroupRepository.findAll(sortedPageable);
        return page.map(studyGroupMapper::toStudyGroupResponse);
    }

    public StudyGroupResponse getById(Long id) {
        StudyGroup studyGroup = studyGroupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Учебная группа с идентификатором %d не найдена".formatted(id)));
        return studyGroupMapper.toStudyGroupResponse(studyGroup);
    }

    public List<StudyGroupResponse> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<StudyGroup> studyGroups = studyGroupRepository.findAllById(ids);
        return studyGroups.stream()
                .map(studyGroupMapper::toStudyGroupResponse)
                .toList();
    }

    @Transactional
    public StudyGroupResponse create(StudyGroupAddRequest request) {
        if (request == null) {
            throw new BadRequestException("Тело запроса отсутствует");
        }
        validateCoordinatesInput(request.coordinatesId(), request.coordinates());
        validateGroupAdminInput(request.groupAdminId(), request.groupAdmin(), false);
        validateCourseValue(request.course());
        validateStudentsBounds(request.formOfEducation(), request.studentsCount(), false);

        Coordinates coordinates = resolveCoordinatesForCreate(request.coordinatesId(), request.coordinates());
        if (coordinates == null) {
            throw new BadRequestException("Координаты обязательны для создания учебной группы");
        }

        Person groupAdmin = resolveGroupAdminForCreate(request.groupAdminId(), request.groupAdmin());
        ensureGroupAdminAvailable(groupAdmin, null);

        StudyGroup studyGroup = StudyGroup.builder()
                .name("")
                .coordinates(coordinates)
                .studentsCount(request.studentsCount())
                .expelledStudents(request.expelledStudents())
                .course(request.course())
                .transferredStudents(request.transferredStudents())
                .formOfEducation(request.formOfEducation())
                .shouldBeExpelled(request.shouldBeExpelled())
                .averageMark(request.averageMark())
                .semesterEnum(request.semesterEnum())
                .groupAdmin(groupAdmin)
                .build();

        assignGeneratedName(studyGroup, true);

        StudyGroup saved = studyGroupRepository.save(studyGroup);
        StudyGroupResponse response = studyGroupMapper.toStudyGroupResponse(saved);
        entityChangeNotifier.publish("STUDY_GROUP", "CREATED", response);
        return response;
    }

    @Transactional
    public StudyGroupResponse update(Long id, StudyGroupUpdateRequest request) {
        validateUpdateRequest(request);

        StudyGroup studyGroup = studyGroupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Учебная группа с идентификатором %d не найдена".formatted(id)));

        applyUpdates(studyGroup, request);

        StudyGroup saved = studyGroupRepository.saveAndFlush(studyGroup);
        StudyGroupResponse response = studyGroupMapper.toStudyGroupResponse(saved);
        if (tryDissolveGroupIfNeeded(saved)) {
            entityChangeNotifier.publish("STUDY_GROUP", "DELETED", response);
        } else {
            entityChangeNotifier.publish("STUDY_GROUP", "UPDATED", response);
        }
        return response;
    }

    @Transactional
    public List<StudyGroupResponse> updateMany(List<Long> ids, StudyGroupUpdateRequest request) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        validateUpdateRequest(request);

        Collection<StudyGroup> studyGroups = studyGroupRepository.findAllById(ids);
        validateAllIdsPresent(ids, studyGroups);

        Coordinates resolvedCoordinates = null;
        if (request.coordinatesId() != null) {
            resolvedCoordinates = resolveExistingCoordinates(request.coordinatesId());
        }

        Person resolvedAdmin = null;
        if (request.groupAdminId() != null) {
            resolvedAdmin = resolveExistingPerson(request.groupAdminId());
        }

        for (StudyGroup studyGroup : studyGroups) {
            applyUpdates(studyGroup, request, resolvedCoordinates, resolvedAdmin);
        }

        List<StudyGroup> saved = studyGroupRepository.saveAllAndFlush(studyGroups);
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

    @Transactional
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

    @Transactional
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
        List<StudyGroupResponse> responses = studyGroups.stream()
                .map(studyGroupMapper::toStudyGroupResponse)
                .toList();
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

    @Transactional
    public long deleteAllBySemester(Semester semesterEnum) {
        if (semesterEnum == null) {
            throw new BadRequestException("Не указан semesterEnum");
        }
        List<StudyGroup> studyGroups = studyGroupRepository.findAllBySemesterEnum(semesterEnum);
        if (studyGroups.isEmpty()) {
            throw new NotFoundException("Учебные группы с семестром %s не найдены".formatted(semesterEnum));
        }
        Set<Long> coordinateIds = studyGroups.stream()
                .map(StudyGroup::getCoordinates)
                .filter(Objects::nonNull)
                .map(Coordinates::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<StudyGroupResponse> responses = studyGroups.stream()
                .map(studyGroupMapper::toStudyGroupResponse)
                .toList();
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

    @Transactional
    public StudyGroupResponse deleteOneBySemester(Semester semesterEnum) {
        if (semesterEnum == null) {
            throw new BadRequestException("Не указан semesterEnum");
        }
        StudyGroup studyGroup = studyGroupRepository.findFirstBySemesterEnum(semesterEnum)
                .orElseThrow(() -> new NotFoundException("Учебные группы с семестром %s не найдены".formatted(semesterEnum)));
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

    private record DeletedPayload(Long id) {}

    public List<StudyGroupShouldBeExpelledGroupResponse> groupByShouldBeExpelled() {
        List<ShouldBeExpelledGroupProjection> stats = studyGroupRepository.countGroupedByShouldBeExpelled();
        return stats.stream()
                .map(item -> new StudyGroupShouldBeExpelledGroupResponse(item.getShouldBeExpelled(), item.getTotal()))
                .toList();
    }

    public StudyGroupExpelledTotalResponse totalExpelledStudents() {
        Long total = studyGroupRepository.sumExpelledStudents();
        return new StudyGroupExpelledTotalResponse(total == null ? 0 : total);
    }

    private void applyUpdates(StudyGroup studyGroup, StudyGroupUpdateRequest request) {
        applyUpdates(studyGroup, request,
                request.coordinatesId() != null ? resolveExistingCoordinates(request.coordinatesId()) : null,
                request.groupAdminId() != null ? resolveExistingPerson(request.groupAdminId()) : null);
    }

    private void applyUpdates(StudyGroup studyGroup, StudyGroupUpdateRequest request,
                              Coordinates coordinatesFromId, Person groupAdminFromId) {
        boolean formChanged = false;
        boolean courseChanged = false;

        if (request.formOfEducation() != null) {
            formChanged = !request.formOfEducation().equals(studyGroup.getFormOfEducation());
            studyGroup.setFormOfEducation(request.formOfEducation());
        }

        if (request.course() != null) {
            validateCourseValue(request.course());
            courseChanged = !request.course().equals(studyGroup.getCourse());
            studyGroup.setCourse(request.course());
        }

        if (request.studentsCount() != null) {
            studyGroup.setStudentsCount(request.studentsCount());
        }

        if (request.expelledStudents() != null) {
            studyGroup.setExpelledStudents(request.expelledStudents());
        }

        if (request.transferredStudents() != null) {
            studyGroup.setTransferredStudents(request.transferredStudents());
        }

        if (request.shouldBeExpelled() != null) {
            studyGroup.setShouldBeExpelled(request.shouldBeExpelled());
        }

        if (request.averageMark() != null) {
            studyGroup.setAverageMark(request.averageMark());
        } else if (Boolean.TRUE.equals(request.clearAverageMark())) {
            studyGroup.setAverageMark(null);
        }

        if (request.semesterEnum() != null) {
            studyGroup.setSemesterEnum(request.semesterEnum());
        }

        if (request.coordinatesId() != null) {
            Coordinates coordinates = coordinatesFromId != null ? coordinatesFromId : resolveExistingCoordinates(request.coordinatesId());
            studyGroup.setCoordinates(coordinates);
        } else if (request.coordinates() != null) {
            Coordinates coordinates = mapNewCoordinates(request.coordinates());
            studyGroup.setCoordinates(coordinates);
        }

        if (Boolean.TRUE.equals(request.removeGroupAdmin())) {
            studyGroup.setGroupAdmin(null);
        } else if (request.groupAdminId() != null) {
            Person admin = groupAdminFromId != null ? groupAdminFromId : resolveExistingPerson(request.groupAdminId());
            ensureGroupAdminAvailable(admin, studyGroup.getId());
            studyGroup.setGroupAdmin(admin);
        } else if (request.groupAdmin() != null) {
            studyGroup.setGroupAdmin(buildPersonEntity(request.groupAdmin()));
        }

        validateStudentsBounds(studyGroup.getFormOfEducation(), studyGroup.getStudentsCount(), true);

        if (formChanged || courseChanged) {
            assignGeneratedName(studyGroup, true);
        }
    }

    private Coordinates resolveCoordinatesForCreate(Long coordinatesId, CoordinatesAddRequest coordinatesDto) {
        if (coordinatesId != null) {
            return resolveExistingCoordinates(coordinatesId);
        }
        if (coordinatesDto != null) {
            return mapNewCoordinates(coordinatesDto);
        }
        return null;
    }

    private Coordinates resolveExistingCoordinates(Long coordinatesId) {
        return coordinatesRepository.findById(coordinatesId)
                .orElseThrow(() -> new NotFoundException("Координаты с идентификатором %d не найдены".formatted(coordinatesId)));
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
            throw new BadRequestException("Координаты с такими значениями уже существуют. Выберите существующую запись по идентификатору или укажите уникальные значения");
        }
    }

    private Person resolveExistingPerson(Long personId) {
        return personRepository.findById(personId)
                .orElseThrow(() -> new NotFoundException("Человек с идентификатором %d не найден".formatted(personId)));
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
        validatePersonLocationInput(request.locationId(), request.location());
        Location location = resolveLocationForPerson(request.locationId(), request.location());
        Person person = Person.builder()
                .name(request.name())
                .eyeColor(request.eyeColor())
                .hairColor(request.hairColor())
                .location(location)
                .height(request.height())
                .weight(request.weight())
                .nationality(request.nationality())
                .build();
        return person;
    }

    private Location resolveLocationForPerson(Long locationId, LocationAddRequest locationDto) {
        if (locationId != null) {
            return locationRepository.findById(locationId)
                    .orElseThrow(() -> new NotFoundException("Локация с идентификатором %d не найдена".formatted(locationId)));
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

        boolean hasAnyField = request.coordinatesId() != null
                || request.coordinates() != null
                || request.studentsCount() != null
                || request.expelledStudents() != null
                || request.transferredStudents() != null
                || request.formOfEducation() != null
                || request.course() != null
                || request.shouldBeExpelled() != null
                || request.averageMark() != null
                || Boolean.TRUE.equals(request.clearAverageMark())
                || request.semesterEnum() != null
                || request.groupAdminId() != null
                || request.groupAdmin() != null
                || Boolean.TRUE.equals(request.removeGroupAdmin());

        if (!hasAnyField) {
            throw new BadRequestException("Не переданы поля для обновления учебной группы");
        }

        if (request.clearAverageMark() != null && request.averageMark() != null && request.clearAverageMark()) {
            throw new BadRequestException("Нельзя одновременно задавать и очищать поле averageMark");
        }

        if (request.course() != null) {
            validateCourseValue(request.course());
        }

        validateCoordinatesInput(request.coordinatesId(), request.coordinates());
        validateGroupAdminInput(request.groupAdminId(), request.groupAdmin(), Boolean.TRUE.equals(request.removeGroupAdmin()));
    }

    private void validateCoordinatesInput(Long coordinatesId, CoordinatesAddRequest coordinatesDto) {
        if (coordinatesId != null && coordinatesDto != null) {
            throw new BadRequestException("Нельзя одновременно указать идентификатор координат и данные новых координат");
        }
    }

    private void validateGroupAdminInput(Long personId, PersonAddRequest personDto, boolean removeRequested) {
        if (personId != null && personDto != null) {
            throw new BadRequestException("Нельзя одновременно указать идентификатор администратора и данные нового администратора");
        }
        if (removeRequested && (personId != null || personDto != null)) {
            throw new BadRequestException("Нельзя удалить администратора группы и одновременно передавать его данные");
        }
    }

    private void validatePersonLocationInput(Long locationId, LocationAddRequest locationDto) {
        if (locationId != null && locationDto != null) {
            throw new BadRequestException("Нельзя одновременно указать идентификатор локации и данные новой локации");
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

    private void validateStudentsBounds(FormOfEducation formOfEducation, Long studentsCount, boolean allowBelowMinimum) {
        if (formOfEducation == null) {
            throw new BadRequestException("Поле formOfEducation обязательно для применения ограничений");
        }
        if (studentsCount == null) {
            throw new BadRequestException("Поле studentsCount не может быть пустым");
        }
        Long min = MIN_STUDENTS.get(formOfEducation);
        Long max = MAX_STUDENTS.get(formOfEducation);
        if (min != null && !allowBelowMinimum && studentsCount < min) {
            throw new BadRequestException("Количество студентов не может быть меньше %d для формы %s".formatted(min, formOfEducation));
        }
        if (max != null && studentsCount > max) {
            throw new BadRequestException("Количество студентов не может превышать %d для формы %s".formatted(max, formOfEducation));
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

    private void validateAllIdsPresent(List<Long> requestedIds, Collection<StudyGroup> foundEntities) {
        Set<Long> foundIds = foundEntities.stream()
                .map(StudyGroup::getId)
                .collect(Collectors.toSet());
        List<Long> missing = requestedIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new NotFoundException("Учебные группы с идентификаторами %s не найдены".formatted(missing));
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

        List<StudyGroup> candidates = studyGroupRepository.findByCourseAndFormOfEducationIn(studyGroup.getCourse(), allowedTargets).stream()
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
            return false;
        }

        studyGroupRepository.saveAll(changedGroups);
        studyGroupRepository.flush();
        changedGroups.stream()
                .map(studyGroupMapper::toStudyGroupResponse)
                .forEach(response -> entityChangeNotifier.publish("STUDY_GROUP", "UPDATED", response));

        Long coordinateId = studyGroup.getCoordinates() != null ? studyGroup.getCoordinates().getId() : null;
        studyGroupRepository.delete(studyGroup);
        studyGroupRepository.flush();
        if (coordinateId != null && !studyGroupRepository.existsByCoordinatesId(coordinateId)) {
            coordinatesRepository.deleteById(coordinateId);
        }
        return true;
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
            case FULL_TIME_EDUCATION -> List.of(FormOfEducation.FULL_TIME_EDUCATION,
                    FormOfEducation.DISTANCE_EDUCATION,
                    FormOfEducation.EVENING_CLASSES);
            case EVENING_CLASSES -> List.of(FormOfEducation.FULL_TIME_EDUCATION,
                    FormOfEducation.DISTANCE_EDUCATION);
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
