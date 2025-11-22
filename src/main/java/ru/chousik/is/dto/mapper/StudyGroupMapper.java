package ru.chousik.is.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import ru.chousik.is.api.model.StudyGroupResponse;
import ru.chousik.is.entity.StudyGroup;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {CoordinatesMapper.class, PersonMapper.class})
public interface StudyGroupMapper {
    StudyGroup toEntity(StudyGroupResponse studyGroupResponse);

    StudyGroupResponse toStudyGroupResponse(StudyGroup studyGroup);

    default LocalDateTime map(OffsetDateTime source) {
        return source == null ? null : source.toLocalDateTime();
    }

    default OffsetDateTime map(LocalDateTime source) {
        return source == null ? null : source.atOffset(ZoneOffset.UTC);
    }
}
