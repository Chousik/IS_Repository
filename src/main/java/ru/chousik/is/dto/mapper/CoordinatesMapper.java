package ru.chousik.is.dto.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import ru.chousik.is.api.model.CoordinatesAddRequest;
import ru.chousik.is.api.model.CoordinatesResponse;
import ru.chousik.is.api.model.CoordinatesUpdateRequest;
import ru.chousik.is.entity.Coordinates;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface CoordinatesMapper {
    Coordinates toEntity(CoordinatesResponse coordinatesResponse);

    CoordinatesResponse toCoordinatesResponse(Coordinates coordinates);

    Coordinates toEntity(CoordinatesAddRequest coordinatesAddRequest);

    CoordinatesAddRequest toCoordinatesAddRequest(Coordinates coordinates);

    Coordinates toEntity(CoordinatesUpdateRequest coordinatesUpdateRequest);

    CoordinatesUpdateRequest toCoordinatesUpdateRequest(Coordinates coordinates);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateWithNull(CoordinatesUpdateRequest request, @MappingTarget Coordinates coordinates);
}
