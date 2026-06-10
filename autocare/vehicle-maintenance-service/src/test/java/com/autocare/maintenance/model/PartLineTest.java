package com.autocare.maintenance.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PartLineTest {

    private PartLine partLine;

    @BeforeEach
    void setUp() {
        partLine = new PartLine();
    }

    // -------------------------------------------------------------------------
    // id
    // -------------------------------------------------------------------------

    @Test
    void testSetAndGetId() {
        partLine.setId(42L);
        assertEquals(42L, partLine.getId());
    }

    @Test
    void testIdDefaultsToNull() {
        assertNull(partLine.getId());
    }

    // -------------------------------------------------------------------------
    // workOrder – EI_EXPOSE_REP / EI_EXPOSE_REP2 remediation tests
    // The getter and setter must return/accept the exact same reference so that
    // JPA proxy identity, lazy-loading, and dirty-tracking are preserved.
    // -------------------------------------------------------------------------

    @Test
    void testSetAndGetWorkOrder_sameReference() {
        WorkOrder workOrder = new WorkOrder();
        partLine.setWorkOrder(workOrder);

        // EI_EXPOSE_REP2 fix: setter must store the reference as-is (no defensive copy)
        // EI_EXPOSE_REP  fix: getter must return the stored reference as-is
        assertSame(workOrder, partLine.getWorkOrder(),
                "getWorkOrder() must return the exact same reference that was set " +
                "(defensive copying would break JPA proxy identity)");
    }

    @Test
    void testSetWorkOrder_nullAllowed() {
        partLine.setWorkOrder(null);
        assertNull(partLine.getWorkOrder());
    }

    @Test
    void testSetWorkOrder_replacesExistingReference() {
        WorkOrder first  = new WorkOrder();
        WorkOrder second = new WorkOrder();

        partLine.setWorkOrder(first);
        partLine.setWorkOrder(second);

        assertSame(second, partLine.getWorkOrder(),
                "setWorkOrder() must replace the stored reference with the new one");
        assertNotSame(first, partLine.getWorkOrder());
    }

    @Test
    void testGetWorkOrder_mutatingExternalReferenceIsReflected() {
        // Because no defensive copy is made, mutations to the original object
        // must be visible through the getter (JPA dirty-tracking requirement).
        WorkOrder workOrder = new WorkOrder();
        partLine.setWorkOrder(workOrder);

        // mutate the original object
        workOrder.setId(99L);

        assertSame(workOrder, partLine.getWorkOrder());
        assertEquals(99L, partLine.getWorkOrder().getId());
    }

    // -------------------------------------------------------------------------
    // partName
    // -------------------------------------------------------------------------

    @Test
    void testSetAndGetPartName() {
        partLine.setPartName("Oil Filter");
        assertEquals("Oil Filter", partLine.getPartName());
    }

    @Test
    void testPartNameDefaultsToNull() {
        assertNull(partLine.getPartName());
    }

    @Test
    void testSetPartName_emptyString() {
        partLine.setPartName("");
        assertEquals("", partLine.getPartName());
    }

    @Test
    void testSetPartName_overwritesPreviousValue() {
        partLine.setPartName("Brake Pad");
        partLine.setPartName("Air Filter");
        assertEquals("Air Filter", partLine.getPartName());
    }

    // -------------------------------------------------------------------------
    // quantity
    // -------------------------------------------------------------------------

    @Test
    void testSetAndGetQuantity() {
        partLine.setQuantity(5);
        assertEquals(5, partLine.getQuantity());
    }

    @Test
    void testQuantityDefaultsToNull() {
        assertNull(partLine.getQuantity());
    }

    @Test
    void testSetQuantity_minimumValidValue() {
        partLine.setQuantity(1);
        assertEquals(1, partLine.getQuantity());
    }

    @Test
    void testSetQuantity_largeValue() {
        partLine.setQuantity(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, partLine.getQuantity());
    }

    // -------------------------------------------------------------------------
    // unitCost
    // -------------------------------------------------------------------------

    @Test
    void testSetAndGetUnitCost() {
        BigDecimal cost = new BigDecimal("19.99");
        partLine.setUnitCost(cost);
        assertEquals(new BigDecimal("19.99"), partLine.getUnitCost());
    }

    @Test
    void testUnitCostDefaultsToNull() {
        assertNull(partLine.getUnitCost());
    }

    @Test
    void testSetUnitCost_minimumValidValue() {
        BigDecimal minCost = new BigDecimal("0.01");
        partLine.setUnitCost(minCost);
        assertEquals(new BigDecimal("0.01"), partLine.getUnitCost());
    }

    @Test
    void testSetUnitCost_largeValue() {
        BigDecimal largeCost = new BigDecimal("9999999.99");
        partLine.setUnitCost(largeCost);
        assertEquals(new BigDecimal("9999999.99"), partLine.getUnitCost());
    }

    @Test
    void testSetUnitCost_overwritesPreviousValue() {
        partLine.setUnitCost(new BigDecimal("10.00"));
        partLine.setUnitCost(new BigDecimal("25.50"));
        assertEquals(new BigDecimal("25.50"), partLine.getUnitCost());
    }

    // -------------------------------------------------------------------------
    // combined state
    // -------------------------------------------------------------------------

    @Test
    void testFullyPopulatedPartLine() {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(1L);

        partLine.setId(100L);
        partLine.setWorkOrder(workOrder);
        partLine.setPartName("Spark Plug");
        partLine.setQuantity(4);
        partLine.setUnitCost(new BigDecimal("3.75"));

        assertEquals(100L, partLine.getId());
        assertSame(workOrder, partLine.getWorkOrder());
        assertEquals("Spark Plug", partLine.getPartName());
        assertEquals(4, partLine.getQuantity());
        assertEquals(new BigDecimal("3.75"), partLine.getUnitCost());
    }
}