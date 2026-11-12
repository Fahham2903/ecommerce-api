package com.ecommerce.api.service;

import com.ecommerce.api.dto.CheckoutItem;
import com.ecommerce.api.dto.OrderResponse;
import com.ecommerce.api.entity.*;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.exception.NotFoundException;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One checkout = one transaction: stock deduction and order creation commit together or not at all.
 * Kept in its own bean so OrderService can retry it through the transactional proxy.
 */
@Service
public class OrderPlacementService {
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrderPlacementService(ProductRepository productRepository, OrderRepository orderRepository,
                                 UserRepository userRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrderResponse place(String email, List<CheckoutItem> items) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));

        // merge duplicate lines; sorted by id so concurrent checkouts touch rows in the same order
        Map<Long, Integer> wanted = new TreeMap<>();
        for (CheckoutItem item : items) wanted.merge(item.productId(), item.quantity(), Integer::sum);

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> line : wanted.entrySet()) {
            Product product = productRepository.findById(line.getKey())
                    .orElseThrow(() -> new NotFoundException("Product " + line.getKey() + " not found"));
            int qty = line.getValue();
            if (product.getStock() < qty) {
                throw new InsufficientStockException("Insufficient stock for '" + product.getName()
                        + "' (available: " + product.getStock() + ")");
            }
            product.setStock(product.getStock() - qty);   // version check happens when this row is flushed

            OrderItem oi = new OrderItem();
            oi.setProduct(product);
            oi.setQuantity(qty);
            oi.setUnitPrice(product.getPrice());
            order.addItem(oi);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(qty)));
        }
        order.setTotal(total);
        return OrderResponse.from(orderRepository.saveAndFlush(order));
    }
}
