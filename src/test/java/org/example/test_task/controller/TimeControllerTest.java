package org.example.test_task.controller;

import org.example.test_task.model.TimeEntry;
import org.example.test_task.repository.TimeEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class TimeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TimeEntryRepository repository;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        repository.save(new TimeEntry(LocalDateTime.now().minusSeconds(2)));
        repository.save(new TimeEntry(LocalDateTime.now().minusSeconds(1)));
        repository.save(new TimeEntry(LocalDateTime.now()));
    }

    @Test
    @DisplayName("GET /records возвращает первую страницу с 2 записями и мета-данными пагинации")
    void testGetRecordsWithPagination() throws Exception {
        mockMvc.perform(get("/records?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.size", is(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)));
    }
}