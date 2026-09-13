package rw.centrika.orders.domain;

import java.util.Set;

/** Mirrors the `status` CHECK constraint on the orders table. */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public String toDbValue() {
        return name().toLowerCase();
    }

    // Simple forward-only state machine. Kept intentionally small — a
    // richer workflow engine would be overkill for this exam, but this
    // guards against nonsensical transitions like DELIVERED -> PENDING
    // through the PUT /api/orders/{id}/status endpoint.
    private static final Set<OrderStatus> TERMINAL = Set.of(DELIVERED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(OrderStatus next) {
        if (this.isTerminal()) {
            return false; // no transitions out of a terminal state
        }
        return switch (this) {
            case PENDING -> next == PROCESSING || next == CANCELLED;
            case PROCESSING -> next == SHIPPED || next == CANCELLED;
            case SHIPPED -> next == DELIVERED;
            default -> false;
        };
    }
}
