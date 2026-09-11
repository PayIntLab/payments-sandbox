package io.pqa.sandbox.merchant;

import io.pqa.sandbox.merchant.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Scheduled reconciliation. Scans orders that are still PENDING locally and asks
 * the provider what actually happened. If the provider says the payment succeeded,
 * the order is recovered even when the webhook never arrived.
 */
@Service
public class ReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final OrderStore store;
    private final ProviderStatusLookup lookup;
    private final SandboxClock clock;
    private final long intervalSeconds;

    private Instant nextRunAt;
    private long scans;
    private long totalRecovered;
    private Instant lastRunAt;

    public ReconciliationService(OrderStore store,
                                 ProviderStatusLookup lookup,
                                 SandboxClock clock,
                                 SandboxProperties props) {
        this.store = store;
        this.lookup = lookup;
        this.clock = clock;
        this.intervalSeconds = props.getReconciliationIntervalSeconds();
        this.nextRunAt = clock.now().plusSeconds(intervalSeconds);
    }

    @Scheduled(fixedDelayString = "${sandbox.reconciliation.tick-ms:1000}")
    public void tick() {
        if (!clock.now().isBefore(nextRunAt)) {
            runScan("scheduled");
        }
    }

    /** Runs a scan immediately, used by the drill and by tests. */
    public synchronized Map<String, Object> runNow() {
        return runScan("manual");
    }

    private synchronized Map<String, Object> runScan(String trigger) {
        scans++;
        lastRunAt = clock.now();
        nextRunAt = lastRunAt.plusSeconds(intervalSeconds);

        int checked = 0;
        int recovered = 0;
        for (Order order : store.byId.values()) {
            if (!"PENDING".equals(order.status) || order.externalId == null) {
                continue;
            }
            checked++;
            String providerStatus;
            try {
                providerStatus = lookup.providerStatus(order);
            } catch (RuntimeException ex) {
                log.warn("[reconciliation] provider lookup failed for order {}: {}",
                        order.id, ex.getMessage());
                continue;
            }
            if (providerStatus == null || !isPaidStatus(order.provider, providerStatus)) {
                continue;
            }
            order.markPaidByReconciliation(providerStatus, lastRunAt);
            recovered++;
            totalRecovered++;
            log.info("[reconciliation] recovered order {} ({}): provider={}, local=PENDING -> PAID, webhook was missing",
                    order.id, order.provider, providerStatus);
        }

        log.info("[reconciliation] t={} scan #{} ({}): {} pending checked, {} recovered",
                lastRunAt, scans, trigger, checked, recovered);

        return Map.of(
                "scans", scans,
                "trigger", trigger,
                "simulated_time", lastRunAt.toString(),
                "next_run_at", nextRunAt.toString(),
                "pending_checked", checked,
                "recovered", recovered,
                "total_recovered", totalRecovered);
    }

    public Map<String, Object> report() {
        return Map.of(
                "scans", scans,
                "last_run_at", lastRunAt == null ? "" : lastRunAt.toString(),
                "next_run_at", nextRunAt.toString(),
                "interval_seconds", intervalSeconds,
                "total_recovered", totalRecovered,
                "simulated_offset_seconds", clock.offsetSeconds());
    }

    private static boolean isPaidStatus(String provider, String status) {
        return switch (provider) {
            case "stripe" -> "succeeded".equals(status);
            case "paypal" -> "COMPLETED".equals(status);
            case "card" -> "CAPTURED".equals(status);
            default -> false;
        };
    }
}
