package ru.chousik.is.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.chousik.is.dto.mapper.CoordinatesMapper;
import ru.chousik.is.dto.response.CoordinatesResponse;
import ru.chousik.is.entity.Coordinates;
import ru.chousik.is.repository.CoordinatesRepository;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/coordinates")
@RequiredArgsConstructor
public class CoordinatesController {

    private final CoordinatesMapper coordinatesMapper;

    private final ObjectMapper objectMapper;

    @GetMapping
    public PagedModel<CoordinatesResponse> getAll(Pageable pageable) {
        Page<Coordinates> coordinates = coordinatesRepository.findAll(pageable);
        Page<CoordinatesResponse> coordinatesResponsePage = coordinates.map(coordinatesMapper::toCoordinatesResponse);
        return new PagedModel<>(coordinatesResponsePage);
    }

    @GetMapping("/{id}")
    public CoordinatesResponse getOne(@PathVariable Long id) {
        Optional<Coordinates> coordinatesOptional = coordinatesRepository.findById(id);
        return coordinatesMapper.toCoordinatesResponse(coordinatesOptional.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id `%s` not found".formatted(id))));
    }

    @GetMapping("/by-ids")
    public List<CoordinatesResponse> getMany(@RequestParam List<Long> ids) {
        List<Coordinates> coordinates = coordinatesRepository.findAllById(ids);
        return coordinates.stream()
                .map(coordinatesMapper::toCoordinatesResponse)
                .toList();
    }

    @PostMapping
    public CoordinatesResponse create(@RequestBody CoordinatesResponse dto) {
        Coordinates coordinates = coordinatesMapper.toEntity(dto);
        Coordinates resultCoordinates = coordinatesRepository.save(coordinates);
        return coordinatesMapper.toCoordinatesResponse(resultCoordinates);
    }

    @PatchMapping("/{id}")
    public CoordinatesResponse patch(@PathVariable Long id, @RequestBody JsonNode patchNode) throws IOException {
        Coordinates coordinates = coordinatesRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id `%s` not found".formatted(id)));

        CoordinatesResponse coordinatesResponse = coordinatesMapper.toCoordinatesResponse(coordinates);
        objectMapper.readerForUpdating(coordinatesResponse).readValue(patchNode);
        coordinatesMapper.updateWithNull(coordinatesResponse, coordinates);

        Coordinates resultCoordinates = coordinatesRepository.save(coordinates);
        return coordinatesMapper.toCoordinatesResponse(resultCoordinates);
    }

    @PatchMapping
    public List<Coordinates> patchMany(@RequestParam @Valid List<Long> ids, @RequestBody JsonNode patchNode) throws IOException {
        Collection<Coordinates> coordinates = coordinatesRepository.findAllById(ids);

        for (Coordinates coordinate : coordinates) {
            CoordinatesResponse coordinatesResponse = coordinatesMapper.toCoordinatesResponse(coordinate);
            objectMapper.readerForUpdating(coordinatesResponse).readValue(patchNode);
            coordinatesMapper.updateWithNull(coordinatesResponse, coordinate);
        }

        return coordinatesRepository.saveAll(coordinates);
    }

    @DeleteMapping("/{id}")
    public CoordinatesResponse delete(@PathVariable Long id) {
        Coordinates coordinates = coordinatesRepository.findById(id).orElse(null);
        if (coordinates != null) {
            coordinatesRepository.delete(coordinates);
        }
        return coordinatesMapper.toCoordinatesResponse(coordinates);
    }

    @DeleteMapping
    public void deleteMany(@RequestParam List<Long> ids) {
        coordinatesRepository.deleteAllById(ids);
    }
}
