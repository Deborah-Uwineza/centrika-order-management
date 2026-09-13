package rw.centrika.orders.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void pendingCanMoveToProcessingOrCancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PROCESSING)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void happyPathFollowsPendingProcessingShippedDelivered() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PROCESSING)).isTrue();
        assertThat(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void terminalStatusesCannotTransitionAnywhere() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(target)).isFalse();
            assertThat(OrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void cannotSkipDirectlyFromPendingToDelivered() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }
}
