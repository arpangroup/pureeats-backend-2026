package com.pureeats.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        log.debug("Health check ping received");
        return "pong";
    }
}
