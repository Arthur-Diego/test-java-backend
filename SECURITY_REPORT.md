# Security Assessment Report

Project: `java-charges`
Date: 2026-06-18

## Executive Summary

The codebase contains multiple security and reliability issues, including a hardcoded secret, sensitive data exposure in logs, unsafe query construction, non-thread-safe shared state, insufficient validation, and an authorization gap on customer and charge lookup endpoints.

The application could not be fully executed in the provided environment because the Maven build fails during compilation due to a missing Spring transaction dependency. This prevents end-to-end runtime validation until the build is corrected.

## Scope

- Full source review of the repository
- Local build and execution attempt
- Dependency and CVE-oriented review of the resolved runtime stack

## Findings

### 1. Hardcoded live secret in source code

- Location: [`src/main/java/com/taller/charges/ChargesService.java`](src/main/java/com/taller/charges/ChargesService.java#L16-L18)
- Issue: A Stripe-style live secret key is embedded directly in the class as a constant.
- Risk: Credential leakage, unauthorized use, forced secret rotation, and exposure through source control history.
- Required fix:
  - Remove the secret from source code.
  - Load secrets from environment variables or a secret manager.
  - Rotate the exposed credential if it is real.

### 2. Sensitive data is written to logs in clear text

- Location: [`src/main/java/com/taller/charges/ChargesService.java`](src/main/java/com/taller/charges/ChargesService.java#L59-L69)
- Issue: The audit log records `cardToken`, `customerEmail`, amount, currency, and timestamp.
- Risk: Payment data and PII exposure in application logs, compliance issues, and lateral leakage into log aggregators.
- Required fix:
  - Remove `cardToken` from all logs.
  - Mask or hash email addresses if needed for traceability.
  - Adopt structured logging with explicit redaction rules.

### 3. Query string is assembled by concatenating user input

- Location: [`src/main/java/com/taller/charges/ChargeStore.java`](src/main/java/com/taller/charges/ChargeStore.java#L34-L37)
- Issue: The email parameter is inserted directly into a SQL-like string.
- Risk: If this pattern reaches a real SQL adapter, it becomes a classic SQL injection vector. It also enables log injection and unsafe query handling patterns.
- Required fix:
  - Use parameterized queries.
  - Never concatenate untrusted input into SQL strings.

### 4. Idempotency is not atomic, allowing duplicate charges

- Location: [`src/main/java/com/taller/charges/ChargesService.java`](src/main/java/com/taller/charges/ChargesService.java#L24-L40)
- Location: [`src/main/java/com/taller/charges/ChargeStore.java`](src/main/java/com/taller/charges/ChargeStore.java#L20-L28)
- Issue: The flow performs `findByKey()` first, then processes the charge, then persists it. There is no atomic lock or transactional uniqueness guarantee.
- Risk: Two concurrent requests with the same idempotency key can both pass the check and create duplicate charges.
- Required fix:
  - Make the idempotency check and persistence atomic.
  - Use a unique constraint or `putIfAbsent`-style synchronization.
  - Move the logic to a transactional persistence layer.

### 5. Shared in-memory store is not thread-safe

- Location: [`src/main/java/com/taller/charges/ChargeStore.java`](src/main/java/com/taller/charges/ChargeStore.java#L16-L18)
- Issue: `HashMap` and `ArrayList` are used as shared mutable state in a singleton component without synchronization.
- Risk: Concurrent requests can corrupt state, lose updates, or trigger inconsistent reads and runtime errors.
- Required fix:
  - Replace with thread-safe data structures or external persistence.
  - Add synchronization or move to a proper data store.

### 6. The project does not compile in the current state

- Location: [`src/main/java/com/taller/charges/ChargesService.java`](src/main/java/com/taller/charges/ChargesService.java#L5, L43-L45)
- Location: [`pom.xml`](pom.xml#L21-L35)
- Issue: `org.springframework.transaction.annotation.Transactional` is imported and used, but the project does not include the dependency that provides it.
- Risk: The application cannot build, deploy, or be validated at runtime until this is resolved.
- Required fix:
  - Add the required Spring transaction dependency if transactions are truly needed.
  - Otherwise remove the annotation and simplify the code.

### 7. Input validation is too weak for a payments endpoint

- Location: [`src/main/java/com/taller/charges/ChargeRequest.java`](src/main/java/com/taller/charges/ChargeRequest.java#L3-L9)
- Location: [`src/main/java/com/taller/charges/ChargesController.java`](src/main/java/com/taller/charges/ChargesController.java#L15-L22)
- Issue:
  - No Bean Validation annotations are applied to request fields.
  - `amount` uses `double`, which is unsuitable for currency.
  - The controller only checks whether `idempotencyKey` is blank.
- Risk: Invalid amounts, malformed emails, invalid currencies, and precision loss in monetary values.
- Required fix:
  - Use `BigDecimal` for monetary values.
  - Add `@Valid`, `@NotBlank`, `@Email`, and `@Positive` where appropriate.
  - Enforce validation at the controller boundary.

### 8. Customer and charge lookup endpoints expose data without access control

- Location: [`src/main/java/com/taller/charges/ChargesController.java`](src/main/java/com/taller/charges/ChargesController.java#L25-L35)
- Issue: `GET /charges/{id}` and `GET /customers/search?email=` are publicly accessible and return sensitive records without authentication or authorization.
- Risk: Charge enumeration, customer data exposure, and unauthorized access to payment history.
- Required fix:
  - Add authentication and authorization.
  - Restrict lookup scope to the authenticated tenant or customer.
  - Add rate limiting and abuse controls.

## Verification Results

### Repository scan

The following patterns were confirmed during source review:

- `sk_live` hardcoded in `ChargesService.java`
- `cardToken` included in audit logs
- SQL string concatenation in `ChargeStore.java`
- `Thread.sleep(250)` in `PaymentProcessor`
- In-memory `HashMap` and `ArrayList` shared across requests

### Build and runtime checks

The environment initially lacked a usable JDK and Maven setup. A portable JDK 17 and Maven distribution were downloaded into the workspace so the project could be verified locally.

#### First build attempt

- Command: `mvn test`
- Result:
  - `The JAVA_HOME environment variable is not defined correctly`
  - `JAVA_HOME should point to a JDK not a JRE`

#### Build attempt with local JDK

- Command: `mvn test`
- Result: Compilation failure
- Exact compiler errors:
  - `package org.springframework.transaction.annotation does not exist`
  - `cannot find symbol class Transactional`

#### Dependency resolution

- Command: `mvn dependency:list -DincludeScope=runtime`
- Result: Successfully resolved the runtime stack, including:
  - `org.springframework.boot:spring-boot-starter-web:3.3.4`
  - `org.springframework.boot:spring-boot-starter-validation:3.3.4`
  - `org.springframework:spring-web:6.1.13`
  - `org.springframework:spring-webmvc:6.1.13`
  - `org.apache.tomcat.embed:tomcat-embed-core:10.1.30`
  - `org.apache.tomcat.embed:tomcat-embed-websocket:10.1.30`
  - `ch.qos.logback:logback-classic:1.5.8`
  - `com.fasterxml.jackson.core:jackson-databind:2.17.2`
  - `org.hibernate.validator:hibernate-validator:8.0.1.Final`

## CVE-Oriented Review

### Confirmed version exposure

The resolved dependency tree shows the application is running on:

- Spring Framework 6.1.13
- Apache Tomcat 10.1.30
- Jackson Databind 2.17.2
- Logback 1.5.8

### Official advisory cross-check

- Spring Framework 6.1.13 is listed as affected by CVE-2024-38820, with the fix available in 6.1.14.
- Spring Framework 6.1.13 is the fixed version for CVE-2024-38816, but that issue applies to functional resource handling patterns that are not present in this repository.
- Apache Tomcat 10.1.30 falls within the affected range for:
  - CVE-2024-52317
  - CVE-2024-52316
- Based on the current codebase, those Tomcat CVEs appear version-relevant but not directly exploitable from the demonstrated application flow unless additional server features are enabled.

### Assessment summary

No directly exploitable CVE was demonstrated from the application code itself during this review. The main issues are application-level weaknesses and dependency exposure that should still be remediated.

## Remediation Priority

1. Remove the hardcoded secret and rotate any exposed credential.
2. Stop logging sensitive payment data.
3. Fix the build by resolving the missing transaction dependency.
4. Make idempotency atomic to eliminate duplicate charges.
5. Replace the in-memory store with a thread-safe and durable persistence layer.
6. Add input validation and switch monetary values to `BigDecimal`.
7. Protect customer and charge lookup endpoints with authentication and authorization.
8. Replace string-concatenated query patterns with parameterized queries.
9. Upgrade dependency versions where applicable and re-run a security scan.

## Conclusion

The repository is not production-safe in its current form. The combination of hardcoded secrets, sensitive logging, weak validation, non-thread-safe shared state, and a broken build path creates both security and operational risk.

The immediate next step should be to remove exposed secrets, fix the compilation issue, and then re-test the application under concurrency with proper validation and access control in place.

## Sources Used

- [ChargesService.java](src/main/java/com/taller/charges/ChargesService.java)
- [ChargesController.java](src/main/java/com/taller/charges/ChargesController.java)
- [ChargeStore.java](src/main/java/com/taller/charges/ChargeStore.java)
- [ChargeRequest.java](src/main/java/com/taller/charges/ChargeRequest.java)
- [pom.xml](pom.xml)
- [Spring CVE-2024-38820](https://spring.io/security/cve-2024-38820)
- [Spring CVE-2024-38816](https://spring.io/security/cve-2024-38816)
- [Apache Tomcat 10 security advisories](https://tomcat.apache.org/security-10.html)
