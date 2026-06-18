package com.taller.charges;

import java.math.BigDecimal;
import java.time.Instant;

public record Charge(
        String id,
        BigDecimal amount,
        String currency,
        String customerEmail,
        String status,
        Instant createdAt
) {}
