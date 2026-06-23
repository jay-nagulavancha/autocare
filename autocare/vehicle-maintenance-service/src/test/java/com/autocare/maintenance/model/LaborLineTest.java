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
    void testSetAndGetIdNull() {
        laborLine.setId(null);
        assertNull(laborLine.getId());
    }

    @Test
    void testSetAndGetDescription() {
        laborLine.setDescription("Oil Change");
        assertEquals("Oil Change", laborLine.getDescription());
    }

    @Test
    void testSetAndGetDescriptionNull() {
        laborLine.setDescription(null);
        assertNull(laborLine.getDescription());
    }

    @Test
    void testSetAndGetHours() {
        BigDecimal hours = new BigDecimal("2.50");
        laborLine.setHours(hours);
        assertEquals(new BigDecimal("2.50"), laborLine.getHours());
    }

    @Test
    void testSetAndGetHoursNull() {
        laborLine.setHours(null);
        assertNull(laborLine.getHours());
    }

    @Test
    void testSetAndGetRate() {
        BigDecimal rate = new BigDecimal("75.00");
        laborLine.setRate(rate);
        assertEquals(new BigDecimal("75.00"), laborLine.getRate());
    }

    @Test
    void testSetAndGetRateNull() {
        laborLine.setRate(null);
        assertNull(laborLine.getRate());
    }

    @Test
    void testSetAndGetWorkOrder() {
        WorkOrder workOrder = new WorkOrder();
        laborLine.setWorkOrder(workOrder);
        assertSame(workOrder, laborLine.getWorkOrder());
    }

    @Test
    void testSetWorkOrderNull() {
        laborLine.setWorkOrder(null);
        assertNull(laborLine.getWorkOrder());
    }

    @Test
    void testGetWorkOrderReturnsSameInstance() {
        WorkOrder workOrder = new WorkOrder();
        laborLine.setWorkOrder(workOrder);
        WorkOrder retrieved = laborLine.getWorkOrder();
        // Verify that the same JPA-managed instance is returned (not a defensive copy)
        assertSame(workOrder, retrieved);
    }

    @Test
    void testSetWorkOrderStoresSameInstance() {
        WorkOrder workOrder = new WorkOrder();
        laborLine.setWorkOrder(workOrder);
        // Verify that the stored reference is the same object (not a copy)
        assertSame(workOrder, laborLine.getWorkOrder());
    }

    @Test
    void testWorkOrderMutationReflectedInLaborLine() {
        WorkOrder workOrder = new WorkOrder();
        laborLine.setWorkOrder(workOrder);
        // Since we store the reference directly (no defensive copy), mutations to the
        // original object should be reflected when retrieved
        WorkOrder retrieved = laborLine.getWorkOrder();
        assertSame(workOrder, retrieved);
    }

    @Test
    void testHoursWithMinimumValidValue() {
        BigDecimal minHours = new BigDecimal("0.01");
        laborLine.setHours(minHours);
        assertEquals(new BigDecimal("0.01"), laborLine.getHours());
    }

    @Test
    void testRateWithMinimumValidValue() {
        BigDecimal minRate = new BigDecimal("0.01");
        laborLine.setRate(minRate);
        assertEquals(new BigDecimal("0.01"), laborLine.getRate());
    }

    @Test
    void testHoursWithLargeValue() {
        BigDecimal largeHours = new BigDecimal("9999.99");
        laborLine.setHours(largeHours);
        assertEquals(new BigDecimal("9999.99"), laborLine.getHours());
    }

    @Test
    void testRateWithLargeValue() {
        BigDecimal largeRate = new BigDecimal("99999999.99");
        laborLine.setRate(largeRate);
        assertEquals(new BigDecimal("99999999.99"), laborLine.getRate());
    }

    @Test
    void testDefaultIdIsNull() {
        assertNull(laborLine.getId());
    }

    @Test
    void testDefaultWorkOrderIsNull() {
        assertNull(laborLine.getWorkOrder());
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
    void testSetWorkOrderMultipleTimes() {
        WorkOrder firstWorkOrder = new WorkOrder();
        WorkOrder secondWorkOrder = new WorkOrder();

        laborLine.setWorkOrder(firstWorkOrder);
        assertSame(firstWorkOrder, laborLine.getWorkOrder());

        laborLine.setWorkOrder(secondWorkOrder);
        assertSame(secondWorkOrder, laborLine.getWorkOrder());
    }

    @Test
    void testHoursImmutabilityNotEnforced() {
        // BigDecimal is immutable by nature, so this verifies the value is stored correctly
        BigDecimal hours = new BigDecimal("3.00");
        laborLine.setHours(hours);
        BigDecimal retrieved = laborLine.getHours();
        assertEquals(hours, retrieved);
    }

    @Test
    void testRateImmutabilityNotEnforced() {
        // BigDecimal is immutable by nature, so this verifies the value is stored correctly
        BigDecimal rate = new BigDecimal("100.00");
        laborLine.setRate(rate);
        BigDecimal retrieved = laborLine.getRate();
        assertEquals(rate, retrieved);
    }

    @Test
    void testDescriptionWithSpecialCharacters() {
        String description = "Brake pad replacement - front axle (2 pads)";
        laborLine.setDescription(description);
        assertEquals(description, laborLine.getDescription());
    }

    @Test
    void testDescriptionWithEmptyString() {
        laborLine.setDescription("");
        assertEquals("", laborLine.getDescription());
    }

    @Test
    void testIdWithZero() {
        laborLine.setId(0L);
        assertEquals(0L, laborLine.getId());
    }

    @Test
    void testIdWithNegativeValue() {
        laborLine.setId(-1L);
        assertEquals(-1L, laborLine.getId());
    }
}