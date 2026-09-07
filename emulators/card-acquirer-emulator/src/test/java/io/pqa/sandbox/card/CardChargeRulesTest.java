package io.pqa.sandbox.card;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardChargeRulesTest {
    @Test
    void approveCardAuthorizes() {
        Map<String, String> r = CardChargeRules.classify("4242424242424242", "12", "2030", "123");
        assertEquals(CardChargeRules.APPROVE, r.get("decision"));
        assertEquals("visa", r.get("brand"));
    }

    @Test
    void insufficientFundsDeclines() {
        Map<String, String> r = CardChargeRules.classify("4000000000000002", "12", "2030", "123");
        assertEquals(CardChargeRules.DECLINE, r.get("decision"));
        assertEquals("insufficient_funds", r.get("reason"));
    }

    @Test
    void threeDsCardRequiresAction() {
        Map<String, String> r = CardChargeRules.classify("4000000000000027", "12", "2030", "123");
        assertEquals(CardChargeRules.REQUIRE_3DS, r.get("decision"));
    }

    @Test
    void expiredCardDeclines() {
        Map<String, String> r = CardChargeRules.classify("4242424242424242", "01", "2020", "123");
        assertEquals(CardChargeRules.DECLINE, r.get("decision"));
        assertEquals("expired_card", r.get("reason"));
    }
}
