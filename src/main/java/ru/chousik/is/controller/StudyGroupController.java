package ru.chousik.is.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import ru.chousik.is.service.StudyGroupService;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class StudyGroupController extends PageHelper implements StudyGroupsApi {

    private final StudyGroupService studyGroupService;

    @Override
    public ResponseEntity<List<StudyGroupResponse>> apiV1StudyGroupsByIdsGet(
            @RequestParam(value = "ids", required = true) List<Long> ids) {
        return ResponseEntity.ok(studyGroupService.getByIds(ids));
    }

    @Override
    public ResponseEntity<Long> apiV1StudyGroupsBySemesterDelete(
            @RequestParam(value = "semesterEnum", required = true) Semester semesterEnum) {
        long deleted = studyGroupService.deleteAllBySemester(semesterEnum);
        return ResponseEntity.ok(deleted);
    }

    @Override
    public ResponseEntity<StudyGroupResponse> apiV1StudyGroupsBySemesterOneDelete(
            @RequestParam(value = "semesterEnum", required = true) Semester semesterEnum) {
        return ResponseEntity.ok(studyGroupService.deleteOneBySemester(semesterEnum));
    }

    @Override
    public ResponseEntity<Void> apiV1StudyGroupsDelete(
            @RequestParam(value = "ids", required = true) List<Long> ids) {
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
            @PathVariable("id") Long id) {
        StudyGroupResponse response = studyGroupService.delete(id);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<StudyGroupResponse> apiV1StudyGroupsIdGet(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(studyGroupService.getById(id));
    }

    @Override
    public ResponseEntity<StudyGroupResponse> apiV1StudyGroupsIdPatch(
            @PathVariable("id") Long id,
            @RequestBody StudyGroupUpdateRequest studyGroupUpdateRequest) {
        return ResponseEntity.ok(studyGroupService.update(id, studyGroupUpdateRequest));
    }

    @Override
    public ResponseEntity<List<StudyGroupResponse>> apiV1StudyGroupsPatch(
            @RequestParam(value = "ids", required = true) List<Long> ids,
            @RequestBody StudyGroupUpdateRequest studyGroupUpdateRequest) {
        return ResponseEntity.ok(studyGroupService.updateMany(ids, studyGroupUpdateRequest));
    }

    @Override
    public ResponseEntity<StudyGroupResponse> apiV1StudyGroupsPost(
            @RequestBody StudyGroupAddRequest studyGroupAddRequest) {
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
}
