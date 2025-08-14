package scaputo88.com.example.rinha_25.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import scaputo88.com.example.rinha_25.service.QueueMonitorService;
import scaputo88.com.example.rinha_25.service.QueueMonitorService.QueueStatus;

@RestController
public class QueueMonitorController {

    private final QueueMonitorService queueMonitorService;

    public QueueMonitorController(QueueMonitorService queueMonitorService) {
        this.queueMonitorService = queueMonitorService;
    }

    @GetMapping("/queue/status")
    public QueueStatus status() {
        return queueMonitorService.snapshot();
    }
}
