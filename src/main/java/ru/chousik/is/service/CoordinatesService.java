package ru.chousik.is.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.chousik.is.dto.mapper.CoordinatesMapper;
import ru.chousik.is.repository.CoordinatesRepository;

@RequiredArgsConstructor
@Service
public class CoordinatesService {
    private final CoordinatesRepository coordinatesRepository;

    private final CoordinatesMapper coordinatesMapper;
}
