package rw.centrika.orders.exception;

import rw.centrika.orders.domain.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition order from status '" + from + "' to '" + to + "'");
    }
}
