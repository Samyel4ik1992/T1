package org.example.test_task.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.test_task.dto.TimeEntryDto;
import org.example.test_task.facade.TimeFacade;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/records")
@Tag(name = "Time Records", description = "Операции над временными записями")
public class TimeController {

    private final TimeFacade facade;

    public TimeController(TimeFacade facade) {
        this.facade = facade;
    }

    @GetMapping
    @Operation(
            summary = "Получить список записей времени (с пагинацией)",
            description = "Возвращает записи времени с возможностью постраничной навигации"
    )
    public Page<TimeEntryDto> getRecords(
            @Parameter(description = "Номер страницы (начинается с 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Размер страницы")
            @RequestParam(defaultValue = "100") int size
    ) {
        return facade.getPage(page, size);
    }
}