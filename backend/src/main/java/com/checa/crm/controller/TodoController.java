package com.checa.crm.controller;

import com.checa.crm.common.ApiResponse;
import com.checa.crm.service.BusinessEventTodoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TodoController {
    private final BusinessEventTodoService service;

    public TodoController(BusinessEventTodoService service) {
        this.service = service;
    }

    @PostMapping("/todos/auto-generate")
    public ApiResponse<Map<String, Object>> autoGenerate(@RequestAttribute("userId") String actor) {
        return ApiResponse.ok(service.generateAutoTodos(actor));
    }
}
