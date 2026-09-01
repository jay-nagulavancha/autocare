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

    // -----------------------------------------------------------------------
    // getJwtSigningKeySegments – defensive copy tests
    // -----------------------------------------------------------------------

    @Test
    void getJwtSigningKeySegments_returnsNonNull() {
        assertNotNull(demo.getJwtSigningKeySegments());
    }

    @Test
    void getJwtSigningKeySegments_containsExpectedPlaceholder() {
        List<String> segments = demo.getJwtSigningKeySegments();
        assertTrue(segments.contains("DEMO_ONLY_PLACEHOLDER_JWT_MATERIAL"),
                "Expected placeholder JWT material to be present");
    }

    @Test
    void getJwtSigningKeySegments_returnsDefensiveCopy_mutationDoesNotAffectInternal() {
        List<String> firstCall = demo.getJwtSigningKeySegments();
        firstCall.add("INJECTED_SEGMENT");
        firstCall.clear();

        List<String> secondCall = demo.getJwtSigningKeySegments();
        assertTrue(secondCall.contains("DEMO_ONLY_PLACEHOLDER_JWT_MATERIAL"),
                "Internal list should not be affected by mutation of returned list");
        assertEquals(1, secondCall.size(),
                "Internal list size should remain 1 after external mutation");
    }

    @Test
    void getJwtSigningKeySegments_returnsDifferentInstanceOnEachCall() {
        List<String> first = demo.getJwtSigningKeySegments();
        List<String> second = demo.getJwtSigningKeySegments();
        assertNotSame(first, second,
                "Each call should return a new defensive copy, not the same instance");
    }

    @Test
    void getJwtSigningKeySegments_hasCorrectSize() {
        assertEquals(1, demo.getJwtSigningKeySegments().size());
    }

    @Test
    void getJwtSigningKeySegments_removeFromReturnedList_doesNotAffectInternal() {
        List<String> returned = demo.getJwtSigningKeySegments();
        returned.remove(0);

        List<String> afterRemoval = demo.getJwtSigningKeySegments();
        assertEquals(1, afterRemoval.size(),
                "Removing from returned list should not affect internal state");
    }

    // -----------------------------------------------------------------------
    // getPasswordResetHints – defensive copy tests
    // -----------------------------------------------------------------------

    @Test
    void getPasswordResetHints_returnsNonNull() {
        assertNotNull(demo.getPasswordResetHints());
    }

    @Test
    void getPasswordResetHints_containsExpectedHint() {
        List<String> hints = demo.getPasswordResetHints();
        assertTrue(hints.contains("DEMO_ONLY_HINT"),
                "Expected DEMO_ONLY_HINT to be present in password reset hints");
    }

    @Test
    void getPasswordResetHints_returnsDefensiveCopy_mutationDoesNotAffectInternal() {
        List<String> firstCall = demo.getPasswordResetHints();
        firstCall.add("INJECTED_HINT");
        firstCall.clear();

        List<String> secondCall = demo.getPasswordResetHints();
        assertTrue(secondCall.contains("DEMO_ONLY_HINT"),
                "Internal hints list should not be affected by mutation of returned list");
        assertEquals(1, secondCall.size(),
                "Internal hints list size should remain 1 after external mutation");
    }

    @Test
    void getPasswordResetHints_returnsDifferentInstanceOnEachCall() {
        List<String> first = demo.getPasswordResetHints();
        List<String> second = demo.getPasswordResetHints();
        assertNotSame(first, second,
                "Each call should return a new defensive copy, not the same instance");
    }

    @Test
    void getPasswordResetHints_hasCorrectSize() {
        assertEquals(1, demo.getPasswordResetHints().size());
    }

    @Test
    void getPasswordResetHints_removeFromReturnedList_doesNotAffectInternal() {
        List<String> returned = demo.getPasswordResetHints();
        returned.remove(0);

        List<String> afterRemoval = demo.getPasswordResetHints();
        assertEquals(1, afterRemoval.size(),
                "Removing from returned list should not affect internal state");
    }

    // -----------------------------------------------------------------------
    // Cross-list isolation tests
    // -----------------------------------------------------------------------

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
                "Mutating JWT segments copy should not affect password hints");
    }

    @Test
    void mutatingPasswordHints_doesNotAffectJwtSegments() {
        List<String> hints = demo.getPasswordResetHints();
        hints.clear();

        List<String> segments = demo.getJwtSigningKeySegments();
        assertFalse(segments.isEmpty(),
                "Mutating password hints copy should not affect JWT segments");
    }

    // -----------------------------------------------------------------------
    // Constructor / initialization tests
    // -----------------------------------------------------------------------

    @Test
    void constructor_initializesWithExpectedData() {
        DemoJwtSecretExposure instance = new DemoJwtSecretExposure();
        assertAll(
                () -> assertNotNull(instance.getJwtSigningKeySegments()),
                () -> assertNotNull(instance.getPasswordResetHints()),
                () -> assertFalse(instance.getJwtSigningKeySegments().isEmpty()),
                () -> assertFalse(instance.getPasswordResetHints().isEmpty())
        );
    }

    @Test
    void multipleInstances_areIndependent() {
        DemoJwtSecretExposure instance1 = new DemoJwtSecretExposure();
        DemoJwtSecretExposure instance2 = new DemoJwtSecretExposure();

        List<String> segments1 = instance1.getJwtSigningKeySegments();
        segments1.add("EXTRA");

        List<String> segments2 = instance2.getJwtSigningKeySegments();
        assertEquals(1, segments2.size(),
                "Mutating returned list from instance1 should not affect instance2");
    }
}