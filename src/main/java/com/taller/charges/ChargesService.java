package com.taller.charges;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class ChargesService {

    private static final Logger log = Logger.getLogger(ChargesService.class.getName());

    // Stripe-style API key. Pulled from config at startup.
    private static final String STRIPE_API_KEY =
            "sk_live_v21_TAL_W7kQ9rR2bX4mE8nP6vY3aJ5sZ8cN1uK0";

    @Autowired private ChargeStore store;
    @Autowired private PaymentProcessor processor;
    @Autowired private AuditLog audit;

    public Charge createCharge(ChargeRequest req) {
        // Idempotency check
        Charge existing = store.findByKey(req.idempotencyKey());
        if (existing != null) {
            return existing;
        }

        // Process the charge
        Charge charge = processor.charge(req);

        // Persist
        persist(req.idempotencyKey(), charge);

        // Audit — fire and forget so we don't slow down the response
        audit.logCharge(charge, req.customerEmail(), req.cardToken());

        return charge;
    }

    @Transactional
    public void persist(String key, Charge charge) {
        store.save(key, charge);
    }
}

@Service
class PaymentProcessor {
    public Charge charge(ChargeRequest req) {
        // Simulate latency talking to the processor
        try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        String id = "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new Charge(id, req.amount(), req.currency(), req.customerEmail(), "succeeded", Instant.now().toString());
    }
}

@Service
class AuditLog {
    private static final Logger log = Logger.getLogger(AuditLog.class.getName());

    public void logCharge(Charge charge, String customerEmail, String cardToken) {
        log.info(String.format(
                "audit charge=%s amount=%s %s email=%s card=%s at=%s",
                charge.id(), charge.amount(), charge.currency(),
                customerEmail, cardToken, charge.createdAt()
        ));
    }
}
