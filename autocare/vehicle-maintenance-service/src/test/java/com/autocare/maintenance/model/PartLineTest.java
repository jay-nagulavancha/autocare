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

    @Test
    void testSetAndGetId() {
        partLine.setId(42L);
        assertEquals(42L, partLine.getId());
    }

    @Test
    void testSetAndGetPartName() {
        partLine.setPartName("Oil Filter");
        assertEquals("Oil Filter", partLine.getPartName());
    }

    @Test
    void testSetAndGetQuantity() {
        partLine.setQuantity(5);
        assertEquals(5, partLine.getQuantity());
    }

    @Test
    void testSetAndGetUnitCost() {
        BigDecimal cost = new BigDecimal("19.99");
        partLine.setUnitCost(cost);
        assertEquals(new BigDecimal("19.99"), partLine.getUnitCost());
    }

    @Test
    void testSetAndGetWorkOrder() {
        WorkOrder workOrder = new WorkOrder();
        partLine.setWorkOrder(workOrder);
        assertSame(workOrder, partLine.getWorkOrder());
    }

    @Test
    void testGetWorkOrderReturnsSameReference() {
        WorkOrder workOrder = new WorkOrder();
        partLine.setWorkOrder(workOrder);
        WorkOrder retrieved = partLine.getWorkOrder();
        // Verify that the same JPA-managed entity reference is returned (not a defensive copy)
        assertSame(workOrder, retrieved);
    }

    @Test
    void testSetWorkOrderStoresSameReference() {
        WorkOrder workOrder = new WorkOrder();
        partLine.setWorkOrder(workOrder);
        // Verify that the stored reference is the same object passed in (not a copy)
        assertSame(workOrder, partLine.getWorkOrder());
    }

    @Test
    void testWorkOrderMutationReflectedInPartLine() {
        WorkOrder workOrder = new WorkOrder();
        partLine.setWorkOrder(workOrder);
        // Since we store the reference directly (no defensive copy), mutations to the
        // original object should be reflected when retrieved
        WorkOrder retrieved = partLine.getWorkOrder();
        assertSame(workOrder, retrieved);
    }

    @Test
    void testSetWorkOrderToNull() {
        partLine.setWorkOrder(null);
        assertNull(partLine.getWorkOrder());
    }

    @Test
    void testSetIdToNull() {
        partLine.setId(null);
        assertNull(partLine.getId());
    }

    @Test
    void testSetPartNameToNull() {
        partLine.setPartName(null);
        assertNull(partLine.getPartName());
    }

    @Test
    void testSetQuantityToNull() {
        partLine.setQuantity(null);
        assertNull(partLine.getQuantity());
    }

    @Test
    void testSetUnitCostToNull() {
        partLine.setUnitCost(null);
        assertNull(partLine.getUnitCost());
    }

    @Test
    void testDefaultIdIsNull() {
        assertNull(partLine.getId());
    }

    @Test
    void testDefaultWorkOrderIsNull() {
        assertNull(partLine.getWorkOrder());
    }

    @Test
    void testDefaultPartNameIsNull() {
        assertNull(partLine.getPartName());
    }

    @Test
    void testDefaultQuantityIsNull() {
        assertNull(partLine.getQuantity());
    }

    @Test
    void testDefaultUnitCostIsNull() {
        assertNull(partLine.getUnitCost());
    }

    @Test
    void testUnitCostPrecision() {
        BigDecimal cost = new BigDecimal("0.01");
        partLine.setUnitCost(cost);
        assertEquals(new BigDecimal("0.01"), partLine.getUnitCost());
    }

    @Test
    void testQuantityMinimumValue() {
        partLine.setQuantity(1);
        assertEquals(1, partLine.getQuantity());
    }

    @Test
    void testPartLineWithAllFieldsSet() {
        WorkOrder workOrder = new WorkOrder();
        partLine.setId(1L);
        partLine.setWorkOrder(workOrder);
        partLine.setPartName("Brake Pad");
        partLine.setQuantity(4);
        partLine.setUnitCost(new BigDecimal("25.50"));

        assertEquals(1L, partLine.getId());
        assertSame(workOrder, partLine.getWorkOrder());
        assertEquals("Brake Pad", partLine.getPartName());
        assertEquals(4, partLine.getQuantity());
        assertEquals(new BigDecimal("25.50"), partLine.getUnitCost());
    }

    @Test
    void testReplaceWorkOrder() {
        WorkOrder workOrder1 = new WorkOrder();
        WorkOrder workOrder2 = new WorkOrder();

        partLine.setWorkOrder(workOrder1);
        assertSame(workOrder1, partLine.getWorkOrder());

        partLine.setWorkOrder(workOrder2);
        assertSame(workOrder2, partLine.getWorkOrder());
    }

    @Test
    void testUnitCostLargeValue() {
        BigDecimal largeCost = new BigDecimal("99999999.99");
        partLine.setUnitCost(largeCost);
        assertEquals(new BigDecimal("99999999.99"), partLine.getUnitCost());
    }

    @Test
    void testQuantityLargeValue() {
        partLine.setQuantity(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, partLine.getQuantity());
    }
}