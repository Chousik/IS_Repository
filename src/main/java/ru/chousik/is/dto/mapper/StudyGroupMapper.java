package ru.chousik.is.dto.mapper;

import org.mapstruct.*;
import ru.chousik.is.dto.StudyGroupDto;
import ru.chousik.is.entity.StudyGroup;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface StudyGroupMapper {
    @Mapping(source = "groupAdminId", target = "groupAdmin.id")
    @Mapping(source = "coordinatesId", target = "coordinates.id")
    StudyGroup toEntity(StudyGroupDto studyGroupDto);

    @InheritInverseConfiguration(name = "toEntity")
    StudyGroupDto toStudyGroupDto(StudyGroup studyGroup);
}