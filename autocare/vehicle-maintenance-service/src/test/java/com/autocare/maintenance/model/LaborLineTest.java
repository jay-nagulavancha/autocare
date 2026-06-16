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
        laborLine.setId(1L);
        assertEquals(1L, laborLine.getId());
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
        workOrder.setId(10L);
        laborLine.setWorkOrder(workOrder);
        assertNotNull(laborLine.getWorkOrder());
        assertEquals(10L, laborLine.getWorkOrder().getId());
    }

    @Test
    void testGetWorkOrderReturnsSameReference() {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(5L);
        laborLine.setWorkOrder(workOrder);
        WorkOrder retrieved = laborLine.getWorkOrder();
        assertSame(workOrder, retrieved);
    }

    @Test
    void testSetWorkOrderWithNull() {
        laborLine.setWorkOrder(null);
        assertNull(laborLine.getWorkOrder());
    }

    @Test
    void testWorkOrderMutationAfterSet() {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(1L);
        laborLine.setWorkOrder(workOrder);

        // Mutate the original object - since EI_EXPOSE_REP is suppressed (not defensive copy),
        // the internal reference will reflect changes. This test documents the current behavior.
        workOrder.setId(99L);
        assertEquals(99L, laborLine.getWorkOrder().getId());
    }

    @Test
    void testGetWorkOrderMutationReflectsInternally() {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(2L);
        laborLine.setWorkOrder(workOrder);

        WorkOrder retrieved = laborLine.getWorkOrder();
        retrieved.setId(200L);

        // Since EI_EXPOSE_REP is suppressed, the internal reference is exposed
        assertEquals(200L, laborLine.getWorkOrder().getId());
    }

    @Test
    void testIdDefaultIsNull() {
        assertNull(laborLine.getId());
    }

    @Test
    void testDescriptionDefaultIsNull() {
        assertNull(laborLine.getDescription());
    }

    @Test
    void testHoursDefaultIsNull() {
        assertNull(laborLine.getHours());
    }

    @Test
    void testRateDefaultIsNull() {
        assertNull(laborLine.getRate());
    }

    @Test
    void testWorkOrderDefaultIsNull() {
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
        String longDesc = "A".repeat(500);
        laborLine.setDescription(longDesc);
        assertEquals(longDesc, laborLine.getDescription());
    }

    @Test
    void testMultipleWorkOrderAssignments() {
        WorkOrder firstWorkOrder = new WorkOrder();
        firstWorkOrder.setId(1L);
        laborLine.setWorkOrder(firstWorkOrder);
        assertEquals(1L, laborLine.getWorkOrder().getId());

        WorkOrder secondWorkOrder = new WorkOrder();
        secondWorkOrder.setId(2L);
        laborLine.setWorkOrder(secondWorkOrder);
        assertEquals(2L, laborLine.getWorkOrder().getId());
    }

    @Test
    void testLaborLineIsNewInstance() {
        LaborLine anotherLaborLine = new LaborLine();
        assertNotSame(laborLine, anotherLaborLine);
    }

    @Test
    void testSetIdWithZero() {
        laborLine.setId(0L);
        assertEquals(0L, laborLine.getId());
    }

    @Test
    void testSetIdWithNegativeValue() {
        laborLine.setId(-1L);
        assertEquals(-1L, laborLine.getId());
    }
}