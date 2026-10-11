package com.platform.ratelimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/demo")
    public DemoResponse demo() {
        return new DemoResponse(
                "Rate limiter service is active and functioning correctly!",
                Instant.now().toString()
        );
    }

    public record DemoResponse(
            String message,
            String timestamp
    ) {}
}
