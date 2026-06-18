# Code Improvement Proposal

Project: `java-charges`
Date: 2026-06-18

This document lists the improvements I would apply to the codebase, in priority order, with the implementation approach I would use for each item.

## 1. Remove hardcoded secrets

### Problem

`ChargesService` contains a live-looking API key directly in source code.

### Implementation I would use

- Move the secret to an environment variable or secret manager.
- Inject it through Spring configuration properties.
- Fail fast at startup if the value is missing.

### Example

```java
@ConfigurationProperties(prefix = "payments")
public record PaymentsProperties(String stripeApiKey) {}
```

```java
@Service
public class ChargesService {
    private final PaymentsProperties properties;

    public ChargesService(PaymentsProperties properties, ChargeStore store,
                          PaymentProcessor processor, AuditLog audit) {
        this.properties = properties;
        this.store = store;
        this.processor = processor;
        this.audit = audit;
    }
}
```

### Additional action

- Rotate the exposed credential if it is real.
- Remove any traces from git history if necessary.

## 2. Stop logging payment secrets and PII

### Problem

The audit log currently writes `cardToken` and email in clear text.

### Implementation I would use

- Introduce a redaction helper.
- Log only the minimum needed identifiers.
- Replace free-form logging with structured events.

### Example

```java
public class AuditLog {
    public void logCharge(Charge charge, String customerEmail) {
        log.info("audit event=charge_created chargeId={} amount={} currency={} customer={}",
                charge.id(), charge.amount(), charge.currency(), maskEmail(customerEmail));
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
```

## 3. Fix idempotency with atomic persistence

### Problem

The current flow checks idempotency, charges, then saves. That is race-prone.

### Implementation I would use

- Make the idempotency key unique in persistent storage.
- Insert or fetch in a single atomic operation.
- Return the existing record when a duplicate request arrives.

### Example approach

```java
public interface ChargeRepository {
    Optional<Charge> findByIdempotencyKey(String key);
    Charge saveIfAbsent(String key, Charge charge);
}
```

```java
public Charge createCharge(ChargeRequest req) {
    return repository.findByIdempotencyKey(req.idempotencyKey())
            .orElseGet(() -> {
                Charge charge = processor.charge(req);
                return repository.saveIfAbsent(req.idempotencyKey(), charge);
            });
}
```

### Important note

If the persistence layer is still in-memory, this must be protected with synchronization or a concurrent map to avoid duplicate creation under load.

## 4. Replace in-memory storage

### Problem

`ChargeStore` stores charges in `HashMap` and `ArrayList` inside a singleton component.

### Implementation I would use

- Replace the in-memory store with a repository backed by a database.
- Model charge and idempotency key as durable records.
- Add unique constraints.

### Recommended structure

- `ChargeEntity`
- `ChargeRepository`
- `ChargeService`
- `ChargeController`

### Minimum safe interim step

If persistence is not available yet, use:

```java
private final Map<String, Charge> byKey = new ConcurrentHashMap<>();
private final Map<String, Charge> byId = new ConcurrentHashMap<>();
private final List<Charge> all = Collections.synchronizedList(new ArrayList<>());
```

This is not a final solution, only a temporary improvement.

## 5. Add validation at the API boundary

### Problem

The request object has no validation annotations and money is represented with `double`.

### Implementation I would use

- Use `BigDecimal` for amounts.
- Validate input with Bean Validation.
- Reject invalid requests before business logic runs.

### Example

```java
public record ChargeRequest(
        @NotBlank String idempotencyKey,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String currency,
        @NotBlank @Email String customerEmail,
        @NotBlank String cardToken
) {}
```

```java
@PostMapping("/charges")
public ResponseEntity<Charge> createCharge(@Valid @RequestBody ChargeRequest req) {
    Charge charge = service.createCharge(req);
    return ResponseEntity.status(201).body(charge);
}
```

### Additional improvement

- Add a `@ControllerAdvice` to convert validation failures into consistent error responses.

## 6. Protect customer and charge lookup endpoints

### Problem

`GET /charges/{id}` and `GET /customers/search` are public.

### Implementation I would use

- Add authentication.
- Add authorization checks per customer or tenant.
- Scope lookups to the authenticated identity.

### Example direction

```java
@PreAuthorize("hasAuthority('charge:read')")
@GetMapping("/charges/{id}")
public ResponseEntity<Charge> getCharge(@PathVariable String id) {
    ...
}
```

### Additional controls

- Rate limit search endpoints.
- Add audit logs for reads as well as writes.
- Return generic errors for unauthorized access.

## 7. Remove the broken transaction annotation or add the correct dependency

### Problem

The project imports `@Transactional` but does not include the dependency that provides it.

### Implementation I would use

Option A:
- If no real transaction manager exists, remove the annotation.

Option B:
- Add the proper Spring transaction support and use it only where it matters.

### My recommendation

Do not keep a misleading transaction annotation around an in-memory store. Either implement a real persistence layer or remove the annotation entirely.

## 8. Make money handling explicit

### Problem

`double` is used for financial values.

### Implementation I would use

- Replace `double` with `BigDecimal`.
- Store currency separately as ISO-4217 code.
- Validate rounding rules explicitly.

### Example

```java
public record Charge(
        String id,
        BigDecimal amount,
        String currency,
        String customerEmail,
        String status,
        Instant createdAt
) {}
```

## 9. Improve error handling

### Problem

The controller returns only basic status codes without a consistent error payload.

### Implementation I would use

- Add a standard API error model.
- Use exception handlers for validation, not found, and business errors.

### Example

```java
public record ApiError(String code, String message, Instant timestamp) {}
```

```java
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(...) { ... }
}
```

## 10. Add tests that cover the real failure modes

### Problem

Current tests only cover the happy path and one basic bad request.

### Implementation I would use

- Add concurrency tests for idempotency.
- Add validation tests for malformed payloads.
- Add security tests for unauthorized access.
- Add tests that verify no secrets are logged.

### Suggested test cases

- Same idempotency key sent concurrently returns one charge
- Negative amount is rejected
- Missing email is rejected
- Search endpoint requires authorization
- Logs do not contain `cardToken`

## 11. Dependency hygiene

### Problem

The dependency tree should be reviewed regularly for version risk.

### Implementation I would use

- Run dependency scanning in CI.
- Track Spring Boot and Tomcat patch levels.
- Upgrade to the latest safe patch release in the same major line.

### Suggested process

1. Update dependencies only after verifying compatibility.
2. Re-run tests.
3. Re-run CVE review.
4. Promote to staging only after the build is green.

## 12. Recommended target architecture

### Minimum production-safe shape

- Controller layer for request/response handling
- Service layer for business rules
- Repository layer for persistence
- Validation layer for request contract enforcement
- Security layer for authentication and authorization
- Audit layer with redaction

### Flow I would implement

1. Request arrives at controller.
2. Bean Validation rejects malformed payloads.
3. Security layer authenticates and authorizes the caller.
4. Service layer performs atomic idempotency handling.
5. Repository persists the charge and idempotency key.
6. Audit logs a redacted event.
7. Response returns a minimal charge representation.

## Priority Order

1. Remove hardcoded secrets.
2. Stop logging sensitive data.
3. Fix the build.
4. Make idempotency atomic.
5. Replace in-memory state.
6. Add validation and switch to `BigDecimal`.
7. Add authentication and authorization.
8. Add structured error handling.
9. Extend tests to cover concurrency and abuse.
10. Add dependency scanning and regular patching.

