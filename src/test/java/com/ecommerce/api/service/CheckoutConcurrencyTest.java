package com.ecommerce.api.service;

import com.ecommerce.api.dto.CheckoutItem;
import com.ecommerce.api.dto.CheckoutRequest;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.Role;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.exception.ConflictException;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CheckoutConcurrencyTest {
    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;

    @Test
    void concurrentCheckoutsNeverOversell() throws Exception {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("x");
        user.setRole(Role.CUSTOMER);
        userRepository.save(user);

        Product product = new Product();
        product.setName("Limited item");
        product.setCategory("test");
        product.setPrice(new BigDecimal("9.99"));
        product.setStock(5);
        Long productId = productRepository.save(product).getId();

        int buyers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < buyers; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    orderService.checkout(user.getEmail(),
                            new CheckoutRequest(List.of(new CheckoutItem(productId, 1))));
                    succeeded.incrementAndGet();
                } catch (ConflictException expected) {
                    // sold out or too much contention: both are acceptable outcomes
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        int remaining = productRepository.findById(productId).orElseThrow().getStock();
        assertThat(remaining).isGreaterThanOrEqualTo(0);          // never negative
        assertThat(succeeded.get()).isLessThanOrEqualTo(5);       // never sold more than we had
        assertThat(remaining).isEqualTo(5 - succeeded.get());     // every sale is accounted for
    }
}
