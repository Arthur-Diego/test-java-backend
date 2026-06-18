package com.taller.charges;

public record Charge(
        String id,
        double amount,
        String currency,
        String customerEmail,
        String status,
        String createdAt
) {}
