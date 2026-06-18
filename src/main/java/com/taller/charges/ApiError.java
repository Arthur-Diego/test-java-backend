package com.taller.charges;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        List<String> details,
        Instant timestamp
) {}
