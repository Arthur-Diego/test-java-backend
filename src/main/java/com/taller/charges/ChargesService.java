package com.taller.charges;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class ChargesService {

    private final ChargeStore store;
    private final PaymentProcessor processor;
    private final AuditLog audit;

    public ChargesService(ChargeStore store, PaymentProcessor processor, AuditLog audit) {
        this.store = store;
        this.processor = processor;
        this.audit = audit;
    }

    public Charge createCharge(ChargeRequest req) {
        Objects.requireNonNull(req, "charge request must not be null");

        ChargeStore.SaveResult result = store.saveIfAbsent(
                req.idempotencyKey(),
                () -> processor.charge(req)
        );

        if (result.created()) {
            audit.logCharge(result.charge(), req.customerEmail());
        }

        return result.charge();
    }
}

@Service
class PaymentProcessor {
    public Charge charge(ChargeRequest req) {
        // Simulate upstream latency in the processor integration.
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String id = "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new Charge(id, req.amount(), req.currency(), req.customerEmail(), "succeeded", Instant.now());
    }
}

@Service
class AuditLog {
    private static final Logger log = Logger.getLogger(AuditLog.class.getName());

    public void logCharge(Charge charge, String customerEmail) {
        log.info(String.format(
                "audit charge=%s amount=%s currency=%s customer=%s at=%s",
                charge.id(), charge.amount(), charge.currency(),
                maskEmail(customerEmail), charge.createdAt()
        ));
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
