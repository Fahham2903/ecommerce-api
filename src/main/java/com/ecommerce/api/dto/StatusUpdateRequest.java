package com.ecommerce.api.dto;

import com.ecommerce.api.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull OrderStatus status) {}
