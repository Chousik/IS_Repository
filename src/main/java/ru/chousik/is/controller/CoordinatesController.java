package ru.chousik.is.controller;

import java.util.List;
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
import ru.chousik.is.api.CoordinatesApi;
import ru.chousik.is.api.model.CoordinatesAddRequest;
import ru.chousik.is.api.model.CoordinatesResponse;
import ru.chousik.is.api.model.CoordinatesUpdateRequest;
import ru.chousik.is.service.CoordinatesService;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class CoordinatesController extends PageHelper implements CoordinatesApi {

    private final CoordinatesService coordinatesService;

    @Override
    public ResponseEntity<List<CoordinatesResponse>> apiV1CoordinatesByIdsGet(
            @RequestParam(value = "ids", required = true) List<Long> ids) {
        return ResponseEntity.ok(coordinatesService.getByIds(ids));
    }

    @Override
    public ResponseEntity<Void> apiV1CoordinatesDelete(
            @RequestParam(value = "ids", required = true) List<Long> ids) {
        coordinatesService.deleteMany(ids);
        return ResponseEntity.noContent().build();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ResponseEntity<PagedModel> apiV1CoordinatesGet(
            Integer page, Integer size, String sort, String sortBy, String direction) {
        Pageable pageable = toPageable(page, size);
        Sort.Direction sortDirection = resolveDirection(direction);
        String sortField = resolveSortField(sortBy, sort);
        Page<CoordinatesResponse> coordinates = coordinatesService.getAll(pageable, sortField, sortDirection);
        PagedModel<CoordinatesResponse> body = new PagedModel<>(coordinates);
        return ResponseEntity.ok((PagedModel) body);
    }

    @Override
    public ResponseEntity<CoordinatesResponse> apiV1CoordinatesIdDelete(@PathVariable("id") Long id) {
        CoordinatesResponse response = coordinatesService.delete(id);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CoordinatesResponse> apiV1CoordinatesIdGet(@PathVariable("id") Long id) {
        return ResponseEntity.ok(coordinatesService.getById(id));
    }

    @Override
    public ResponseEntity<CoordinatesResponse> apiV1CoordinatesIdPatch(
            @PathVariable("id") Long id, @RequestBody CoordinatesUpdateRequest coordinatesUpdateRequest) {
        return ResponseEntity.ok(coordinatesService.update(id, coordinatesUpdateRequest));
    }

    @Override
    public ResponseEntity<List<CoordinatesResponse>> apiV1CoordinatesPatch(
            @RequestParam(value = "ids", required = true) List<Long> ids,
            @RequestBody CoordinatesUpdateRequest coordinatesUpdateRequest) {
        List<CoordinatesResponse> responses = coordinatesService.updateMany(ids, coordinatesUpdateRequest);
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<CoordinatesResponse> apiV1CoordinatesPost(
            @RequestBody CoordinatesAddRequest coordinatesAddRequest) {
        return ResponseEntity.ok(coordinatesService.create(coordinatesAddRequest));
    }
}
