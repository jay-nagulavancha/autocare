package com.autocare.maintenance.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemoJwtSecretExposureTest {

    private DemoJwtSecretExposure demo;

    @BeforeEach
    void setUp() {
        demo = new DemoJwtSecretExposure();
    }

    // --- getJwtSigningKeySegments() defensive copy tests ---

    @Test
    void getJwtSigningKeySegments_returnsNonNull() {
        assertNotNull(demo.getJwtSigningKeySegments());
    }

    @Test
    void getJwtSigningKeySegments_containsExpectedDemoPlaceholder() {
        List<String> segments = demo.getJwtSigningKeySegments();
        assertTrue(segments.contains("DEMO_ONLY_PLACEHOLDER_JWT_MATERIAL"),
                "Expected DEMO_ONLY_PLACEHOLDER_JWT_MATERIAL in jwt signing key segments");
    }

    @Test
    void getJwtSigningKeySegments_returnsDefensiveCopy_mutationDoesNotAffectInternal() {
        List<String> firstCall = demo.getJwtSigningKeySegments();
        firstCall.add("INJECTED_SEGMENT");
        firstCall.clear();

        List<String> secondCall = demo.getJwtSigningKeySegments();
        assertTrue(secondCall.contains("DEMO_ONLY_PLACEHOLDER_JWT_MATERIAL"),
                "Internal list should not be affected by mutation of returned list");
    }

    @Test
    void getJwtSigningKeySegments_returnsDifferentInstanceOnEachCall() {
        List<String> first = demo.getJwtSigningKeySegments();
        List<String> second = demo.getJwtSigningKeySegments();
        assertNotSame(first, second,
                "Each call should return a new defensive copy, not the same instance");
    }

    @Test
    void getJwtSigningKeySegments_returnedListHasCorrectSize() {
        List<String> segments = demo.getJwtSigningKeySegments();
        assertEquals(1, segments.size(),
                "Expected exactly one segment in the default constructor");
    }

    @Test
    void getJwtSigningKeySegments_addingToReturnedListDoesNotGrowInternalList() {
        List<String> returned = demo.getJwtSigningKeySegments();
        returned.add("EXTRA");

        List<String> fresh = demo.getJwtSigningKeySegments();
        assertEquals(1, fresh.size(),
                "Internal list size should remain 1 after mutating the returned copy");
    }

    @Test
    void getJwtSigningKeySegments_removingFromReturnedListDoesNotShrinkInternalList() {
        List<String> returned = demo.getJwtSigningKeySegments();
        returned.remove(0);

        List<String> fresh = demo.getJwtSigningKeySegments();
        assertEquals(1, fresh.size(),
                "Internal list size should remain 1 after removing from the returned copy");
    }

    // --- getPasswordResetHints() defensive copy tests ---

    @Test
    void getPasswordResetHints_returnsNonNull() {
        assertNotNull(demo.getPasswordResetHints());
    }

    @Test
    void getPasswordResetHints_containsExpectedDemoHint() {
        List<String> hints = demo.getPasswordResetHints();
        assertTrue(hints.contains("DEMO_ONLY_HINT"),
                "Expected DEMO_ONLY_HINT in password reset hints");
    }

    @Test
    void getPasswordResetHints_returnsDefensiveCopy_mutationDoesNotAffectInternal() {
        List<String> firstCall = demo.getPasswordResetHints();
        firstCall.add("INJECTED_HINT");
        firstCall.clear();

        List<String> secondCall = demo.getPasswordResetHints();
        assertTrue(secondCall.contains("DEMO_ONLY_HINT"),
                "Internal list should not be affected by mutation of returned list");
    }

    @Test
    void getPasswordResetHints_returnsDifferentInstanceOnEachCall() {
        List<String> first = demo.getPasswordResetHints();
        List<String> second = demo.getPasswordResetHints();
        assertNotSame(first, second,
                "Each call should return a new defensive copy, not the same instance");
    }

    @Test
    void getPasswordResetHints_returnedListHasCorrectSize() {
        List<String> hints = demo.getPasswordResetHints();
        assertEquals(1, hints.size(),
                "Expected exactly one hint in the default constructor");
    }

    @Test
    void getPasswordResetHints_addingToReturnedListDoesNotGrowInternalList() {
        List<String> returned = demo.getPasswordResetHints();
        returned.add("EXTRA_HINT");

        List<String> fresh = demo.getPasswordResetHints();
        assertEquals(1, fresh.size(),
                "Internal list size should remain 1 after mutating the returned copy");
    }

    @Test
    void getPasswordResetHints_removingFromReturnedListDoesNotShrinkInternalList() {
        List<String> returned = demo.getPasswordResetHints();
        returned.remove(0);

        List<String> fresh = demo.getPasswordResetHints();
        assertEquals(1, fresh.size(),
                "Internal list size should remain 1 after removing from the returned copy");
    }

    // --- Cross-field isolation tests ---

    @Test
    void jwtSegmentsAndPasswordHints_areIndependentLists() {
        List<String> segments = demo.getJwtSigningKeySegments();
        List<String> hints = demo.getPasswordResetHints();
        assertNotSame(segments, hints,
                "JWT segments and password hints should be separate list instances");
    }

    @Test
    void mutatingJwtSegments_doesNotAffectPasswordHints() {
        List<String> segments = demo.getJwtSigningKeySegments();
        segments.clear();

        List<String> hints = demo.getPasswordResetHints();
        assertFalse(hints.isEmpty(),
                "Mutating JWT segments copy should not affect password reset hints");
    }

    @Test
    void mutatingPasswordHints_doesNotAffectJwtSegments() {
        List<String> hints = demo.getPasswordResetHints();
        hints.clear();

        List<String> segments = demo.getJwtSigningKeySegments();
        assertFalse(segments.isEmpty(),
                "Mutating password hints copy should not affect JWT signing key segments");
    }

    // --- Multiple instance isolation ---

    @Test
    void twoInstances_haveIndependentInternalState() {
        DemoJwtSecretExposure instance1 = new DemoJwtSecretExposure();
        DemoJwtSecretExposure instance2 = new DemoJwtSecretExposure();

        List<String> segments1 = instance1.getJwtSigningKeySegments();
        segments1.add("EXTRA");

        List<String> segments2 = instance2.getJwtSigningKeySegments();
        assertEquals(1, segments2.size(),
                "Mutating returned list from instance1 should not affect instance2");
    }

    @Test
    void constructor_initializesJwtSegmentsWithExactlyOneEntry() {
        DemoJwtSecretExposure fresh = new DemoJwtSecretExposure();
        assertEquals(1, fresh.getJwtSigningKeySegments().size());
    }

    @Test
    void constructor_initializesPasswordHintsWithExactlyOneEntry() {
        DemoJwtSecretExposure fresh = new DemoJwtSecretExposure();
        assertEquals(1, fresh.getPasswordResetHints().size());
    }
}