package com.commercecore.inventory.service;

import com.commercecore.inventory.model.Inventory;
import com.commercecore.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyTests {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long testProductId = 555L;

    @BeforeEach
    void setup() {
        inventoryRepository.deleteAll();
        Inventory inventory = new Inventory(testProductId, 5, 0, 2);
        inventoryRepository.saveAndFlush(inventory);
    }

    @Test
    @DisplayName("Concurrency: Two concurrent requests attempting to reserve 4 units from initial stock 5 -> only 1 succeeds")
    void testOptimisticLocking_ConcurrentReservation() throws InterruptedException {
        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // ensure both threads start simultaneously
                    transactionTemplate.execute(status -> {
                        Inventory inv = inventoryRepository.findByProductId(testProductId).orElseThrow();
                        inv.reserve(4);
                        inventoryRepository.saveAndFlush(inv);
                        return null;
                    });
                    successCount.incrementAndGet();
                } catch (OptimisticLockingFailureException | IllegalStateException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    if (ex.getCause() instanceof OptimisticLockingFailureException || ex.getCause() instanceof IllegalStateException) {
                        conflictCount.incrementAndGet();
                    }
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // Exactly one should succeed, the other fails due to optimistic lock or insufficient stock
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // Verify final durable state in database is consistent (available >= 0)
        Inventory finalInventory = inventoryRepository.findByProductId(testProductId).orElseThrow();
        assertThat(finalInventory.getAvailableQuantity()).isEqualTo(1);
        assertThat(finalInventory.getReservedQuantity()).isEqualTo(4);
    }
}
