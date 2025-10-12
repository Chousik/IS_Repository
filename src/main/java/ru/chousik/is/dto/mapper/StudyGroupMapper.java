package ru.chousik.is.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.chousik.is.dto.response.StudyGroupResponse;
import ru.chousik.is.entity.StudyGroup;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface StudyGroupMapper {
    StudyGroup toEntity(StudyGroupResponse studyGroupResponse);

    StudyGroupResponse toStudyGroupResponse(StudyGroup studyGroup);
}