package com.autocare.maintenance.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class LaborLineTest {

    private LaborLine laborLine;

    @BeforeEach
    void setUp() {
        laborLine = new LaborLine();
    }

    @Test
    void testSetAndGetId() {
        laborLine.setId(42L);
        assertEquals(42L, laborLine.getId());
    }

    @Test
    void testSetAndGetDescription() {
        laborLine.setDescription("Oil Change");
        assertEquals("Oil Change", laborLine.getDescription());
    }

    @Test
    void testSetAndGetHours() {
        BigDecimal hours = new BigDecimal("2.50");
        laborLine.setHours(hours);
        assertEquals(new BigDecimal("2.50"), laborLine.getHours());
    }

    @Test
    void testSetAndGetRate() {
        BigDecimal rate = new BigDecimal("75.00");
        laborLine.setRate(rate);
        assertEquals(new BigDecimal("75.00"), laborLine.getRate());
    }

    @Test
    void testSetAndGetWorkOrder() {
        WorkOrder workOrder = new WorkOrder();
        laborLine.setWorkOrder(workOrder);
        assertSame(workOrder, laborLine.getWorkOrder(),
                "getWorkOrder should return the same JPA-managed entity reference (no defensive copy)");
    }

    @Test
    void testWorkOrderReferenceIdentity() {
        WorkOrder workOrder = new WorkOrder();
        laborLine.setWorkOrder(workOrder);
        WorkOrder retrieved = laborLine.getWorkOrder();
        // EI_EXPOSE_REP fix: for JPA entities, the same reference must be returned
        // to preserve proxy identity and lazy-loading
        assertSame(workOrder, retrieved,
                "WorkOrder reference must be identical to support JPA proxy behavior");
    }

    @Test
    void testSetWorkOrderPreservesReference() {
        WorkOrder workOrder1 = new WorkOrder();
        WorkOrder workOrder2 = new WorkOrder();

        laborLine.setWorkOrder(workOrder1);
        assertSame(workOrder1, laborLine.getWorkOrder());

        laborLine.setWorkOrder(workOrder2);
        assertSame(workOrder2, laborLine.getWorkOrder(),
                "After updating workOrder, the new reference should be returned");
    }

    @Test
    void testSetWorkOrderToNull() {
        WorkOrder workOrder = new WorkOrder();
        laborLine.setWorkOrder(workOrder);
        laborLine.setWorkOrder(null);
        assertNull(laborLine.getWorkOrder());
    }

    @Test
    void testDefaultIdIsNull() {
        assertNull(laborLine.getId());
    }

    @Test
    void testDefaultDescriptionIsNull() {
        assertNull(laborLine.getDescription());
    }

    @Test
    void testDefaultHoursIsNull() {
        assertNull(laborLine.getHours());
    }

    @Test
    void testDefaultRateIsNull() {
        assertNull(laborLine.getRate());
    }

    @Test
    void testDefaultWorkOrderIsNull() {
        assertNull(laborLine.getWorkOrder());
    }

    @Test
    void testSetHoursWithMinimumValue() {
        BigDecimal minHours = new BigDecimal("0.01");
        laborLine.setHours(minHours);
        assertEquals(new BigDecimal("0.01"), laborLine.getHours());
    }

    @Test
    void testSetRateWithMinimumValue() {
        BigDecimal minRate = new BigDecimal("0.01");
        laborLine.setRate(minRate);
        assertEquals(new BigDecimal("0.01"), laborLine.getRate());
    }

    @Test
    void testSetHoursWithLargeValue() {
        BigDecimal largeHours = new BigDecimal("9999.99");
        laborLine.setHours(largeHours);
        assertEquals(new BigDecimal("9999.99"), laborLine.getHours());
    }

    @Test
    void testSetRateWithLargeValue() {
        BigDecimal largeRate = new BigDecimal("99999999.99");
        laborLine.setRate(largeRate);
        assertEquals(new BigDecimal("99999999.99"), laborLine.getRate());
    }

    @Test
    void testSetDescriptionWithEmptyString() {
        laborLine.setDescription("");
        assertEquals("", laborLine.getDescription());
    }

    @Test
    void testSetDescriptionWithLongString() {
        String longDescription = "A".repeat(500);
        laborLine.setDescription(longDescription);
        assertEquals(longDescription, laborLine.getDescription());
    }

    @Test
    void testMultipleFieldsSetTogether() {
        WorkOrder workOrder = new WorkOrder();
        laborLine.setId(1L);
        laborLine.setWorkOrder(workOrder);
        laborLine.setDescription("Brake Inspection");
        laborLine.setHours(new BigDecimal("1.50"));
        laborLine.setRate(new BigDecimal("85.00"));

        assertEquals(1L, laborLine.getId());
        assertSame(workOrder, laborLine.getWorkOrder());
        assertEquals("Brake Inspection", laborLine.getDescription());
        assertEquals(new BigDecimal("1.50"), laborLine.getHours());
        assertEquals(new BigDecimal("85.00"), laborLine.getRate());
    }

    @Test
    void testGetWorkOrderDoesNotReturnDefensiveCopy() {
        WorkOrder workOrder = new WorkOrder();
        laborLine.setWorkOrder(workOrder);

        WorkOrder first = laborLine.getWorkOrder();
        WorkOrder second = laborLine.getWorkOrder();

        // Both calls should return the same reference (JPA proxy compatibility)
        assertSame(first, second,
                "Repeated calls to getWorkOrder should return the same reference");
        assertSame(workOrder, first,
                "getWorkOrder must return the original reference, not a copy");
    }

    @Test
    void testSetWorkOrderDoesNotMakeDefensiveCopy() {
        WorkOrder workOrder = new WorkOrder();
        laborLine.setWorkOrder(workOrder);

        // The stored reference should be the exact same object passed in
        assertSame(workOrder, laborLine.getWorkOrder(),
                "setWorkOrder must store the original reference, not a defensive copy");
    }
}