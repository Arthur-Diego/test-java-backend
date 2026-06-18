# Comparative Security Report

Project: `java-charges`
Branch: `security-hardening`
Date: 2026-06-18

## Purpose

This report compares the original codebase with the hardened version implemented in this branch and summarizes all corrections that were applied.

## Baseline Before Fixes

The original repository had the following high-risk issues:

1. A live-looking API secret was hardcoded in source.
2. Payment tokens and customer emails were logged in plain text.
3. Search logic assembled a SQL string by concatenating user input.
4. Idempotency was not atomic, which allowed duplicate charges under concurrency.
5. The in-memory store used non-thread-safe collections.
6. The project did not compile because `@Transactional` was imported without the required dependency.
7. Request payloads lacked robust validation, and money was represented with `double`.
8. Charge and customer lookup endpoints were exposed without authentication or authorization.
9. The runtime stack was on older Spring and Tomcat patch levels.

## Corrections Applied

### 1. Hardcoded secret removed

- Before: A secret key was embedded directly in `ChargesService`.
- After: The secret constant was removed entirely.
- Result: No credential remains in source code.

### 2. Sensitive logging redacted

- Before: `AuditLog` logged `cardToken` and full customer email.
- After: Logging now redacts the customer email and no longer logs the card token.
- Result: Payment-sensitive data is no longer written to logs.

### 3. Unsafe query construction removed

- Before: `ChargeStore.findByEmail()` created a SQL string via concatenation.
- After: The method now filters the in-memory collection directly and does not build SQL text.
- Result: The injection-prone pattern was removed.

### 4. Idempotency made atomic

- Before: The service checked for an existing charge, then processed, then saved.
- After: The store provides a synchronized `saveIfAbsent` operation that performs the check and insert atomically.
- Result: Duplicate charge creation from concurrent requests is prevented in the current in-memory implementation.

### 5. Thread safety improved

- Before: Shared state used `HashMap` and `ArrayList` without synchronization.
- After: The store now uses `ConcurrentHashMap` and a synchronized atomic write path.
- Result: Concurrent access is materially safer and no longer depends on unsafe mutable collections.

### 6. Build fixed

- Before: `@Transactional` caused compilation failure because the supporting dependency was missing.
- After: The unsupported annotation was removed.
- Result: The project now compiles and the test suite runs successfully.

### 7. Validation and money handling strengthened

- Before: Request fields had no Bean Validation annotations and monetary values used `double`.
- After:
  - `idempotencyKey` is required and size-limited.
  - `amount` uses `BigDecimal` and must be at least `0.01`.
  - `currency` must be a three-letter uppercase code.
  - `customerEmail` is validated as an email address.
  - `cardToken` is required and size-limited.
- Result: The API rejects malformed input earlier and handles currency safely.

### 8. Security added to API endpoints

- Before: Endpoints were publicly reachable.
- After: Spring Security HTTP Basic authentication was added, with method-level role checks for the charge and search endpoints.
- Result: The application now requires authentication and enforces authorization.

### 9. Validation errors return structured responses

- Before: Validation failures were handled ad hoc.
- After: A global REST exception handler returns a consistent `ApiError` payload.
- Result: Client failures are easier to diagnose and safer to consume.

### 10. Dependency stack upgraded

- Before:
  - Spring Boot 3.3.4
  - Spring Framework 6.1.13
  - Tomcat 10.1.30
- After:
  - Spring Boot 3.5.15
  - Spring Framework 6.2.19
  - Tomcat 10.1.55
- Result: The runtime stack now tracks newer patch levels and reduces exposure to previously identified advisories.

## Validation Results

### Build and tests

- Command: `mvn test`
- Result: `BUILD SUCCESS`
- Test summary:
  - Tests run: 5
  - Failures: 0
  - Errors: 0
  - Skipped: 0

### Runtime behavior confirmed

- Authentication is required for charge creation.
- Validation failures return HTTP 400 with a structured error body.
- Duplicate idempotency keys return the same charge response.
- Audit logs no longer expose the card token.
- Customer email values are redacted in audit logs.

### Dependency resolution confirmed

Resolved runtime stack now includes:

- `org.springframework.boot:spring-boot-starter-web:3.5.15`
- `org.springframework.boot:spring-boot-starter-validation:3.5.15`
- `org.springframework.boot:spring-boot-starter-security:3.5.15`
- `org.springframework:spring-web:6.2.19`
- `org.springframework:spring-webmvc:6.2.19`
- `org.apache.tomcat.embed:tomcat-embed-core:10.1.55`
- `org.apache.tomcat.embed:tomcat-embed-websocket:10.1.55`
- `ch.qos.logback:logback-classic:1.5.34`
- `com.fasterxml.jackson.core:jackson-databind:2.21.4`
- `org.hibernate.validator:hibernate-validator:8.0.3.Final`

## Residual Notes

- The processor latency simulation still uses `Thread.sleep(250)` to emulate upstream behavior.
- The current persistence layer is still in-memory and should be replaced with a durable database-backed repository for production use.

## Conclusion

The branch addresses the critical issues identified in the initial reports and brings the project to a compilable, testable, and substantially safer state.

For production readiness, the next required step is replacing the in-memory store with durable persistence and adding tighter tenant or customer scoping where applicable.

