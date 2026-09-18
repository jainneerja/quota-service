package com.quotamanager;

import com.quotamanager.dto.ReserveRequest;
import com.quotamanager.entity.Quota;
import com.quotamanager.entity.QuotaId;
import com.quotamanager.repository.QuotaRepository;
import com.quotamanager.service.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Proves the core design guarantee: under concurrent reserve calls, usage
// never exceeds the limit. This is the single most important test in the
// project — it's the difference between "looks right" and "is right".
@Testcontainers
@SpringBootTest
class QuotaConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private QuotaRepository quotaRepository;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void seedQuota() {
        Quota quota = new Quota();
        quota.setId(new QuotaId(orgId, "droplet"));
        quota.setLimitAmount(100);
        quota.setUsedAmount(0);
        quotaRepository.save(quota);
    }

    @Test
    void concurrentReservesNeverExceedTheLimit() throws InterruptedException {
        int threads = 50;
        int amountEach = 5; // 50 * 5 = 250 requested against a limit of 100 -> exactly 20 should succeed
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            int idx = i;
            pool.submit(() -> {
                try {
                    ReserveRequest req = new ReserveRequest();
                    req.setResourceType("droplet");
                    req.setAmount(amountEach);
                    req.setIdempotencyKey("key-" + idx);
                    quotaService.reserve(orgId, req);
                    successes.incrementAndGet();
                } catch (Exception ignored) {
                    // expected for requests that would exceed the limit
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        Quota finalState = quotaRepository.findById(new QuotaId(orgId, "droplet")).orElseThrow();

        assertThat(successes.get()).isEqualTo(20);
        assertThat(finalState.getUsedAmount()).isEqualTo(100);
        assertThat(finalState.getUsedAmount()).isLessThanOrEqualTo(finalState.getLimitAmount());
    }
}
