package ru.chousik.is.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;
import ru.chousik.is.dto.request.LocationAddRequest;
import ru.chousik.is.dto.request.LocationUpdateRequest;
import ru.chousik.is.dto.response.LocationResponse;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.service.LocationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    public PagedModel<LocationResponse> getAll(Pageable pageable,
                                               @RequestParam(required = false) String sortBy,
                                               @RequestParam(required = false, defaultValue = "asc") String direction) {
        Sort.Direction sortDirection = resolveDirection(direction);
        Page<LocationResponse> locations = locationService.getAll(pageable, sortBy, sortDirection);
        return new PagedModel<>(locations);
    }

    @GetMapping("/{id}")
    public LocationResponse getOne(@PathVariable Long id) {
        return locationService.getById(id);
    }

    @GetMapping("/by-ids")
    public List<LocationResponse> getMany(@RequestParam List<Long> ids) {
        return locationService.getByIds(ids);
    }

    @PostMapping
    public LocationResponse create(@RequestBody @Valid LocationAddRequest request) {
        return locationService.create(request);
    }

    @PatchMapping("/{id}")
    public LocationResponse patch(@PathVariable Long id, @RequestBody LocationUpdateRequest request) {
        return locationService.update(id, request);
    }

    @PatchMapping
    public List<LocationResponse> patchMany(@RequestParam List<Long> ids, @RequestBody LocationUpdateRequest request) {
        return locationService.updateMany(ids, request);
    }

    @DeleteMapping("/{id}")
    public LocationResponse delete(@PathVariable Long id) {
        return locationService.delete(id);
    }

    @DeleteMapping
    public void deleteMany(@RequestParam List<Long> ids) {
        locationService.deleteMany(ids);
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
