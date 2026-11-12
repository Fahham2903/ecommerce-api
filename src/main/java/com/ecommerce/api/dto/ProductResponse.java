package com.ecommerce.api.dto;

import com.ecommerce.api.entity.Product;
import java.io.Serializable;
import java.math.BigDecimal;

/** Serializable because Redis cache entries use JDK serialization by default. */
public record ProductResponse(Long id, String name, String description, String category,
                              BigDecimal price, int stock) implements Serializable {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getCategory(), p.getPrice(), p.getStock());
    }
}
