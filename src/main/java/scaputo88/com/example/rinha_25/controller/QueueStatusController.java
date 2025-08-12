package scaputo88.com.example.rinha_25.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import scaputo88.com.example.rinha_25.service.QueueMonitorService;

@RestController
public class QueueStatusController {

    private final QueueMonitorService service;

    public QueueStatusController(QueueMonitorService service) {
        this.service = service;
    }

    @GetMapping("/internal/queue-status")
    public QueueMonitorService.QueueStatus status() {
        return service.snapshot();
    }
}


