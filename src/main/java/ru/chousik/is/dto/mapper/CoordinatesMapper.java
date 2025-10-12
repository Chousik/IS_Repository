package ru.chousik.is.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.chousik.is.dto.request.CoordinatesAddRequest;
import ru.chousik.is.dto.request.CoordinatesUpdateRequest;
import ru.chousik.is.dto.response.CoordinatesResponse;
import ru.chousik.is.entity.Coordinates;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface CoordinatesMapper {
    Coordinates toEntity(CoordinatesResponse coordinatesResponse);

    CoordinatesResponse toCoordinatesResponse(Coordinates coordinates);

    Coordinates toEntity(CoordinatesAddRequest coordinatesAddRequest);

    CoordinatesAddRequest toCoordinatesAddRequest(Coordinates coordinates);

    Coordinates toEntity(CoordinatesUpdateRequest coordinatesUpdateRequest);

    CoordinatesUpdateRequest toCoordinatesUpdateRequest(Coordinates coordinates);
}