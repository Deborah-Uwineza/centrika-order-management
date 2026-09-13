package rw.centrika.orders.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;


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
