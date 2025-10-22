package ru.chousik.is.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.chousik.is.api.StudyGroupsApi;
import ru.chousik.is.dto.request.StudyGroupAddRequest;
import ru.chousik.is.dto.request.StudyGroupUpdateRequest;
import ru.chousik.is.dto.response.StudyGroupExpelledTotalResponse;
import ru.chousik.is.dto.response.StudyGroupResponse;
import ru.chousik.is.dto.response.StudyGroupShouldBeExpelledGroupResponse;
import ru.chousik.is.entity.Semester;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.service.StudyGroupService;

import java.util.List;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class StudyGroupController implements StudyGroupsApi {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final StudyGroupService studyGroupService;

    @Override
    public ResponseEntity<List<StudyGroupResponse>> apiV1StudyGroupsByIdsGet(
            @NotNull @Valid @RequestParam(value = "ids", required = true) List<Long> ids) {
        return ResponseEntity.ok(studyGroupService.getByIds(ids));
    }

    @Override
    public ResponseEntity<Long> apiV1StudyGroupsBySemesterDelete(
            @NotNull @Valid @RequestParam(value = "semesterEnum", required = true) Semester semesterEnum) {
        long deleted = studyGroupService.deleteAllBySemester(semesterEnum);
        return ResponseEntity.ok(deleted);
    }

    @Override
    public ResponseEntity<StudyGroupResponse> apiV1StudyGroupsBySemesterOneDelete(
            @NotNull @Valid @RequestParam(value = "semesterEnum", required = true) Semester semesterEnum) {
        return ResponseEntity.ok(studyGroupService.deleteOneBySemester(semesterEnum));
    }

    @Override
    public ResponseEntity<Void> apiV1StudyGroupsDelete(
            @NotNull @Valid @RequestParam(value = "ids", required = true) List<Long> ids) {
        studyGroupService.deleteMany(ids);
        return ResponseEntity.noContent().build();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ResponseEntity<PagedModel> apiV1StudyGroupsGet(Integer page, Integer size, String sort,
                                                          String sortBy, String direction) {
        Pageable pageable = toPageable(page, size);
        Sort.Direction sortDirection = resolveDirection(direction);
        String sortField = resolveSortField(sortBy, sort);
        Page<StudyGroupResponse> studyGroups = studyGroupService.getAll(pageable, sortField, sortDirection);
        PagedModel<StudyGroupResponse> body = new PagedModel<>(studyGroups);
        return ResponseEntity.ok((PagedModel) body);
    }

    @Override
    public ResponseEntity<StudyGroupResponse> apiV1StudyGroupsIdDelete(
            @NotNull @PathVariable("id") Long id) {
        StudyGroupResponse response = studyGroupService.delete(id);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<StudyGroupResponse> apiV1StudyGroupsIdGet(
            @NotNull @PathVariable("id") Long id) {
        return ResponseEntity.ok(studyGroupService.getById(id));
    }

    @Override
    public ResponseEntity<StudyGroupResponse> apiV1StudyGroupsIdPatch(
            @NotNull @PathVariable("id") Long id,
            @Valid @RequestBody StudyGroupUpdateRequest studyGroupUpdateRequest) {
        return ResponseEntity.ok(studyGroupService.update(id, studyGroupUpdateRequest));
    }

    @Override
    public ResponseEntity<List<StudyGroupResponse>> apiV1StudyGroupsPatch(
            @NotNull @Valid @RequestParam(value = "ids", required = true) List<Long> ids,
            @Valid @RequestBody StudyGroupUpdateRequest studyGroupUpdateRequest) {
        return ResponseEntity.ok(studyGroupService.updateMany(ids, studyGroupUpdateRequest));
    }

    @Override
    public ResponseEntity<StudyGroupResponse> apiV1StudyGroupsPost(
            @Valid @RequestBody StudyGroupAddRequest studyGroupAddRequest) {
        return ResponseEntity.ok(studyGroupService.create(studyGroupAddRequest));
    }

    @Override
    public ResponseEntity<List<StudyGroupShouldBeExpelledGroupResponse>> apiV1StudyGroupsStatsShouldBeExpelledGet() {
        return ResponseEntity.ok(studyGroupService.groupByShouldBeExpelled());
    }

    @Override
    public ResponseEntity<StudyGroupExpelledTotalResponse> apiV1StudyGroupsStatsExpelledTotalGet() {
        return ResponseEntity.ok(studyGroupService.totalExpelledStudents());
    }

    private Pageable toPageable(Integer page, Integer size) {
        int pageNumber = page == null ? DEFAULT_PAGE : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        return PageRequest.of(pageNumber, pageSize);
    }

    private String resolveSortField(String sortBy, String sort) {
        if (sortBy != null && !sortBy.isBlank()) {
            return sortBy;
        }
        return (sort != null && !sort.isBlank()) ? sort : null;
    }

    private Sort.Direction resolveDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return Sort.Direction.ASC;
        }
        try {
            return Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Некорректное направление сортировки '%s'".formatted(direction));
        }
    }
}
