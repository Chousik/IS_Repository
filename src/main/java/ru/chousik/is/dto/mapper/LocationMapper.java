package ru.chousik.is.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.chousik.is.dto.response.LocationResponse;
import ru.chousik.is.entity.Location;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface LocationMapper {
    Location toEntity(LocationResponse locationResponse);

    LocationResponse toLocationResponse(Location location);
}