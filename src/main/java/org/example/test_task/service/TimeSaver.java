package org.example.test_task.service;

import jakarta.transaction.Transactional;
import org.example.test_task.model.TimeEntry;
import org.example.test_task.repository.TimeEntryRepository;
import org.springframework.stereotype.Service;

@Service
public class TimeSaver {

    private final TimeEntryRepository repository;

    public TimeSaver(TimeEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(TimeEntry entry) {
        repository.save(entry);
    }
}
