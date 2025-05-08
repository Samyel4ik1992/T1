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
    private final TimeSaver timeSaver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Очереди
    private final BlockingQueue<TimeEntry> mainQueue = new LinkedBlockingQueue<>(1000); // расширена
    private final BlockingQueue<TimeEntry> bufferQueue = new LinkedBlockingQueue<>();

    @Autowired
    public TimeService(TimeEntryRepository repository, TimeSaver timeSaver) {
        this.repository = repository;
        this.timeSaver = timeSaver;
    }

    @PostConstruct
    public void loadBuffer() {
        File file = new File(BUFFER_FILE);
        if (file.exists()) {
            try {
                CollectionType listType = objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, TimeEntry.class);
                List<TimeEntry> loaded = objectMapper.readValue(file, listType);
                bufferQueue.addAll(loaded);
                log.info("🔄 Загружено {} записей из буфера", loaded.size());
            } catch (IOException e) {
                log.error("❌ Ошибка при загрузке буфера из файла", e);
            }
        }
    }

    @PreDestroy
    public void saveBufferToFile() {
        if (!bufferQueue.isEmpty()) {
            try {
                objectMapper.writeValue(new File(BUFFER_FILE), bufferQueue);
                log.info("💾 Сохранили буфер из {} записей в файл", bufferQueue.size());
            } catch (IOException e) {
                log.error("❌ Ошибка при сохранении буфера в файл", e);
            }
        }
    }

    @Scheduled(fixedRate = 1000)
    public void writeTime() {
        LocalDateTime now = LocalDateTime.now();
        TimeEntry entry = new TimeEntry(now);
        try {
            mainQueue.put(entry); // блокирующее добавление
            log.info("📥 Поставили в очередь новую запись: {}", now);
            log.debug("mainQueue.size = {}, bufferQueue.size = {}", mainQueue.size(), bufferQueue.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("⚠️ Поток был прерван при добавлении в очередь");
        }
    }

    @PostConstruct
    public void startWorker() {
        Thread worker = new Thread(() -> {
            while (true) {
                TimeEntry entry = null;
                try {
                    entry = bufferQueue.poll();
                    if (entry != null) {
                        log.info("🔁 Обрабатываем из буфера: {}", entry.getTime());
                    } else {
                        entry = mainQueue.take();
                        log.info("➡️ Обрабатываем из очереди: {}", entry.getTime());
                    }

                    timeSaver.save(entry);
                    log.info("✅ Сохранили запись: {}", entry.getTime());

                } catch (CannotCreateTransactionException ex) {
                    log.warn("🚫 БД недоступна. Перекладываем в буфер: {}", entry.getTime());
                    requeueBuffer(entry);
                    sleepSilently(2000); // короче — чтобы не терять темп

                } catch (Exception ex) {
                    log.error("❌ Ошибка при сохранении: {}. Перекладываем в буфер", entry.getTime(), ex);
                    requeueBuffer(entry);
                    sleepSilently(2000);
                }
            }
        });

        worker.setDaemon(true);
        worker.start();
    }

    private void requeueBuffer(TimeEntry entry) {
        if (entry != null) {
            try {
                bufferQueue.put(entry); // блокирующее добавление
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⚠️ Поток прерван при добавлении в буфер");
            }
        }
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<TimeEntry> getAll() {
        return repository.findAll();
    }
}
