package org.example.test_task.mapper;

import org.example.test_task.dto.TimeEntryDto;
import org.example.test_task.model.TimeEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TimeEntryMapper {

    @Mapping(source = "time", target = "time")
    TimeEntryDto toDto(TimeEntry entity);
}
