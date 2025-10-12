package ru.chousik.is.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;
import ru.chousik.is.dto.request.StudyGroupAddRequest;
import ru.chousik.is.dto.request.StudyGroupUpdateRequest;
import ru.chousik.is.dto.response.StudyGroupExpelledTotalResponse;
import ru.chousik.is.dto.response.StudyGroupResponse;
import ru.chousik.is.dto.response.StudyGroupShouldBeExpelledGroupResponse;
import ru.chousik.is.entity.Semester;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.service.StudyGroupService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/study-groups")
@RequiredArgsConstructor
public class StudyGroupController {

    private final StudyGroupService studyGroupService;

    @GetMapping
    public PagedModel<StudyGroupResponse> getAll(Pageable pageable,
                                                 @RequestParam(required = false) String sortBy,
                                                 @RequestParam(required = false, defaultValue = "asc") String direction) {
        Sort.Direction sortDirection = resolveDirection(direction);
        Page<StudyGroupResponse> studyGroups = studyGroupService.getAll(pageable, sortBy, sortDirection);
        return new PagedModel<>(studyGroups);
    }

    @GetMapping("/{id}")
    public StudyGroupResponse getOne(@PathVariable Long id) {
        return studyGroupService.getById(id);
    }

    @GetMapping("/by-ids")
    public List<StudyGroupResponse> getMany(@RequestParam List<Long> ids) {
        return studyGroupService.getByIds(ids);
    }

    @PostMapping
    public StudyGroupResponse create(@RequestBody @Valid StudyGroupAddRequest request) {
        return studyGroupService.create(request);
    }

    @PatchMapping("/{id}")
    public StudyGroupResponse patch(@PathVariable Long id, @RequestBody @Valid StudyGroupUpdateRequest request) {
        return studyGroupService.update(id, request);
    }

    @PatchMapping
    public List<StudyGroupResponse> patchMany(@RequestParam List<Long> ids,
                                              @RequestBody @Valid StudyGroupUpdateRequest request) {
        return studyGroupService.updateMany(ids, request);
    }

    @DeleteMapping("/{id}")
    public StudyGroupResponse delete(@PathVariable Long id) {
        return studyGroupService.delete(id);
    }

    @DeleteMapping
    public void deleteMany(@RequestParam List<Long> ids) {
        studyGroupService.deleteMany(ids);
    }

    @DeleteMapping("/by-semester")
    public long deleteAllBySemester(@RequestParam Semester semesterEnum) {
        return studyGroupService.deleteAllBySemester(semesterEnum);
    }

    @DeleteMapping("/by-semester/one")
    public StudyGroupResponse deleteOneBySemester(@RequestParam Semester semesterEnum) {
        return studyGroupService.deleteOneBySemester(semesterEnum);
    }

    @GetMapping("/stats/should-be-expelled")
    public List<StudyGroupShouldBeExpelledGroupResponse> groupByShouldBeExpelled() {
        return studyGroupService.groupByShouldBeExpelled();
    }

    @GetMapping("/stats/expelled-total")
    public StudyGroupExpelledTotalResponse totalExpelledStudents() {
        return studyGroupService.totalExpelledStudents();
    }

    private Sort.Direction resolveDirection(String direction) {
        if (direction == null) {
            return Sort.Direction.ASC;
        }
        try {
            return Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Некорректное направление сортировки '%s'".formatted(direction));
        }
    }
}
