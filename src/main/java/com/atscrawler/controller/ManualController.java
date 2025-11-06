package com.atscrawler.controller;

import com.atscrawler.scheduler.DailySync;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManualController {

    private final DailySync scheduler;

    public ManualController(DailySync scheduler) {
        this.scheduler = scheduler;
    }

    @GetMapping("/run-now")
    public String runNow() {
        scheduler.runDailySync(); // chama o método do agendador manualmente
        return "✅ Daily sync triggered manually!";
    }
}
