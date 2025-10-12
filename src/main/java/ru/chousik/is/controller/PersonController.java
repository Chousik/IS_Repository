package ru.chousik.is.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;
import ru.chousik.is.dto.request.PersonAddRequest;
import ru.chousik.is.dto.request.PersonUpdateRequest;
import ru.chousik.is.dto.response.PersonResponse;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.service.PersonService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/persons")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;

    @GetMapping
    public PagedModel<PersonResponse> getAll(Pageable pageable,
                                             @RequestParam(required = false) String sortBy,
                                             @RequestParam(required = false, defaultValue = "asc") String direction) {
        Sort.Direction sortDirection = resolveDirection(direction);
        Page<PersonResponse> people = personService.getAll(pageable, sortBy, sortDirection);
        return new PagedModel<>(people);
    }

    @GetMapping("/{id}")
    public PersonResponse getOne(@PathVariable Long id) {
        return personService.getById(id);
    }

    @GetMapping("/by-ids")
    public List<PersonResponse> getMany(@RequestParam List<Long> ids) {
        return personService.getByIds(ids);
    }

    @PostMapping
    public PersonResponse create(@RequestBody @Valid PersonAddRequest request) {
        return personService.create(request);
    }

    @PatchMapping("/{id}")
    public PersonResponse patch(@PathVariable Long id, @RequestBody @Valid PersonUpdateRequest request) {
        return personService.update(id, request);
    }

    @PatchMapping
    public List<PersonResponse> patchMany(@RequestParam List<Long> ids, @RequestBody @Valid PersonUpdateRequest request) {
        return personService.updateMany(ids, request);
    }

    @DeleteMapping("/{id}")
    public PersonResponse delete(@PathVariable Long id) {
        return personService.delete(id);
    }

    @DeleteMapping
    public void deleteMany(@RequestParam List<Long> ids) {
        personService.deleteMany(ids);
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
