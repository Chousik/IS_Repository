package ru.chousik.is.dto.mapper;

import org.mapstruct.*;
import ru.chousik.is.dto.StudyGroupDto;
import ru.chousik.is.entity.*;

@Mapper(componentModel = "spring")
public interface StudyGroupMapper {
    @Mapping(target = "coordinatesId", source = "coordinates.id")
    @Mapping(target = "groupAdminId", source = "groupAdmin.id")
    StudyGroupDto toDto(StudyGroup entity);
}
