package com.ecommerce.api.exception;

public class InsufficientStockException extends ConflictException {
    public InsufficientStockException(String message) { super(message); }
}
