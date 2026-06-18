package com.taller.charges;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Happy-path smoke tests for the charges service.
 *
 * These tests currently pass. That doesn't mean the service is correct.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChargesControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private TestRestTemplate secureRest;

    @BeforeEach
    void setUp() {
        secureRest = rest.withBasicAuth("support", "support-password");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void createChargeReturns201ForAFreshKey() {
        String json = "{"
                + "\"idempotencyKey\":\"test_fresh_key\","
                + "\"amount\":12.50,"
                + "\"currency\":\"USD\","
                + "\"customerEmail\":\"happy@example.com\","
                + "\"cardToken\":\"tok_visa\""
                + "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = secureRest.exchange(
                url("/charges"),
                HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class);

        assertEquals(201, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("\"id\":\"ch_"));
    }

    @Test
    void missingIdempotencyKeyReturns400() {
        String json = "{"
                + "\"idempotencyKey\":\"\","
                + "\"amount\":1.00,"
                + "\"currency\":\"USD\","
                + "\"customerEmail\":\"x@y.com\","
                + "\"cardToken\":\"tok_visa\""
                + "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = secureRest.exchange(
                url("/charges"),
                HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class);

        assertEquals(400, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("validation_error"));
    }

    @Test
    void createChargeRequiresAuthentication() {
        String json = "{"
                + "\"idempotencyKey\":\"auth_required_key\","
                + "\"amount\":12.50,"
                + "\"currency\":\"USD\","
                + "\"customerEmail\":\"noauth@example.com\","
                + "\"cardToken\":\"tok_visa\""
                + "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = rest.exchange(
                url("/charges"),
                HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class);

        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    void duplicateIdempotencyKeyReturnsSameCharge() {
        String json = "{"
                + "\"idempotencyKey\":\"dup_key\","
                + "\"amount\":19.99,"
                + "\"currency\":\"USD\","
                + "\"customerEmail\":\"dup@example.com\","
                + "\"cardToken\":\"tok_visa\""
                + "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> first = secureRest.exchange(
                url("/charges"),
                HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class);
        ResponseEntity<String> second = secureRest.exchange(
                url("/charges"),
                HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class);

        assertEquals(201, first.getStatusCode().value());
        assertEquals(201, second.getStatusCode().value());
        assertNotNull(first.getBody());
        assertEquals(first.getBody(), second.getBody());
    }

    @Test
    void invalidAmountReturns400() {
        String json = "{"
                + "\"idempotencyKey\":\"bad_amount_key\","
                + "\"amount\":0,"
                + "\"currency\":\"USD\","
                + "\"customerEmail\":\"bad@example.com\","
                + "\"cardToken\":\"tok_visa\""
                + "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = secureRest.exchange(
                url("/charges"),
                HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class);

        assertEquals(400, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("validation_error"));
    }
}
