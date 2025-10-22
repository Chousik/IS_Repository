package ru.chousik.is.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;
import ru.chousik.is.dto.mapper.CoordinatesMapper;
import ru.chousik.is.dto.request.CoordinatesAddRequest;
import ru.chousik.is.dto.request.CoordinatesUpdateRequest;
import ru.chousik.is.dto.response.CoordinatesResponse;
import ru.chousik.is.entity.Coordinates;
import ru.chousik.is.event.EntityChangeNotifier;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.exception.NotFoundException;
import ru.chousik.is.repository.CoordinatesRepository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class CoordinatesService {
    private final CoordinatesRepository coordinatesRepository;

    private final CoordinatesMapper coordinatesMapper;

    private final EntityChangeNotifier entityChangeNotifier;

    public Page<CoordinatesResponse> getAll(Pageable pageable, String sortBy, Sort.Direction direction) {
        Pageable sortedPageable = applySorting(pageable, sortBy, direction);
        Page<Coordinates> page = coordinatesRepository.findAll(sortedPageable);
        return page.map(coordinatesMapper::toCoordinatesResponse);
    }

    public CoordinatesResponse getById(Long id) {
        Coordinates coordinates = coordinatesRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Сущность с идентификатором %d не найдена".formatted(id)));
        return coordinatesMapper.toCoordinatesResponse(coordinates);
    }

    public List<CoordinatesResponse> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Coordinates> coordinates = coordinatesRepository.findAllById(ids);
        return coordinates.stream()
                .map(coordinatesMapper::toCoordinatesResponse)
                .toList();
    }

    public CoordinatesResponse create(CoordinatesAddRequest request) {
        Coordinates coordinates = coordinatesMapper.toEntity(request);
        Coordinates saved = coordinatesRepository.save(coordinates);
        CoordinatesResponse response = coordinatesMapper.toCoordinatesResponse(saved);
        entityChangeNotifier.publish("COORDINATES", "CREATED", response);
        return response;
    }

    public CoordinatesResponse update(Long id, CoordinatesUpdateRequest request) {
        if ((request.x() == null) && (request.y() == null)) {
            throw new BadRequestException("Не переданы поля для обновления координат");
        }

        Coordinates coordinates = coordinatesRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Сущность с идентификатором %d не найдена".formatted(id)));

        coordinatesMapper.updateWithNull(request, coordinates);
        Coordinates saved = coordinatesRepository.save(coordinates);
        CoordinatesResponse response = coordinatesMapper.toCoordinatesResponse(saved);
        entityChangeNotifier.publish("COORDINATES", "UPDATED", response);
        return response;
    }

    public List<CoordinatesResponse> updateMany(List<Long> ids, CoordinatesUpdateRequest request) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Collection<Coordinates> coordinates = coordinatesRepository.findAllById(ids);
        validateAllIdsPresent(ids, coordinates);

        for (Coordinates coordinate : coordinates) {
            coordinatesMapper.updateWithNull(request, coordinate);
        }

        List<Coordinates> saved = coordinatesRepository.saveAll(coordinates);
        List<CoordinatesResponse> responses = saved.stream()
                .map(coordinatesMapper::toCoordinatesResponse)
                .toList();
        responses.forEach(response -> entityChangeNotifier.publish("COORDINATES", "UPDATED", response));
        return responses;
    }

    public CoordinatesResponse delete(Long id) {
        Coordinates coordinates = coordinatesRepository.findById(id).orElse(null);
        if (coordinates == null) {
            entityChangeNotifier.publish("COORDINATES", "DELETED", new DeletedPayload(id));
            return null;
        }
        coordinatesRepository.delete(coordinates);
        CoordinatesResponse response = coordinatesMapper.toCoordinatesResponse(coordinates);
        entityChangeNotifier.publish("COORDINATES", "DELETED", response);
        return response;
    }

    public void deleteMany(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Collection<Coordinates> coordinates = coordinatesRepository.findAllById(ids);
        validateAllIdsPresent(ids, coordinates);
        List<CoordinatesResponse> responses = coordinates.stream()
                .map(coordinatesMapper::toCoordinatesResponse)
                .toList();
        coordinatesRepository.deleteAll(coordinates);
        responses.forEach(response -> entityChangeNotifier.publish("COORDINATES", "DELETED", response));
    }

    private record DeletedPayload(Long id) {}

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

    private void validateAllIdsPresent(List<Long> requestedIds, Collection<Coordinates> foundEntities) {
        Set<Long> foundIds = foundEntities.stream()
                .map(Coordinates::getId)
                .collect(Collectors.toSet());
        List<Long> missing = requestedIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new NotFoundException("Сущности с идентификаторами %s не найдены".formatted(missing));
        }
    }
}
