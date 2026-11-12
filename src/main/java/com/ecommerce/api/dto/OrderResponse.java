package com.ecommerce.api.dto;

import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long id, OrderStatus status, BigDecimal total, LocalDateTime createdAt,
                            List<OrderItemResponse> items) {
    public static OrderResponse from(Order o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProduct().getId(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(o.getId(), o.getStatus(), o.getTotal(), o.getCreatedAt(), items);
    }
}
