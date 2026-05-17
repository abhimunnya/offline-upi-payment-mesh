package com.example.UPI.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String vpa) {
        super("Account not found: " + vpa);
    }
}
