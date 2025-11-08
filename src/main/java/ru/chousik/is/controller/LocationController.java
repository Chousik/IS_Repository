package ru.chousik.is.controller;

import jakarta.validation.Valid;
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
import ru.chousik.is.api.LocationsApi;
import ru.chousik.is.dto.request.LocationAddRequest;
import ru.chousik.is.dto.request.LocationUpdateRequest;
import ru.chousik.is.dto.response.LocationResponse;
import ru.chousik.is.service.LocationService;

import java.util.List;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class LocationController extends PageHelper implements LocationsApi {

    private final LocationService locationService;

    @Override
    public ResponseEntity<List<LocationResponse>> apiV1LocationsByIdsGet(
            @NotNull @Valid @RequestParam(value = "ids", required = true) List<Long> ids) {
        return ResponseEntity.ok(locationService.getByIds(ids));
    }

    @Override
    public ResponseEntity<Void> apiV1LocationsDelete(
            @NotNull @Valid @RequestParam(value = "ids", required = true) List<Long> ids) {
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
    public ResponseEntity<LocationResponse> apiV1LocationsIdDelete(
            @NotNull @PathVariable("id") Long id) {
        LocationResponse response = locationService.delete(id);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<LocationResponse> apiV1LocationsIdGet(
            @NotNull @PathVariable("id") Long id) {
        return ResponseEntity.ok(locationService.getById(id));
    }

    @Override
    public ResponseEntity<LocationResponse> apiV1LocationsIdPatch(
            @NotNull @PathVariable("id") Long id,
            @Valid @RequestBody LocationUpdateRequest locationUpdateRequest) {
        return ResponseEntity.ok(locationService.update(id, locationUpdateRequest));
    }

    @Override
    public ResponseEntity<List<LocationResponse>> apiV1LocationsPatch(
            @NotNull @Valid @RequestParam(value = "ids", required = true) List<Long> ids,
            @Valid @RequestBody LocationUpdateRequest locationUpdateRequest) {
        return ResponseEntity.ok(locationService.updateMany(ids, locationUpdateRequest));
    }

    @Override
    public ResponseEntity<LocationResponse> apiV1LocationsPost(
            @Valid @RequestBody LocationAddRequest locationAddRequest) {
        return ResponseEntity.ok(locationService.create(locationAddRequest));
    }
}
