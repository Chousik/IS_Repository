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
import ru.chousik.is.entity.Location;
import ru.chousik.is.entity.Person;
import ru.chousik.is.entity.StudyGroup;
import ru.chousik.is.entity.Semester;
import ru.chousik.is.event.EntityChangeNotifier;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.exception.NotFoundException;
import ru.chousik.is.repository.CoordinatesRepository;
import ru.chousik.is.repository.LocationRepository;
import ru.chousik.is.repository.PersonRepository;
import ru.chousik.is.repository.StudyGroupRepository;
import ru.chousik.is.repository.StudyGroupRepository.ShouldBeExpelledGroupProjection;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        validateCoordinatesInput(request.coordinatesId(), request.coordinates());
        validateGroupAdminInput(request.groupAdminId(), request.groupAdmin(), false);

        Coordinates coordinates = resolveCoordinatesForCreate(request.coordinatesId(), request.coordinates());
        if (coordinates == null) {
            throw new BadRequestException("Координаты обязательны для создания учебной группы");
        }

        Person groupAdmin = resolveGroupAdminForCreate(request.groupAdminId(), request.groupAdmin());

        StudyGroup studyGroup = StudyGroup.builder()
                .name(request.name())
                .coordinates(coordinates)
                .studentsCount(request.studentsCount())
                .expelledStudents(request.expelledStudents())
                .transferredStudents(request.transferredStudents())
                .formOfEducation(request.formOfEducation())
                .shouldBeExpelled(request.shouldBeExpelled())
                .averageMark(request.averageMark())
                .semesterEnum(request.semesterEnum())
                .groupAdmin(groupAdmin)
                .build();

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

        StudyGroup saved = studyGroupRepository.save(studyGroup);
        StudyGroupResponse response = studyGroupMapper.toStudyGroupResponse(saved);
        entityChangeNotifier.publish("STUDY_GROUP", "UPDATED", response);
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

        List<StudyGroup> saved = studyGroupRepository.saveAll(studyGroups);
        List<StudyGroupResponse> responses = saved.stream().map(studyGroupMapper::toStudyGroupResponse).toList();
        responses.forEach(response -> entityChangeNotifier.publish("STUDY_GROUP", "UPDATED", response));
        return responses;
    }

    @Transactional
    public StudyGroupResponse delete(Long id) {
        StudyGroup studyGroup = studyGroupRepository.findById(id).orElse(null);
        if (studyGroup == null) {
            entityChangeNotifier.publish("STUDY_GROUP", "DELETED", new DeletedPayload(id));
            return null;
        }
        studyGroupRepository.delete(studyGroup);
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
        List<StudyGroupResponse> responses = studyGroups.stream()
                .map(studyGroupMapper::toStudyGroupResponse)
                .toList();
        studyGroupRepository.deleteAll(studyGroups);
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
        List<StudyGroupResponse> responses = studyGroups.stream()
                .map(studyGroupMapper::toStudyGroupResponse)
                .toList();
        studyGroupRepository.deleteAll(studyGroups);
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
        studyGroupRepository.delete(studyGroup);
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
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("Поле name не может быть пустым");
            }
            studyGroup.setName(request.name());
        }

        if (request.studentsCount() != null) {
            studyGroup.setStudentsCount(request.studentsCount());
        } else if (Boolean.TRUE.equals(request.clearStudentsCount())) {
            studyGroup.setStudentsCount(null);
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

        if (Boolean.TRUE.equals(request.clearFormOfEducation())) {
            studyGroup.setFormOfEducation(null);
        } else if (request.formOfEducation() != null) {
            studyGroup.setFormOfEducation(request.formOfEducation());
        }

        if (request.semesterEnum() != null) {
            studyGroup.setSemesterEnum(request.semesterEnum());
        }

        if (request.coordinatesId() != null) {
            Coordinates coordinates = coordinatesFromId != null ? coordinatesFromId : resolveExistingCoordinates(request.coordinatesId());
            studyGroup.setCoordinates(coordinates);
        } else if (request.coordinates() != null) {
            studyGroup.setCoordinates(coordinatesMapper.toEntity(request.coordinates()));
        }

        if (Boolean.TRUE.equals(request.removeGroupAdmin())) {
            studyGroup.setGroupAdmin(null);
        } else if (request.groupAdminId() != null) {
            Person admin = groupAdminFromId != null ? groupAdminFromId : resolveExistingPerson(request.groupAdminId());
            studyGroup.setGroupAdmin(admin);
        } else if (request.groupAdmin() != null) {
            studyGroup.setGroupAdmin(buildPersonEntity(request.groupAdmin()));
        }
    }

    private Coordinates resolveCoordinatesForCreate(Long coordinatesId, CoordinatesAddRequest coordinatesDto) {
        if (coordinatesId != null) {
            return resolveExistingCoordinates(coordinatesId);
        }
        if (coordinatesDto != null) {
            return coordinatesMapper.toEntity(coordinatesDto);
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

    private Person resolveExistingPerson(Long personId) {
        return personRepository.findById(personId)
                .orElseThrow(() -> new NotFoundException("Человек с идентификатором %d не найден".formatted(personId)));
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

        boolean hasAnyField = request.name() != null
                || request.coordinatesId() != null
                || request.coordinates() != null
                || request.studentsCount() != null
                || Boolean.TRUE.equals(request.clearStudentsCount())
                || request.expelledStudents() != null
                || request.transferredStudents() != null
                || request.formOfEducation() != null
                || Boolean.TRUE.equals(request.clearFormOfEducation())
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

        if (request.name() != null && request.name().isBlank()) {
            throw new BadRequestException("Поле name не может быть пустым");
        }

        if (request.clearStudentsCount() != null && request.studentsCount() != null && request.clearStudentsCount()) {
            throw new BadRequestException("Нельзя одновременно задавать и очищать поле studentsCount");
        }

        if (request.clearAverageMark() != null && request.averageMark() != null && request.clearAverageMark()) {
            throw new BadRequestException("Нельзя одновременно задавать и очищать поле averageMark");
        }

        if (request.clearFormOfEducation() != null && request.formOfEducation() != null && request.clearFormOfEducation()) {
            throw new BadRequestException("Нельзя одновременно задавать и очищать поле formOfEducation");
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

    private Pageable applySorting(Pageable pageable, String sortBy, Sort.Direction direction) {
        String sortField = Objects.requireNonNullElse(sortBy, "id");
        Sort.Direction sortDirection = direction == null ? Sort.Direction.ASC : direction;
        try {
            Sort sort = Sort.by(sortDirection, sortField);
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        } catch (PropertyReferenceException e) {
            throw new BadRequestException("Неизвестное поле сортировки '%s'".formatted(sortField));
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
}
