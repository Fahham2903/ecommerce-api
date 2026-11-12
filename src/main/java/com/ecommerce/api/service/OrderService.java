package com.ecommerce.api.service;

import com.ecommerce.api.dto.CheckoutRequest;
import com.ecommerce.api.dto.CheckoutItem;
import com.ecommerce.api.dto.OrderResponse;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.OrderStatus;
import com.ecommerce.api.exception.ConflictException;
import com.ecommerce.api.exception.NotFoundException;
import com.ecommerce.api.repository.OrderRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private static final int MAX_ATTEMPTS = 5;

    private final OrderPlacementService placementService;
    private final OrderRepository orderRepository;
    private final CacheManager cacheManager;

    public OrderService(OrderPlacementService placementService, OrderRepository orderRepository,
                        CacheManager cacheManager) {
        this.placementService = placementService;
        this.orderRepository = orderRepository;
        this.cacheManager = cacheManager;
    }

    /**
     * Checkout with optimistic-lock retry: if another buyer changed the same product row first,
     * the transaction rolls back and is retried against fresh stock. Overselling is impossible;
     * after MAX_ATTEMPTS the client gets a 409 and can retry.
     */
    public OrderResponse checkout(String email, CheckoutRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                OrderResponse response = placementService.place(email, request.items());
                evictProducts(request);
                return response;
            } catch (ConcurrencyFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new ConflictException("High demand on these items, please retry");
                }
                backoff(attempt);
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listMine(String email, Pageable pageable) {
        return orderRepository.findByUserEmail(email, pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id, String email, boolean admin) {
        Order order = orderRepository.findById(id).orElseThrow(() -> notFound(id));
        if (!admin && !order.getUser().getEmail().equals(email)) throw notFound(id);  // don't leak existence
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus status) {
        Order order = orderRepository.findById(id).orElseThrow(() -> notFound(id));
        order.setStatus(status);
        return OrderResponse.from(order);
    }

    private void evictProducts(CheckoutRequest request) {
        Cache cache = cacheManager.getCache("products");
        if (cache == null) return;
        for (CheckoutItem item : request.items()) cache.evict(item.productId());
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(10L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ConflictException("Checkout interrupted");
        }
    }

    private NotFoundException notFound(Long id) { return new NotFoundException("Order " + id + " not found"); }
}
