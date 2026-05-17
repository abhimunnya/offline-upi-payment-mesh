package com.example.UPI.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String vpa, BigDecimal balance, BigDecimal amount) {
        super("Insufficient balance for " + vpa + ": has ₹" + balance + ", tried to send ₹" + amount);
    }
}