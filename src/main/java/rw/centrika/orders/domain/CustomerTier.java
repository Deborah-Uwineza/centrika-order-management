package rw.centrika.orders.domain;


public enum CustomerTier {
    STANDARD,
    PREMIUM,
    ENTERPRISE;

    public String toDbValue() {
        return name().toLowerCase();
    }
}
