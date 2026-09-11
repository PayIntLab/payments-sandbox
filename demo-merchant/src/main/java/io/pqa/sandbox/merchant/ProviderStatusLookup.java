package io.pqa.sandbox.merchant;

import io.pqa.sandbox.merchant.model.Order;

/**
 * Pull-side lookup used by reconciliation. Returns the provider's current status
 * for an order, or null when the provider cannot be queried this way.
 */
public interface ProviderStatusLookup {
    String providerStatus(Order order);
}
