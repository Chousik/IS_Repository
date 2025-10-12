package ru.chousik.is.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.chousik.is.api.LocationsApi;
import ru.chousik.is.dto.request.LocationAddRequest;
import ru.chousik.is.dto.request.LocationUpdateRequest;
import ru.chousik.is.dto.response.LocationResponse;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.service.LocationService;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class LocationController implements LocationsApi {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final LocationService locationService;

    @Override
    public ResponseEntity<List<LocationResponse>> apiV1LocationsByIdsGet(List<Long> ids) {
        return ResponseEntity.ok(locationService.getByIds(ids));
    }

    @Override
    public ResponseEntity<Void> apiV1LocationsDelete(List<Long> ids) {
        locationService.deleteMany(ids);
        return ResponseEntity.noContent().build();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ResponseEntity<PagedModel> apiV1LocationsGet(Integer page, Integer size, String sort,
                                                       String sortBy, String direction) {
        Pageable pageable = toPageable(page, size);
        Sort.Direction sortDirection = resolveDirection(direction);
        String sortField = resolveSortField(sortBy, sort);
        Page<LocationResponse> locations = locationService.getAll(pageable, sortField, sortDirection);
        PagedModel<LocationResponse> body = new PagedModel<>(locations);
        return ResponseEntity.ok((PagedModel) body);
    }

    @Override
    public ResponseEntity<LocationResponse> apiV1LocationsIdDelete(Long id) {
        return ResponseEntity.ok(locationService.delete(id));
    }

    @Override
    public ResponseEntity<LocationResponse> apiV1LocationsIdGet(Long id) {
        return ResponseEntity.ok(locationService.getById(id));
    }

    @Override
    public ResponseEntity<LocationResponse> apiV1LocationsIdPatch(Long id, LocationUpdateRequest locationUpdateRequest) {
        return ResponseEntity.ok(locationService.update(id, locationUpdateRequest));
    }

    @Override
    public ResponseEntity<List<LocationResponse>> apiV1LocationsPatch(List<Long> ids,
                                                                     LocationUpdateRequest locationUpdateRequest) {
        return ResponseEntity.ok(locationService.updateMany(ids, locationUpdateRequest));
    }

    @Override
    public ResponseEntity<LocationResponse> apiV1LocationsPost(@Valid LocationAddRequest locationAddRequest) {
        return ResponseEntity.ok(locationService.create(locationAddRequest));
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
