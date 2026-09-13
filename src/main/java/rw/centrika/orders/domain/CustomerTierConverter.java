package rw.centrika.orders.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts between the Java enum (UPPERCASE, idiomatic) and the DB
 * representation (lowercase, matching the CHECK constraint in schema.sql).
 * Keeping this explicit avoids surprises from Hibernate's default
 * EnumType.STRING, which would store "STANDARD" and violate the constraint.
 */
@Converter(autoApply = true)
public class CustomerTierConverter implements AttributeConverter<CustomerTier, String> {

    @Override
    public String convertToDatabaseColumn(CustomerTier attribute) {
        return attribute == null ? null : attribute.toDbValue();
    }

    @Override
    public CustomerTier convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CustomerTier.valueOf(dbData.toUpperCase());
    }
}
