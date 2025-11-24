package ru.chousik.is.dto.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import ru.chousik.is.api.model.LocationAddRequest;
import ru.chousik.is.api.model.LocationResponse;
import ru.chousik.is.api.model.LocationUpdateRequest;
import ru.chousik.is.entity.Location;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface LocationMapper {
    Location toEntity(LocationAddRequest locationAddRequest);

    LocationAddRequest toLocationAddRequest(Location location);

    Location toEntity(LocationUpdateRequest locationUpdateRequest);

    LocationUpdateRequest toLocationUpdateRequest(Location location);

    Location toEntity(LocationResponse locationResponse);

    LocationResponse toLocationResponse(Location location);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateWithNull(LocationUpdateRequest request, @MappingTarget Location location);
}
