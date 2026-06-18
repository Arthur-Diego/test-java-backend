package com.taller.charges;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class ChargeStore {

    private final Map<String, Charge> byKey = new ConcurrentHashMap<>();
    private final Map<String, Charge> byId = new ConcurrentHashMap<>();

    public Charge findByKey(String key) {
        return byKey.get(key);
    }

    public synchronized SaveResult saveIfAbsent(String key, Supplier<Charge> factory) {
        Charge existing = byKey.get(key);
        if (existing != null) {
            return new SaveResult(existing, false);
        }

        Charge charge = factory.get();
        byKey.put(key, charge);
        byId.put(charge.id(), charge);
        return new SaveResult(charge, true);
    }

    public Charge findById(String id) {
        return byId.get(id);
    }

    public List<Charge> findByEmail(String email) {
        return byId.values().stream()
                .filter(c -> c.customerEmail().equals(email))
                .toList();
    }

    public record SaveResult(Charge charge, boolean created) {}
}
