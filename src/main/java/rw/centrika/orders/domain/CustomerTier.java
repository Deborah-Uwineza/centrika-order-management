package rw.centrika.orders.domain;

/** Mirrors the `tier` CHECK constraint on the customers table. */
public enum CustomerTier {
    STANDARD,
    PREMIUM,
    ENTERPRISE;

    public String toDbValue() {
        return name().toLowerCase();
    }
}
