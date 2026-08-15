package com.commercecore.order.service;

import com.commercecore.order.dto.CreateOrderRequest;
import com.commercecore.order.dto.OrderItemRequest;
import com.commercecore.order.model.Order;
import com.commercecore.order.repository.OrderRepository;
import com.commercecore.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DirtiesContext
class OrderPersistenceIntegrationTests {

    @Autowired
    private OrderPersistenceService orderPersistenceService;

    @Autowired
    private OrderRepository orderRepository;

    @SpyBean
    private OutboxEventRepository outboxRepository;

    private CreateOrderRequest sampleRequest;

    @BeforeEach
    void setUp() {
        reset(outboxRepository);
        orderRepository.deleteAll();
        outboxRepository.deleteAll();

        sampleRequest = new CreateOrderRequest(
                List.of(
                        new OrderItemRequest(501L, "Mechanical Switch Set", 2, new BigDecimal("29.99")),
                        new OrderItemRequest(502L, "Coiled Cable", 1, new BigDecimal("19.99"))
                ),
                "USD"
        );
    }

    @Test
    @DisplayName("Transactional Boundary Verification: Successfully persists Order, OrderItems and OutboxEvent in single transaction")
    void testPersistOrderAndOutbox_Success() {
        Order savedOrder = orderPersistenceService.persistOrderAndOutbox(100L, sampleRequest, "corr-tx-success");

        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getId()).isNotNull();

        // Verify Order is committed
        assertThat(orderRepository.findById(savedOrder.getId())).isPresent();
        assertThat(orderRepository.findById(savedOrder.getId()).get().getItems()).hasSize(2);

        // Verify OutboxEvent is committed
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Transactional Boundary Verification: Rollback Order and OrderItems when OutboxEvent persistence fails")
    void testPersistOrderAndOutbox_RollsBackOnOutboxFailure() {
        // Simulate outbox repository persistence failure
        doThrow(new RuntimeException("Simulated Database Error during Outbox persistence"))
                .when(outboxRepository).save(any());

        assertThatThrownBy(() -> orderPersistenceService.persistOrderAndOutbox(100L, sampleRequest, "corr-tx-fail"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create outbox event");

        // Verify that entire transaction was rolled back: ZERO orders and ZERO order items exist in DB
        assertThat(orderRepository.count()).isEqualTo(0);
        assertThat(outboxRepository.count()).isEqualTo(0);
    }
}
