package ru.chousik.is.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;
import ru.chousik.is.dto.mapper.LocationMapper;
import ru.chousik.is.dto.mapper.PersonMapper;
import ru.chousik.is.dto.request.LocationAddRequest;
import ru.chousik.is.dto.request.PersonAddRequest;
import ru.chousik.is.dto.request.PersonUpdateRequest;
import ru.chousik.is.dto.response.PersonResponse;
import ru.chousik.is.entity.Location;
import ru.chousik.is.entity.Person;
import ru.chousik.is.event.EntityChangeNotifier;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.exception.NotFoundException;
import ru.chousik.is.repository.LocationRepository;
import ru.chousik.is.repository.PersonRepository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final LocationRepository locationRepository;
    private final PersonMapper personMapper;
    private final LocationMapper locationMapper;
    private final EntityChangeNotifier entityChangeNotifier;

    public Page<PersonResponse> getAll(Pageable pageable, String sortBy, Sort.Direction direction) {
        Pageable sortedPageable = applySorting(pageable, sortBy, direction);
        Page<Person> page = personRepository.findAll(sortedPageable);
        return page.map(personMapper::toPersonResponse);
    }

    public PersonResponse getById(Long id) {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Человек с идентификатором %d не найден".formatted(id)));
        return personMapper.toPersonResponse(person);
    }

    public List<PersonResponse> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Person> people = personRepository.findAllById(ids);
        return people.stream()
                .map(personMapper::toPersonResponse)
                .toList();
    }

    public PersonResponse create(PersonAddRequest request) {
        validateLocationInput(request.locationId(), request.location(), false);

        Location resolvedLocation = resolveLocationForCreate(request.locationId(), request.location());

        Person person = Person.builder()
                .name(request.name())
                .eyeColor(request.eyeColor())
                .hairColor(request.hairColor())
                .location(resolvedLocation)
                .height(request.height())
                .weight(request.weight())
                .nationality(request.nationality())
                .build();

        Person saved = personRepository.save(person);
        PersonResponse response = personMapper.toPersonResponse(saved);
        entityChangeNotifier.publish("PERSON", "CREATED", response);
        return response;
    }

    public PersonResponse update(Long id, PersonUpdateRequest request) {
        validateUpdateRequest(request);

        Person person = personRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Человек с идентификатором %d не найден".formatted(id)));

        applyUpdates(person, request);

        Person saved = personRepository.save(person);
        PersonResponse response = personMapper.toPersonResponse(saved);
        entityChangeNotifier.publish("PERSON", "UPDATED", response);
        return response;
    }

    public List<PersonResponse> updateMany(List<Long> ids, PersonUpdateRequest request) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        validateUpdateRequest(request);

        Collection<Person> people = personRepository.findAllById(ids);
        validateAllIdsPresent(ids, people);

        Location referencedLocation = null;
        if (request.locationId() != null) {
            referencedLocation = resolveExistingLocation(request.locationId());
        }

        for (Person person : people) {
            applyUpdates(person, request, referencedLocation);
        }

        List<Person> saved = personRepository.saveAll(people);
        List<PersonResponse> responses = saved.stream().map(personMapper::toPersonResponse).toList();
        responses.forEach(response -> entityChangeNotifier.publish("PERSON", "UPDATED", response));
        return responses;
    }

    public PersonResponse delete(Long id) {
        Person person = personRepository.findById(id).orElse(null);
        if (person == null) {
            entityChangeNotifier.publish("PERSON", "DELETED", new DeletedPayload(id));
            return null;
        }
        personRepository.delete(person);
        PersonResponse response = personMapper.toPersonResponse(person);
        entityChangeNotifier.publish("PERSON", "DELETED", response);
        return response;
    }

    public void deleteMany(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Collection<Person> people = personRepository.findAllById(ids);
        validateAllIdsPresent(ids, people);
        List<PersonResponse> responses = people.stream().map(personMapper::toPersonResponse).toList();
        personRepository.deleteAll(people);
        responses.forEach(response -> entityChangeNotifier.publish("PERSON", "DELETED", response));
    }

    private record DeletedPayload(Long id) {}

    private void applyUpdates(Person person, PersonUpdateRequest request) {
        applyUpdates(person, request, request.locationId() != null ? resolveExistingLocation(request.locationId()) : null);
    }

    private void applyUpdates(Person person, PersonUpdateRequest request, Location locationFromId) {
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("Поле name не может быть пустым");
            }
            person.setName(request.name());
        }

        if (request.eyeColor() != null) {
            person.setEyeColor(request.eyeColor());
        }

        if (request.hairColor() != null) {
            person.setHairColor(request.hairColor());
        }

        if (request.height() != null) {
            if (request.height() <= 0) {
                throw new BadRequestException("Поле height должно быть больше 0");
            }
            person.setHeight(request.height());
        }

        if (request.weight() != null) {
            if (request.weight() <= 0) {
                throw new BadRequestException("Поле weight должно быть больше 0");
            }
            person.setWeight(request.weight());
        }

        if (Boolean.TRUE.equals(request.removeLocation())) {
            person.setLocation(null);
        } else if (request.locationId() != null) {
            person.setLocation(locationFromId != null ? locationFromId : resolveExistingLocation(request.locationId()));
        } else if (request.location() != null) {
            person.setLocation(locationMapper.toEntity(request.location()));
        }

        if (request.nationality() != null) {
            person.setNationality(request.nationality());
        }
    }

    private Location resolveLocationForCreate(Long locationId, LocationAddRequest locationDto) {
        if (locationId != null) {
            return resolveExistingLocation(locationId);
        }
        if (locationDto != null) {
            return locationMapper.toEntity(locationDto);
        }
        return null;
    }

    private Location resolveExistingLocation(Long locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Локация с идентификатором %d не найдена".formatted(locationId)));
    }

    private void validateUpdateRequest(PersonUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Тело запроса отсутствует");
        }
        boolean hasAnyField = request.name() != null || request.eyeColor() != null || request.hairColor() != null
                || request.locationId() != null || request.location() != null
                || Boolean.TRUE.equals(request.removeLocation()) || request.height() != null
                || request.weight() != null || request.nationality() != null;

        if (!hasAnyField) {
            throw new BadRequestException("Не переданы поля для обновления человека");
        }

        validateLocationInput(request.locationId(), request.location(), Boolean.TRUE.equals(request.removeLocation()));

        if (request.name() != null && request.name().isBlank()) {
            throw new BadRequestException("Поле name не может быть пустым");
        }

        if (request.height() != null && request.height() <= 0) {
            throw new BadRequestException("Поле height должно быть больше 0");
        }

        if (request.weight() != null && request.weight() <= 0) {
            throw new BadRequestException("Поле weight должно быть больше 0");
        }
    }

    private void validateLocationInput(Long locationId, LocationAddRequest locationDto, boolean removeRequested) {
        if (locationId != null && locationDto != null) {
            throw new BadRequestException("Нельзя одновременно указать идентификатор локации и данные новой локации");
        }
        if (removeRequested && (locationId != null || locationDto != null)) {
            throw new BadRequestException("Нельзя удалить локацию и одновременно передавать её данные");
        }
    }

    private Pageable applySorting(Pageable pageable, String sortBy, Sort.Direction direction) {
        String sortField = Objects.requireNonNullElse(sortBy, "id");
        Sort.Direction sortDirection = direction == null ? Sort.Direction.ASC : direction;
        try {
            Sort sort = Sort.by(sortDirection, sortField);
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        } catch (PropertyReferenceException e) {
            throw new BadRequestException("Неизвестное поле сортировки '%s'".formatted(sortField));
        }
    }

    private void validateAllIdsPresent(List<Long> requestedIds, Collection<Person> foundEntities) {
        Set<Long> foundIds = foundEntities.stream()
                .map(Person::getId)
                .collect(Collectors.toSet());
        List<Long> missing = requestedIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new NotFoundException("Люди с идентификаторами %s не найдены".formatted(missing));
        }
    }
}
