package ru.chousik.is.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.chousik.is.api.model.PersonResponse;
import ru.chousik.is.entity.Person;

@Mapper(
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = LocationMapper.class)
public interface PersonMapper {
    Person toEntity(PersonResponse personResponse);

    PersonResponse toPersonResponse(Person person);
}
