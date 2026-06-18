package com.taller.charges;

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

        ResponseEntity<String> resp = rest.exchange(
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

        ResponseEntity<String> resp = rest.exchange(
                url("/charges"),
                HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class);

        assertEquals(400, resp.getStatusCode().value());
    }
}
