package org.example.test_task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.test_task.model.TimeEntry;
import org.example.test_task.repository.TimeEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class TimeService {

    private static final Logger log = LoggerFactory.getLogger(TimeService.class);
    private static final String BUFFER_FILE = "buffer.json";

    private final TimeEntryRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Очереди: для новых записей и для буфера при недоступности БД
    private final BlockingQueue<TimeEntry> mainQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<TimeEntry> bufferQueue = new LinkedBlockingQueue<>();

    @Autowired
    public TimeService(TimeEntryRepository repository) {
        this.repository = repository;
    }

    // Загрузка буфера из файла при старте
    @PostConstruct
    public void loadBuffer() {
        File file = new File(BUFFER_FILE);
        if (file.exists()) {
            try {
                CollectionType listType = objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, TimeEntry.class);
                List<TimeEntry> loaded = objectMapper.readValue(file, listType);
                bufferQueue.addAll(loaded);
                log.info("Загружено {} записей из буфера", loaded.size());
            } catch (IOException e) {
                log.error("Ошибка при загрузке буфера из файла", e);
            }
        }
    }

    // Сохранение буфера при завершении работы приложения
    @PreDestroy
    public void saveBufferToFile() {
        if (!bufferQueue.isEmpty()) {
            try {
                objectMapper.writeValue(new File(BUFFER_FILE), bufferQueue);
                log.info("Сохранили буфер из {} записей в файл", bufferQueue.size());
            } catch (IOException e) {
                log.error("Ошибка при сохранении буфера в файл", e);
            }
        }
    }

    // Каждую секунду создаём новую запись и кладём в очередь
    @Scheduled(fixedRate = 1000)
    public void writeTime() {
        LocalDateTime now = LocalDateTime.now();
        TimeEntry entry = new TimeEntry(now);
        mainQueue.offer(entry);
        log.info("Поставили в очередь новую запись: {}", now);
    }

    // Воркер: обрабатывает очередь и сохраняет записи в БД
    @PostConstruct
    public void startWorker() {
        Thread worker = new Thread(() -> {
            while (true) {
                TimeEntry entry = null;
                try {
                    // 1️⃣ Сначала пробуем достать из буфера (приоритет)
                    entry = bufferQueue.poll();
                    if (entry == null) {
                        // 2️⃣ Если буфер пуст, ждём новую запись из основной очереди (блокирующий вызов)
                        entry = mainQueue.take();
                    }
                    // Пытаемся сохранить запись
                    repository.save(entry);
                    log.info("Сохранили запись: {}", entry.getTime());

                } catch (CannotCreateTransactionException ex) {
                    log.warn("БД недоступна. Перекладываем запись в буфер.");
                    bufferQueue.offer(entry);
                    sleepSilently(5000);  // ждём 5 секунд перед следующей попыткой
                } catch (Exception ex) {
                    log.error("Ошибка при сохранении", ex);
                }
            }
        });
        worker.setDaemon(true);  // поток завершится при остановке приложения
        worker.start();
    }

    // Вспомогательный метод для сна
    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Метод для получения всех записей (не изменял)
    public List<TimeEntry> getAll() {
        return repository.findAll();
    }
}
