package com.autocare.maintenance.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WorkOrderStatusHistoryTest {

    private WorkOrderStatusHistory history;

    @BeforeEach
    void setUp() {
        history = new WorkOrderStatusHistory();
    }

    // -------------------------------------------------------------------------
    // id
    // -------------------------------------------------------------------------

    @Test
    void testSetAndGetId() {
        history.setId(42L);
        assertEquals(42L, history.getId());
    }

    @Test
    void testIdDefaultsToNull() {
        assertNull(history.getId());
    }

    // -------------------------------------------------------------------------
    // workOrder – EI_EXPOSE_REP / EI_EXPOSE_REP2 remediation
    // -------------------------------------------------------------------------

    @Test
    void testSetWorkOrderStoresReference() {
        WorkOrder wo = new WorkOrder();
        history.setWorkOrder(wo);
        assertNotNull(history.getWorkOrder());
    }

    @Test
    void testSetWorkOrderNullThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> history.setWorkOrder(null));
    }

    @Test
    void testGetWorkOrderReturnsStoredReference() {
        WorkOrder wo = new WorkOrder();
        history.setWorkOrder(wo);
        assertSame(wo, history.getWorkOrder());
    }

    @Test
    void testWorkOrderDefaultsToNull() {
        assertNull(history.getWorkOrder());
    }

    // -------------------------------------------------------------------------
    // previousStatus
    // -------------------------------------------------------------------------

    @Test
    void testSetAndGetPreviousStatus() {
        history.setPreviousStatus(WorkOrderStatus.OPEN);
        assertEquals(WorkOrderStatus.OPEN, history.getPreviousStatus());
    }

    @Test
    void testPreviousStatusCanBeNull() {
        history.setPreviousStatus(null);
        assertNull(history.getPreviousStatus());
    }

    // -------------------------------------------------------------------------
    // newStatus
    // -------------------------------------------------------------------------

    @Test
    void testSetAndGetNewStatus() {
        history.setNewStatus(WorkOrderStatus.IN_PROGRESS);
        assertEquals(WorkOrderStatus.IN_PROGRESS, history.getNewStatus());
    }

    @Test
    void testNewStatusDefaultsToNull() {
        assertNull(history.getNewStatus());
    }

    // -------------------------------------------------------------------------
    // changedBy
    // -------------------------------------------------------------------------

    @Test
    void testSetAndGetChangedBy() {
        history.setChangedBy("admin");
        assertEquals("admin", history.getChangedBy());
    }

    @Test
    void testChangedByDefaultsToNull() {
        assertNull(history.getChangedBy());
    }

    @Test
    void testChangedByCanBeNull() {
        history.setChangedBy(null);
        assertNull(history.getChangedBy());
    }

    // -------------------------------------------------------------------------
    // changedAt – defensive copy behaviour (EI_EXPOSE_REP / EI_EXPOSE_REP2)
    // -------------------------------------------------------------------------

    @Test
    void testSetAndGetChangedAt() {
        LocalDateTime now = LocalDateTime.of(2024, 6, 15, 10, 30, 0);
        history.setChangedAt(now);
        assertEquals(now, history.getChangedAt());
    }

    @Test
    void testChangedAtDefaultsToNull() {
        assertNull(history.getChangedAt());
    }

    @Test
    void testMutatingOriginalLocalDateTimeDoesNotAffectStoredValue() {
        // LocalDateTime is immutable, so any reassignment of the local variable
        // must not change the stored value – this validates the immutability contract.
        LocalDateTime original = LocalDateTime.of(2024, 1, 1, 0, 0);
        history.setChangedAt(original);

        // Reassign the local reference (simulates caller trying to mutate)
        LocalDateTime modified = original.plusDays(10);

        // The stored value must remain unchanged
        assertEquals(original, history.getChangedAt());
        assertNotEquals(modified, history.getChangedAt());
    }

    @Test
    void testGetChangedAtReturnedValueDoesNotMutateInternalState() {
        LocalDateTime stored = LocalDateTime.of(2024, 3, 20, 8, 0);
        history.setChangedAt(stored);

        LocalDateTime retrieved = history.getChangedAt();
        // LocalDateTime is immutable; calling plus* creates a new instance
        LocalDateTime mutated = retrieved.plusYears(1);

        // Internal state must be unaffected
        assertEquals(stored, history.getChangedAt());
        assertNotEquals(mutated, history.getChangedAt());
    }

    // -------------------------------------------------------------------------
    // onCreate – @PrePersist lifecycle callback
    // -------------------------------------------------------------------------

    @Test
    void testOnCreateSetsChangedAt() {
        assertNull(history.getChangedAt());
        history.onCreate();
        assertNotNull(history.getChangedAt());
    }

    @Test
    void testOnCreateSetsChangedAtToApproximatelyNow() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        history.onCreate();
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertTrue(history.getChangedAt().isAfter(before));
        assertTrue(history.getChangedAt().isBefore(after));
    }

    @Test
    void testOnCreateOverwritesPreviousChangedAt() {
        LocalDateTime old = LocalDateTime.of(2000, 1, 1, 0, 0);
        history.setChangedAt(old);
        history.onCreate();
        assertNotEquals(old, history.getChangedAt());
    }

    // -------------------------------------------------------------------------
    // Combined / integration-style
    // -------------------------------------------------------------------------

    @Test
    void testFullObjectConfiguration() {
        WorkOrder wo = new WorkOrder();
        LocalDateTime ts = LocalDateTime.of(2024, 7, 4, 12, 0);

        history.setId(1L);
        history.setWorkOrder(wo);
        history.setPreviousStatus(WorkOrderStatus.OPEN);
        history.setNewStatus(WorkOrderStatus.IN_PROGRESS);
        history.setChangedBy("technician");
        history.setChangedAt(ts);

        assertEquals(1L, history.getId());
        assertSame(wo, history.getWorkOrder());
        assertEquals(WorkOrderStatus.OPEN, history.getPreviousStatus());
        assertEquals(WorkOrderStatus.IN_PROGRESS, history.getNewStatus());
        assertEquals("technician", history.getChangedBy());
        assertEquals(ts, history.getChangedAt());
    }
}