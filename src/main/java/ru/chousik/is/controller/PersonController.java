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
import ru.chousik.is.api.PersonsApi;
import ru.chousik.is.dto.request.PersonAddRequest;
import ru.chousik.is.dto.request.PersonUpdateRequest;
import ru.chousik.is.dto.response.PersonResponse;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.service.PersonService;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class PersonController implements PersonsApi {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final PersonService personService;

    @Override
    public ResponseEntity<List<PersonResponse>> apiV1PersonsByIdsGet(List<Long> ids) {
        return ResponseEntity.ok(personService.getByIds(ids));
    }

    @Override
    public ResponseEntity<Void> apiV1PersonsDelete(List<Long> ids) {
        personService.deleteMany(ids);
        return ResponseEntity.noContent().build();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ResponseEntity<PagedModel> apiV1PersonsGet(Integer page, Integer size, String sort,
                                                      String sortBy, String direction) {
        Pageable pageable = toPageable(page, size);
        Sort.Direction sortDirection = resolveDirection(direction);
        String sortField = resolveSortField(sortBy, sort);
        Page<PersonResponse> people = personService.getAll(pageable, sortField, sortDirection);
        PagedModel<PersonResponse> body = new PagedModel<>(people);
        return ResponseEntity.ok((PagedModel) body);
    }

    @Override
    public ResponseEntity<PersonResponse> apiV1PersonsIdDelete(Long id) {
        return ResponseEntity.ok(personService.delete(id));
    }

    @Override
    public ResponseEntity<PersonResponse> apiV1PersonsIdGet(Long id) {
        return ResponseEntity.ok(personService.getById(id));
    }

    @Override
    public ResponseEntity<PersonResponse> apiV1PersonsIdPatch(Long id, PersonUpdateRequest personUpdateRequest) {
        return ResponseEntity.ok(personService.update(id, personUpdateRequest));
    }

    @Override
    public ResponseEntity<List<PersonResponse>> apiV1PersonsPatch(List<Long> ids,
                                                                  @Valid PersonUpdateRequest personUpdateRequest) {
        return ResponseEntity.ok(personService.updateMany(ids, personUpdateRequest));
    }

    @Override
    public ResponseEntity<PersonResponse> apiV1PersonsPost(@Valid PersonAddRequest personAddRequest) {
        return ResponseEntity.ok(personService.create(personAddRequest));
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
