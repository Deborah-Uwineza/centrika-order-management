package rw.centrika.orders.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Same rationale as {@link CustomerTierConverter}, applied to order status. */
@Converter(autoApply = true)
public class OrderStatusConverter implements AttributeConverter<OrderStatus, String> {

    @Override
    public String convertToDatabaseColumn(OrderStatus attribute) {
        return attribute == null ? null : attribute.toDbValue();
    }

    @Override
    public OrderStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : OrderStatus.valueOf(dbData.toUpperCase());
    }
}
