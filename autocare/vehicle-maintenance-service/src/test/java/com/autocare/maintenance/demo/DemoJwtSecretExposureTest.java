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
                "Internal list size should remain unchanged after external mutation");
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
        assertEquals(1, segments.size());
    }

    @Test
    void getJwtSigningKeySegments_addToReturnedList_doesNotGrowInternalList() {
        demo.getJwtSigningKeySegments().add("EXTRA");
        assertEquals(1, demo.getJwtSigningKeySegments().size());
    }

    @Test
    void getJwtSigningKeySegments_removeFromReturnedList_doesNotShrinkInternalList() {
        List<String> copy = demo.getJwtSigningKeySegments();
        copy.remove(0);
        assertEquals(1, demo.getJwtSigningKeySegments().size());
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
                "Internal hints list size should remain unchanged after external mutation");
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
        assertEquals(1, hints.size());
    }

    @Test
    void getPasswordResetHints_addToReturnedList_doesNotGrowInternalList() {
        demo.getPasswordResetHints().add("EXTRA_HINT");
        assertEquals(1, demo.getPasswordResetHints().size());
    }

    @Test
    void getPasswordResetHints_removeFromReturnedList_doesNotShrinkInternalList() {
        List<String> copy = demo.getPasswordResetHints();
        copy.remove(0);
        assertEquals(1, demo.getPasswordResetHints().size());
    }

    // -----------------------------------------------------------------------
    // Cross-field isolation tests
    // -----------------------------------------------------------------------

    @Test
    void jwtSegmentsAndPasswordHints_areIndependentLists() {
        List<String> segments = demo.getJwtSigningKeySegments();
        List<String> hints = demo.getPasswordResetHints();
        assertNotSame(segments, hints);
        assertNotEquals(segments, hints);
    }

    @Test
    void multipleInstances_areIndependent() {
        DemoJwtSecretExposure demo2 = new DemoJwtSecretExposure();
        List<String> segments1 = demo.getJwtSigningKeySegments();
        List<String> segments2 = demo2.getJwtSigningKeySegments();
        assertNotSame(segments1, segments2);
        assertEquals(segments1, segments2);
    }

    @Test
    void constructor_initializesBothLists() {
        assertFalse(demo.getJwtSigningKeySegments().isEmpty(),
                "JWT signing key segments should be initialized in constructor");
        assertFalse(demo.getPasswordResetHints().isEmpty(),
                "Password reset hints should be initialized in constructor");
    }
}