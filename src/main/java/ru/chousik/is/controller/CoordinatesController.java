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
import ru.chousik.is.api.CoordinatesApi;
import ru.chousik.is.dto.request.CoordinatesAddRequest;
import ru.chousik.is.dto.request.CoordinatesUpdateRequest;
import ru.chousik.is.dto.response.CoordinatesResponse;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.service.CoordinatesService;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class CoordinatesController implements CoordinatesApi {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final CoordinatesService coordinatesService;

    @Override
    public ResponseEntity<List<CoordinatesResponse>> apiV1CoordinatesByIdsGet(List<Long> ids) {
        return ResponseEntity.ok(coordinatesService.getByIds(ids));
    }

    @Override
    public ResponseEntity<Void> apiV1CoordinatesDelete(List<Long> ids) {
        coordinatesService.deleteMany(ids);
        return ResponseEntity.noContent().build();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ResponseEntity<PagedModel> apiV1CoordinatesGet(Integer page, Integer size, String sort,
                                                         String sortBy, String direction) {
        Pageable pageable = toPageable(page, size);
        Sort.Direction sortDirection = resolveDirection(direction);
        String sortField = resolveSortField(sortBy, sort);
        Page<CoordinatesResponse> coordinates = coordinatesService.getAll(pageable, sortField, sortDirection);
        PagedModel<CoordinatesResponse> body = new PagedModel<>(coordinates);
        return ResponseEntity.ok((PagedModel) body);
    }

    @Override
    public ResponseEntity<CoordinatesResponse> apiV1CoordinatesIdDelete(Long id) {
        return ResponseEntity.ok(coordinatesService.delete(id));
    }

    @Override
    public ResponseEntity<CoordinatesResponse> apiV1CoordinatesIdGet(Long id) {
        return ResponseEntity.ok(coordinatesService.getById(id));
    }

    @Override
    public ResponseEntity<CoordinatesResponse> apiV1CoordinatesIdPatch(Long id, CoordinatesUpdateRequest coordinatesUpdateRequest) {
        return ResponseEntity.ok(coordinatesService.update(id, coordinatesUpdateRequest));
    }

    @Override
    public ResponseEntity<List<CoordinatesResponse>> apiV1CoordinatesPatch(List<Long> ids,
                                                                          CoordinatesUpdateRequest coordinatesUpdateRequest) {
        return ResponseEntity.ok(coordinatesService.updateMany(ids, coordinatesUpdateRequest));
    }

    @Override
    public ResponseEntity<CoordinatesResponse> apiV1CoordinatesPost(@Valid CoordinatesAddRequest coordinatesAddRequest) {
        return ResponseEntity.ok(coordinatesService.create(coordinatesAddRequest));
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
