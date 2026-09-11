package io.pqa.sandbox.merchant;

import io.pqa.sandbox.merchant.model.Order;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationServiceTest {
    private final SandboxClock clock = new SandboxClock();
    private final OrderStore store = new OrderStore();
    private final SandboxProperties props = new SandboxProperties();

    @Test
    void recoversPendingOrderWhenProviderSaysPaid() {
        Order order = store.create("stripe", 29900, "usd");
        order.externalId = "pi_test_1";

        ReconciliationService service =
                new ReconciliationService(store, o -> "succeeded", clock, props);
        Map<String, Object> report = service.runNow();

        assertThat(order.status).isEqualTo("PAID");
        assertThat(order.paidSource).isEqualTo("reconciliation");
        assertThat(order.recoveredAt).isNotNull();
        assertThat(report.get("recovered")).isEqualTo(1);
    }

    @Test
    void doesNotRecoverTwiceAndLeavesUnpaidOrdersAlone() {
        Order paid = store.create("stripe", 100, "usd");
        paid.externalId = "pi_a";
        Order unpaid = store.create("stripe", 100, "usd");
        unpaid.externalId = "pi_b";

        ReconciliationService service = new ReconciliationService(
                store,
                o -> "pi_a".equals(o.externalId) ? "succeeded" : "requires_confirmation",
                clock, props);

        service.runNow();
        Map<String, Object> second = service.runNow();

        assertThat(paid.status).isEqualTo("PAID");
        assertThat(paid.paidSource).isEqualTo("reconciliation");
        assertThat(unpaid.status).isEqualTo("PENDING");
        assertThat(second.get("pending_checked")).isEqualTo(1);
        assertThat(second.get("recovered")).isEqualTo(0);
    }

    @Test
    void scheduledTickRunsOnlyAfterTheInterval() {
        Order order = store.create("stripe", 100, "usd");
        order.externalId = "pi_tick";

        ReconciliationService service =
                new ReconciliationService(store, o -> "succeeded", clock, props);

        service.tick(); // still before the first interval, no scan
        assertThat(order.status).isEqualTo("PENDING");

        clock.advance(300); // drill advances the sandbox clock by five minutes
        service.tick();
        assertThat(order.status).isEqualTo("PAID");
    }

    @Test
    void skipsProvidersWithoutALookup() {
        Order order = store.create("crypto", 100, "usd");
        order.externalId = "addr_1";

        ReconciliationService service =
                new ReconciliationService(store, o -> null, clock, props);
        Map<String, Object> report = service.runNow();

        assertThat(order.status).isEqualTo("PENDING");
        assertThat(report.get("recovered")).isEqualTo(0);
    }
}
