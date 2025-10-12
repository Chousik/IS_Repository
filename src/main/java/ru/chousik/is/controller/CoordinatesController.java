package ru.chousik.is.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;
import ru.chousik.is.dto.request.CoordinatesAddRequest;
import ru.chousik.is.dto.request.CoordinatesUpdateRequest;
import ru.chousik.is.dto.response.CoordinatesResponse;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.service.CoordinatesService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coordinates")
@RequiredArgsConstructor
public class CoordinatesController {

    private final CoordinatesService coordinatesService;

    @GetMapping
    public PagedModel<CoordinatesResponse> getAll(Pageable pageable,
                                                  @RequestParam(required = false) String sortBy,
                                                  @RequestParam(required = false, defaultValue = "asc") String direction) {
        Sort.Direction sortDirection = resolveDirection(direction);
        Page<CoordinatesResponse> coordinates = coordinatesService.getAll(pageable, sortBy, sortDirection);
        return new PagedModel<>(coordinates);
    }

    @GetMapping("/{id}")
    public CoordinatesResponse getOne(@PathVariable Long id) {
        return coordinatesService.getById(id);
    }

    @GetMapping("/by-ids")
    public List<CoordinatesResponse> getMany(@RequestParam List<Long> ids) {
        return coordinatesService.getByIds(ids);
    }

    @PostMapping
    public CoordinatesResponse create(@RequestBody @Valid CoordinatesAddRequest request) {
        return coordinatesService.create(request);
    }

    @PatchMapping("/{id}")
    public CoordinatesResponse patch(@PathVariable Long id, @RequestBody CoordinatesUpdateRequest request) {
        return coordinatesService.update(id, request);
    }

    @PatchMapping
    public List<CoordinatesResponse> patchMany(@RequestParam List<Long> ids, @RequestBody CoordinatesUpdateRequest request) {
        return coordinatesService.updateMany(ids, request);
    }

    @DeleteMapping("/{id}")
    public CoordinatesResponse delete(@PathVariable Long id) {
        return coordinatesService.delete(id);
    }

    @DeleteMapping
    public void deleteMany(@RequestParam List<Long> ids) {
        coordinatesService.deleteMany(ids);
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
