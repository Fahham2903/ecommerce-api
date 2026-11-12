package com.ecommerce.api.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
