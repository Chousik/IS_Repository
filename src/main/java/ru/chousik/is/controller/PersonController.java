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
import ru.chousik.is.api.PersonsApi;
import ru.chousik.is.api.model.PersonAddRequest;
import ru.chousik.is.api.model.PersonResponse;
import ru.chousik.is.api.model.PersonUpdateRequest;
import ru.chousik.is.service.PersonService;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class PersonController extends PageHelper implements PersonsApi {

    private final PersonService personService;

    @Override
    public ResponseEntity<List<PersonResponse>> apiV1PersonsByIdsGet(
            @RequestParam(value = "ids", required = true) List<Long> ids) {
        return ResponseEntity.ok(personService.getByIds(ids));
    }

    @Override
    public ResponseEntity<Void> apiV1PersonsDelete(
            @RequestParam(value = "ids", required = true) List<Long> ids) {
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
    public ResponseEntity<PersonResponse> apiV1PersonsIdDelete(
            @PathVariable("id") Long id) {
        PersonResponse response = personService.delete(id);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PersonResponse> apiV1PersonsIdGet(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(personService.getById(id));
    }

    @Override
    public ResponseEntity<PersonResponse> apiV1PersonsIdPatch(
            @PathVariable("id") Long id,
            @RequestBody PersonUpdateRequest personUpdateRequest) {
        return ResponseEntity.ok(personService.update(id, personUpdateRequest));
    }

    @Override
    public ResponseEntity<List<PersonResponse>> apiV1PersonsPatch(
            @RequestParam(value = "ids", required = true) List<Long> ids,
            @RequestBody PersonUpdateRequest personUpdateRequest) {
        return ResponseEntity.ok(personService.updateMany(ids, personUpdateRequest));
    }

    @Override
    public ResponseEntity<PersonResponse> apiV1PersonsPost(
            @RequestBody PersonAddRequest personAddRequest) {
        return ResponseEntity.ok(personService.create(personAddRequest));
    }
}
