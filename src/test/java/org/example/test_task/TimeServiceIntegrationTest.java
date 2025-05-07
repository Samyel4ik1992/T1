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
import org.testcontainers.DockerClientFactory;
import com.github.dockerjava.api.DockerClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class TimeServiceIntegrationTest {

    @Autowired
    private TimeService timeService;

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
    void testDatabasePauseAndResume() throws Exception {
        // Ждём 3 секунды, чтобы сервис успел что-то записать
        Thread.sleep(3000);

        List<TimeEntry> beforePause = timeService.getAll();
        System.out.println("До приостановки БД: " + beforePause.size());
        assertThat(beforePause).isNotEmpty();

        // Приостанавливаем контейнер (имитируем перегруженную БД)
        DockerClient dockerClient = DockerClientFactory.instance().client();
        dockerClient.pauseContainerCmd(postgres.getContainerId()).exec();
        System.out.println("Postgres приостановлен");

        // Ждём 5 секунд — время накопления буфера
        Thread.sleep(5000);

        // Возобновляем контейнер
        dockerClient.unpauseContainerCmd(postgres.getContainerId()).exec();
        System.out.println("Postgres возобновлён");

        // Даем сервису время дозаписать из буфера
        Thread.sleep(10000);

        List<TimeEntry> afterResume = timeService.getAll();
        System.out.println("После возобновления: " + afterResume.size());
        assertThat(afterResume.size()).isGreaterThan(beforePause.size());

        // Проверка корректности порядка
        LocalDateTime prev = null;
        for (TimeEntry entry : afterResume) {
            if (prev != null) {
                Assertions.assertFalse(entry.getTime().isBefore(prev), "Нарушен порядок: " + entry.getTime() + " перед " + prev);
            }
            prev = entry.getTime();
        }
    }
}
