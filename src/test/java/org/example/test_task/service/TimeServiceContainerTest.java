package org.example.test_task.service;

import org.example.test_task.model.TimeEntry;
import org.example.test_task.repository.TimeEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(locations = "classpath:application-test.properties")
public class TimeServiceContainerTest {

    @Autowired
    private TimeService service;

    @Autowired
    private TimeEntryRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Сохраняет одну запись времени в базу данных")
    void testWriteTimeToDatabase() {
        service.writeTime();
        List<TimeEntry> all = repository.findAll();
        assertThat(all).hasSize(1);
    }

    @Test
    @Order(2)
    @DisplayName("Запись в буфер и успешное восстановление при повторной попытке")
    void testBufferedWriteAndRecovery() throws Exception {
        TimeEntry entry1 = new TimeEntry(LocalDateTime.now().minusSeconds(2));
        TimeEntry entry2 = new TimeEntry(LocalDateTime.now().minusSeconds(1));

        var bufferField = service.getClass().getDeclaredField("buffer");
        bufferField.setAccessible(true);
        List<TimeEntry> buffer = (List<TimeEntry>) bufferField.get(service);
        buffer.add(entry1);
        buffer.add(entry2);

        service.retryBufferedEntries();

        List<TimeEntry> all = repository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getTime()).isBefore(all.get(1).getTime());
    }

    @Test
    @Order(3)
    @DisplayName("Проверка хронологического порядка записей без сортировки")
    void testOrderOfEntries() throws InterruptedException {
        service.writeTime();
        Thread.sleep(200);
        service.writeTime();
        Thread.sleep(200);
        service.writeTime();

        List<TimeEntry> all = repository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all.get(0).getTime()).isBefore(all.get(1).getTime());
        assertThat(all.get(1).getTime()).isBefore(all.get(2).getTime());
    }
}
