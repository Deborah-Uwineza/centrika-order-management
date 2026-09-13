package rw.centrika.orders.domain;

import java.util.Set;


public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public String toDbValue() {
        return name().toLowerCase();
    }

   
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
