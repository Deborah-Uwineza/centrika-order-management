package rw.centrika.orders.domain;

import jakarta.persistence.*;
import lombok.*;
import rw.centrika.orders.exception.InsufficientStockException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * The whole point of pessimistic locking (see OrderService) is that
     * this mutation only ever happens while we hold a row lock on this
     * product. Never call this outside a transaction that acquired one.
     */
    public void deductStock(int quantity) {
        if (quantity > this.stockQuantity) {
            throw new InsufficientStockException(this.sku, quantity, this.stockQuantity);
        }
        this.stockQuantity -= quantity;
    }
}
