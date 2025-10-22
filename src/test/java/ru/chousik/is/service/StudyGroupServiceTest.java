package ru.chousik.is.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.chousik.is.dto.mapper.CoordinatesMapper;
import ru.chousik.is.dto.mapper.LocationMapper;
import ru.chousik.is.dto.mapper.StudyGroupMapper;
import ru.chousik.is.dto.request.CoordinatesAddRequest;
import ru.chousik.is.dto.request.StudyGroupAddRequest;
import ru.chousik.is.dto.response.CoordinatesResponse;
import ru.chousik.is.dto.response.StudyGroupExpelledTotalResponse;
import ru.chousik.is.dto.response.StudyGroupResponse;
import ru.chousik.is.dto.response.StudyGroupShouldBeExpelledGroupResponse;
import ru.chousik.is.entity.Coordinates;
import ru.chousik.is.entity.FormOfEducation;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyGroupServiceTest {

    @Mock
    private StudyGroupRepository studyGroupRepository;
    @Mock
    private CoordinatesRepository coordinatesRepository;
    @Mock
    private StudyGroupMapper studyGroupMapper;
    @Mock
    private EntityChangeNotifier entityChangeNotifier;

    @InjectMocks
    private StudyGroupService service;

    @Test
    void deleteAllBySemester_removesGroupsAndUnusedCoordinates() {
        Semester semester = Semester.FIRST;
        Coordinates coordinates1 = Coordinates.builder().id(10L).x(1L).y(1.0f).build();
        Coordinates coordinates2 = Coordinates.builder().id(20L).x(2L).y(2.0f).build();
        StudyGroup group1 = buildGroup(1L, "A", semester, coordinates1);
        StudyGroup group2 = buildGroup(2L, "B", semester, coordinates2);
        StudyGroupResponse response1 = toResponse(group1);
        StudyGroupResponse response2 = toResponse(group2);

        when(studyGroupRepository.findAllBySemesterEnum(semester)).thenReturn(List.of(group1, group2));
        when(studyGroupRepository.existsByCoordinatesId(10L)).thenReturn(false);
        when(studyGroupRepository.existsByCoordinatesId(20L)).thenReturn(true);
        when(studyGroupMapper.toStudyGroupResponse(group1)).thenReturn(response1);
        when(studyGroupMapper.toStudyGroupResponse(group2)).thenReturn(response2);

        long removed = service.deleteAllBySemester(semester);

        assertEquals(2L, removed);
        verify(studyGroupRepository).deleteAllInBatch(List.of(group1, group2));
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(coordinatesRepository).deleteAllByIdInBatch(captor.capture());
        List<Long> removedIds = captor.getValue();
        assertEquals(1, removedIds.size());
        assertEquals(10L, removedIds.get(0));
        verify(entityChangeNotifier).publish("STUDY_GROUP", "DELETED", response1);
        verify(entityChangeNotifier).publish("STUDY_GROUP", "DELETED", response2);
    }

    @Test
    void deleteAllBySemester_requiresSemester() {
        assertThrows(BadRequestException.class, () -> service.deleteAllBySemester(null));
        verifyNoInteractions(studyGroupRepository, coordinatesRepository, entityChangeNotifier);
    }

    @Test
    void deleteAllBySemester_throwsWhenNothingFound() {
        Semester semester = Semester.SECOND;
        when(studyGroupRepository.findAllBySemesterEnum(semester)).thenReturn(List.of());

        assertThrows(NotFoundException.class, () -> service.deleteAllBySemester(semester));
        verify(studyGroupRepository, never()).deleteAllInBatch(any());
        verifyNoInteractions(coordinatesRepository, entityChangeNotifier);
    }

    @Test
    void deleteOneBySemester_removesCoordinateWhenUnused() {
        Semester semester = Semester.FOURTH;
        Coordinates coordinates = Coordinates.builder().id(30L).x(3L).y(3.0f).build();
        StudyGroup group = buildGroup(5L, "Group", semester, coordinates);
        StudyGroupResponse response = toResponse(group);

        when(studyGroupRepository.findFirstBySemesterEnum(semester)).thenReturn(Optional.of(group));
        when(studyGroupRepository.existsByCoordinatesId(30L)).thenReturn(false);
        when(studyGroupMapper.toStudyGroupResponse(group)).thenReturn(response);

        StudyGroupResponse actual = service.deleteOneBySemester(semester);

        assertEquals(response, actual);
        verify(studyGroupRepository).delete(group);
        verify(studyGroupRepository).flush();
        verify(coordinatesRepository).deleteById(30L);
        verify(entityChangeNotifier).publish("STUDY_GROUP", "DELETED", response);
    }

    @Test
    void deleteOneBySemester_doesNotRemoveCoordinateIfStillUsed() {
        Semester semester = Semester.SEVENTH;
        Coordinates coordinates = Coordinates.builder().id(40L).x(4L).y(4.0f).build();
        StudyGroup group = buildGroup(7L, "Another", semester, coordinates);
        StudyGroupResponse response = toResponse(group);

        when(studyGroupRepository.findFirstBySemesterEnum(semester)).thenReturn(Optional.of(group));
        when(studyGroupRepository.existsByCoordinatesId(40L)).thenReturn(true);
        when(studyGroupMapper.toStudyGroupResponse(group)).thenReturn(response);

        service.deleteOneBySemester(semester);

        verify(coordinatesRepository, never()).deleteById(any());
        verify(entityChangeNotifier).publish("STUDY_GROUP", "DELETED", response);
    }

    @Test
    void deleteOneBySemester_throwsWhenNothingFound() {
        Semester semester = Semester.SIXTH;
        when(studyGroupRepository.findFirstBySemesterEnum(semester)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.deleteOneBySemester(semester));
        verify(studyGroupRepository, never()).delete(any());
    }

    @Test
    void groupByShouldBeExpelled_mapsProjection() {
        ShouldBeExpelledGroupProjection projection = new ShouldBeExpelledGroupProjection() {
            @Override
            public long getShouldBeExpelled() {
                return 12L;
            }

            @Override
            public long getTotal() {
                return 3L;
            }
        };
        when(studyGroupRepository.countGroupedByShouldBeExpelled()).thenReturn(List.of(projection));

        List<StudyGroupShouldBeExpelledGroupResponse> result = service.groupByShouldBeExpelled();

        assertEquals(1, result.size());
        StudyGroupShouldBeExpelledGroupResponse item = result.get(0);
        assertEquals(12L, item.shouldBeExpelled());
        assertEquals(3L, item.count());
    }

    @Test
    void totalExpelledStudents_returnsZeroWhenRepositoryReturnsNull() {
        when(studyGroupRepository.sumExpelledStudents()).thenReturn(null);

        StudyGroupExpelledTotalResponse response = service.totalExpelledStudents();

        assertEquals(0L, response.totalExpelledStudents());
    }

    @Test
    void totalExpelledStudents_delegatesToRepository() {
        when(studyGroupRepository.sumExpelledStudents()).thenReturn(42L);

        StudyGroupExpelledTotalResponse response = service.totalExpelledStudents();

        assertEquals(42L, response.totalExpelledStudents());
    }

    @Test
    void create_throwsWhenBothCoordinateSourcesProvided() {
        StudyGroupAddRequest request = new StudyGroupAddRequest(
                "Test",
                11L,
                new CoordinatesAddRequest(1L, 2.0f),
                10L,
                5L,
                4L,
                FormOfEducation.FULL_TIME_EDUCATION,
                3L,
                7,
                Semester.FIRST,
                null,
                null
        );

        assertThrows(BadRequestException.class, () -> service.create(request));
        verifyNoInteractions(studyGroupRepository, coordinatesRepository, studyGroupMapper, entityChangeNotifier);
    }

    @Test
    void create_throwsWhenCoordinatesMissing() {
        StudyGroupAddRequest request = new StudyGroupAddRequest(
                "Test",
                null,
                null,
                10L,
                5L,
                4L,
                FormOfEducation.FULL_TIME_EDUCATION,
                3L,
                7,
                Semester.FIRST,
                null,
                null
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request));
        assertTrue(ex.getMessage().contains("Координаты обязательны"));
        verifyNoInteractions(studyGroupRepository, coordinatesRepository, studyGroupMapper, entityChangeNotifier);
    }

    private StudyGroup buildGroup(Long id, String name, Semester semester, Coordinates coordinates) {
        StudyGroup group = StudyGroup.builder()
                .id(id)
                .name(name)
                .coordinates(coordinates)
                .studentsCount(10L)
                .expelledStudents(2L)
                .transferredStudents(1L)
                .formOfEducation(FormOfEducation.FULL_TIME_EDUCATION)
                .shouldBeExpelled(3L)
                .averageMark(5)
                .semesterEnum(semester)
                .groupAdmin(null)
                .build();
        group.setCreationDate(LocalDateTime.now());
        return group;
    }

    private StudyGroupResponse toResponse(StudyGroup group) {
        Coordinates coords = group.getCoordinates();
        CoordinatesResponse coordinatesResponse = new CoordinatesResponse(
                coords != null ? coords.getId() : null,
                coords != null ? coords.getX() : 0L,
                coords != null ? coords.getY() : null
        );
        return new StudyGroupResponse(
                group.getId(),
                group.getName(),
                coordinatesResponse,
                group.getCreationDate(),
                group.getStudentsCount(),
                group.getExpelledStudents(),
                group.getTransferredStudents(),
                group.getFormOfEducation(),
                group.getShouldBeExpelled(),
                group.getAverageMark(),
                group.getSemesterEnum(),
                null
        );
    }
}
