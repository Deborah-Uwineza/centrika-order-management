package rw.centrika.orders.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku, int requested, int available) {
        super("Insufficient stock for product " + sku + ": requested " + requested
            + " but only " + available + " available");
    }
}
