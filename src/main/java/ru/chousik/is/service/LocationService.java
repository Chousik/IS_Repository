package ru.chousik.is.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.stereotype.Service;
import ru.chousik.is.api.model.LocationAddRequest;
import ru.chousik.is.api.model.LocationResponse;
import ru.chousik.is.api.model.LocationUpdateRequest;
import ru.chousik.is.dto.mapper.LocationMapper;
import ru.chousik.is.entity.Location;
import ru.chousik.is.event.EntityChangeNotifier;
import ru.chousik.is.exception.BadRequestException;
import ru.chousik.is.exception.NotFoundException;
import ru.chousik.is.repository.LocationRepository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;
    private final EntityChangeNotifier entityChangeNotifier;

    public Page<LocationResponse> getAll(Pageable pageable, String sortBy, Sort.Direction direction) {
        Pageable sortedPageable = applySorting(pageable, sortBy, direction);
        Page<Location> page = locationRepository.findAll(sortedPageable);
        return page.map(locationMapper::toLocationResponse);
    }

    public LocationResponse getById(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Локация с идентификатором %d не найдена".formatted(id)));
        return locationMapper.toLocationResponse(location);
    }

    public List<LocationResponse> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Location> locations = locationRepository.findAllById(ids);
        return locations.stream()
                .map(locationMapper::toLocationResponse)
                .toList();
    }

    public LocationResponse create(LocationAddRequest request) {
        Location location = locationMapper.toEntity(request);
        Location saved = locationRepository.save(location);
        LocationResponse response = locationMapper.toLocationResponse(saved);
        entityChangeNotifier.publish("LOCATION", "CREATED", response);
        return response;
    }

    public LocationResponse update(Long id, LocationUpdateRequest request) {
        validateUpdateRequest(request);

        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Локация с идентификатором %d не найдена".formatted(id)));

        locationMapper.updateWithNull(request, location);
        Location saved = locationRepository.save(location);
        LocationResponse response = locationMapper.toLocationResponse(saved);
        entityChangeNotifier.publish("LOCATION", "UPDATED", response);
        return response;
    }

    public List<LocationResponse> updateMany(List<Long> ids, LocationUpdateRequest request) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        validateUpdateRequest(request);

        Collection<Location> locations = locationRepository.findAllById(ids);
        validateAllIdsPresent(ids, locations);

        for (Location location : locations) {
            locationMapper.updateWithNull(request, location);
        }

        List<Location> saved = locationRepository.saveAll(locations);
        List<LocationResponse> responses = saved.stream()
                .map(locationMapper::toLocationResponse)
                .toList();
        responses.forEach(response -> entityChangeNotifier.publish("LOCATION", "UPDATED", response));
        return responses;
    }

    public LocationResponse delete(Long id) {
        Location location = locationRepository.findById(id).orElse(null);
        if (location == null) {
            entityChangeNotifier.publish("LOCATION", "DELETED", new DeletedPayload(id));
            return null;
        }
        locationRepository.delete(location);
        LocationResponse response = locationMapper.toLocationResponse(location);
        entityChangeNotifier.publish("LOCATION", "DELETED", response);
        return response;
    }

    public void deleteMany(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Collection<Location> locations = locationRepository.findAllById(ids);
        validateAllIdsPresent(ids, locations);
        List<LocationResponse> responses = locations.stream()
                .map(locationMapper::toLocationResponse)
                .toList();
        locationRepository.deleteAll(locations);
        responses.forEach(response -> entityChangeNotifier.publish("LOCATION", "DELETED", response));
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

    private void validateUpdateRequest(LocationUpdateRequest request) {
        if (request == null || (request.getX() == null && request.getY() == null
                && request.getZ() == null && request.getName() == null)) {
            throw new BadRequestException("Не переданы поля для обновления локации");
        }
        if (request.getName() != null && request.getName().isBlank()) {
            throw new BadRequestException("Поле name не может быть пустым");
        }
    }

    private void validateAllIdsPresent(List<Long> requestedIds, Collection<Location> foundEntities) {
        Set<Long> foundIds = foundEntities.stream()
                .map(Location::getId)
                .collect(Collectors.toSet());
        List<Long> missing = requestedIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new NotFoundException("Локации с идентификаторами %s не найдены".formatted(missing));
        }
    }
}
