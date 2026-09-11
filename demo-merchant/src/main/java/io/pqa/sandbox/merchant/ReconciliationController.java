package io.pqa.sandbox.merchant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Drill endpoints. Not part of a real merchant integration.
 */
@RestController
public class ReconciliationController {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);
    private final ReconciliationService reconciliation;
    private final SandboxClock clock;

    public ReconciliationController(ReconciliationService reconciliation, SandboxClock clock) {
        this.reconciliation = reconciliation;
        this.clock = clock;
    }

    @GetMapping("/reconciliation/report")
    public Map<String, Object> report() {
        return reconciliation.report();
    }

    @PostMapping("/reconciliation/run")
    public Map<String, Object> run() {
        return reconciliation.runNow();
    }

    @GetMapping("/dev/clock")
    public Map<String, Object> now() {
        return Map.of(
                "simulated_time", clock.now().toString(),
                "offset_seconds", clock.offsetSeconds());
    }

    @PostMapping("/dev/clock/advance")
    public Map<String, Object> advance(@RequestBody Map<String, Object> body) {
        long seconds = Long.parseLong(String.valueOf(body.getOrDefault("seconds", 300)));
        long offset = clock.advance(seconds);
        log.info("[clock] sandbox clock advanced by {}s (offset={}s, now={})",
                seconds, offset, clock.now());
        return Map.of(
                "advanced_seconds", seconds,
                "offset_seconds", offset,
                "simulated_time", clock.now().toString());
    }
}
