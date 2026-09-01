package com.autocare.maintenance.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * DEMO_ONLY — SpotBugs {@code EI_EXPOSE_REP}: getters return internal mutable lists that may hold
 * JWT signing segments and password-reset hints. Deterministic remediation replaces getters with
 * defensive copies. Safe to delete this entire class after demos.
 */
public class DemoJwtSecretExposure {

    /** In-memory stand-in for JWT HS256 key material split across segments (not a real secret). */
    private final List<String> jwtSigningKeySegments = new ArrayList<>();

    /** DEMO_ONLY labels tied to password-reset workflow (still mutable list exposure). */
    private final List<String> passwordResetHints = new ArrayList<>();

    public DemoJwtSecretExposure() {
        jwtSigningKeySegments.add("DEMO_ONLY_PLACEHOLDER_JWT_MATERIAL");
        passwordResetHints.add("DEMO_ONLY_HINT");
    }

    /**
     * DEMO_ONLY — Returns internal {@code List}; SpotBugs reports EI_EXPOSE_REP (caller can mutate JWT
     * key material).
     */
    public List<String> getJwtSigningKeySegments() { return jwtSigningKeySegments == null ? null : new ArrayList<>(jwtSigningKeySegments); }

    /**
     * DEMO_ONLY — Same exposure pattern for password-adjacent cached hints.
     */
    public List<String> getPasswordResetHints() { return passwordResetHints == null ? null : new ArrayList<>(passwordResetHints); }
}
