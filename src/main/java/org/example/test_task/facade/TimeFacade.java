package org.example.test_task.facade;

import org.example.test_task.dto.TimeEntryDto;
import org.example.test_task.mapper.TimeEntryMapper;
import org.example.test_task.repository.TimeEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class TimeFacade {

    private final TimeEntryRepository repository;
    private final TimeEntryMapper mapper;

    public TimeFacade(TimeEntryRepository repository, TimeEntryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Page<TimeEntryDto> getPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return repository.findAll(pageable)
                .map(mapper::toDto);
    }
}