package io.pqa.sandbox.merchant;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Clock for the sandbox demo. Real time plus an offset that the drill can advance,
 * so a five minute reconciliation window can be shown in a few seconds.
 */
@Component
public class SandboxClock {
    private final AtomicLong offsetSeconds = new AtomicLong();

    public Instant now() {
        return Instant.now().plusSeconds(offsetSeconds.get());
    }

    public long advance(long seconds) {
        return offsetSeconds.addAndGet(seconds);
    }

    public long offsetSeconds() {
        return offsetSeconds.get();
    }
}
