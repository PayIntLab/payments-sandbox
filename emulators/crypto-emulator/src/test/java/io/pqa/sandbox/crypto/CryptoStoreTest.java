package io.pqa.sandbox.crypto;

import io.pqa.sandbox.crypto.model.CryptoDeposit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoStoreTest {
    @Test
    void confirmedOnlyAfterRequiredConfirmations() {
        CryptoStore store = new CryptoStore();
        CryptoDeposit d = new CryptoDeposit("dep_1", "ORD-1", "USDT",
                "addr", "25.50", "0.00", "0xabc");
        assertFalse(store.applyConfirmations(d, 1));
        assertFalse(store.applyConfirmations(d, 2));
        assertTrue(store.applyConfirmations(d, 3));
        assertEquals("CONFIRMED", d.status);
    }

    @Test
    void neverConfirmedTwice() {
        CryptoStore store = new CryptoStore();
        CryptoDeposit d = new CryptoDeposit("dep_2", "ORD-2", "BTC",
                "addr", "0.001", "0.00001", "0xdef");
        assertTrue(store.applyConfirmations(d, 6));
        assertFalse(store.applyConfirmations(d, 12));
    }
}
