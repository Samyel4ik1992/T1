package org.example.test_task;

import org.example.test_task.model.TimeEntry;
import org.example.test_task.service.TimeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class TimeServiceIntegrationTest {

    @Autowired
    private TimeService timeService;

    // Запускаем Postgres-контейнер
    @Container
    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("testdb")
                    .withUsername("user")
                    .withPassword("pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void testDatabaseConnectionLossAndRecovery() throws Exception {
        // Подождём 3 секунды, чтобы записались первые таймштампы
        Thread.sleep(3000);

        List<TimeEntry> initialEntries = timeService.getAll();
        System.out.println("До остановки БД: " + initialEntries.size());
        assertThat(initialEntries).isNotEmpty();

        // Останавливаем БД (эмуляция падения)
        postgres.stop();
        System.out.println("Postgres остановлен");

        // Ждём несколько секунд, чтобы сервис попытался писать и собрал буфер
        Thread.sleep(5000);

        // Перезапускаем БД
        postgres.start();
        System.out.println("Postgres снова запущен");

        // Даем время, чтобы буфер дозаписался после восстановления
        Thread.sleep(10000);

        // Проверяем итоговые записи
        List<TimeEntry> allEntries = timeService.getAll();
        System.out.println("После восстановления: " + allEntries.size());
        assertThat(allEntries.size()).isGreaterThan(initialEntries.size());

        // Проверяем, что все записи строго по возрастанию времени (хронологически)
        LocalDateTime prevTime = null;
        for (TimeEntry entry : allEntries) {
            if (prevTime != null) {
                Assertions.assertTrue(
                        !entry.getTime().isBefore(prevTime),
                        "Порядок нарушен: " + entry.getTime() + " идёт после " + prevTime
                );
            }
            prevTime = entry.getTime();
        }
    }
}
