package com.taller.charges;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
public class ChargesController {

    private final ChargesService service;
    private final ChargeStore store;

    public ChargesController(ChargesService service, ChargeStore store) {
        this.service = service;
        this.store = store;
    }

    @PostMapping("/charges")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('USER')")
    public ResponseEntity<Charge> createCharge(@RequestBody @Valid ChargeRequest req) {
        Charge charge = service.createCharge(req);
        return ResponseEntity.status(201).body(charge);
    }

    @GetMapping("/charges/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('USER')")
    public ResponseEntity<Charge> getCharge(@PathVariable String id) {
        Charge c = store.findById(id);
        return c == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(c);
    }

    @GetMapping("/customers/search")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('USER')")
    public List<Charge> searchCustomers(@RequestParam @NotBlank @Email String email) {
        return store.findByEmail(email);
    }
}
