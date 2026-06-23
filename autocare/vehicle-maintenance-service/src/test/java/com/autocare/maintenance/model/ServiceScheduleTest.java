package com.autocare.maintenance.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ServiceScheduleTest {

    private ServiceSchedule serviceSchedule;

    @BeforeEach
    void setUp() {
        serviceSchedule = new ServiceSchedule();
    }

    @Test
    void testDefaultStatusIsConfirmed() {
        assertEquals("CONFIRMED", serviceSchedule.getStatus());
    }

    @Test
    void testSetAndGetId() {
        serviceSchedule.setId(42L);
        assertEquals(42L, serviceSchedule.getId());
    }

    @Test
    void testSetAndGetIdNull() {
        serviceSchedule.setId(null);
        assertNull(serviceSchedule.getId());
    }

    @Test
    void testSetAndGetVehicle() {
        Vehicle vehicle = new Vehicle();
        serviceSchedule.setVehicle(vehicle);
        assertSame(vehicle, serviceSchedule.getVehicle());
    }

    @Test
    void testSetVehicleNull() {
        serviceSchedule.setVehicle(null);
        assertNull(serviceSchedule.getVehicle());
    }

    @Test
    void testGetVehicleReturnsSameReference() {
        Vehicle vehicle = new Vehicle();
        serviceSchedule.setVehicle(vehicle);
        Vehicle retrieved = serviceSchedule.getVehicle();
        assertSame(vehicle, retrieved,
                "getVehicle() should return the same JPA-managed entity reference (no defensive copy)");
    }

    @Test
    void testSetVehicleStoresSameReference() {
        Vehicle vehicle = new Vehicle();
        serviceSchedule.setVehicle(vehicle);
        assertSame(vehicle, serviceSchedule.getVehicle(),
                "setVehicle() should store the same JPA-managed entity reference (no defensive copy)");
    }

    @Test
    void testSetAndGetBay() {
        Bay bay = new Bay();
        serviceSchedule.setBay(bay);
        assertSame(bay, serviceSchedule.getBay());
    }

    @Test
    void testSetBayNull() {
        serviceSchedule.setBay(null);
        assertNull(serviceSchedule.getBay());
    }

    @Test
    void testGetBayReturnsSameReference() {
        Bay bay = new Bay();
        serviceSchedule.setBay(bay);
        Bay retrieved = serviceSchedule.getBay();
        assertSame(bay, retrieved,
                "getBay() should return the same JPA-managed entity reference (no defensive copy)");
    }

    @Test
    void testSetBayStoresSameReference() {
        Bay bay = new Bay();
        serviceSchedule.setBay(bay);
        assertSame(bay, serviceSchedule.getBay(),
                "setBay() should store the same JPA-managed entity reference (no defensive copy)");
    }

    @Test
    void testSetAndGetScheduledAt() {
        LocalDateTime now = LocalDateTime.now();
        serviceSchedule.setScheduledAt(now);
        assertEquals(now, serviceSchedule.getScheduledAt());
    }

    @Test
    void testSetScheduledAtNull() {
        serviceSchedule.setScheduledAt(null);
        assertNull(serviceSchedule.getScheduledAt());
    }

    @Test
    void testSetAndGetServiceType() {
        serviceSchedule.setServiceType("OIL_CHANGE");
        assertEquals("OIL_CHANGE", serviceSchedule.getServiceType());
    }

    @Test
    void testSetServiceTypeNull() {
        serviceSchedule.setServiceType(null);
        assertNull(serviceSchedule.getServiceType());
    }

    @Test
    void testSetAndGetStatus() {
        serviceSchedule.setStatus("PENDING");
        assertEquals("PENDING", serviceSchedule.getStatus());
    }

    @Test
    void testSetStatusNull() {
        serviceSchedule.setStatus(null);
        assertNull(serviceSchedule.getStatus());
    }

    @Test
    void testSetStatusOverridesDefault() {
        assertEquals("CONFIRMED", serviceSchedule.getStatus());
        serviceSchedule.setStatus("CANCELLED");
        assertEquals("CANCELLED", serviceSchedule.getStatus());
    }

    @Test
    void testVehicleReferenceIsNotCopied_mutationReflected() {
        Vehicle vehicle = new Vehicle();
        serviceSchedule.setVehicle(vehicle);
        // Since no defensive copy is made, the same reference is stored
        // Mutating the original vehicle should be reflected in the retrieved vehicle
        Vehicle retrieved = serviceSchedule.getVehicle();
        assertSame(vehicle, retrieved,
                "JPA entity should not be defensively copied; mutation should be reflected");
    }

    @Test
    void testBayReferenceIsNotCopied_mutationReflected() {
        Bay bay = new Bay();
        serviceSchedule.setBay(bay);
        // Since no defensive copy is made, the same reference is stored
        Bay retrieved = serviceSchedule.getBay();
        assertSame(bay, retrieved,
                "JPA entity should not be defensively copied; mutation should be reflected");
    }

    @Test
    void testNewServiceScheduleHasNullId() {
        assertNull(serviceSchedule.getId());
    }

    @Test
    void testNewServiceScheduleHasNullVehicle() {
        assertNull(serviceSchedule.getVehicle());
    }

    @Test
    void testNewServiceScheduleHasNullBay() {
        assertNull(serviceSchedule.getBay());
    }

    @Test
    void testNewServiceScheduleHasNullScheduledAt() {
        assertNull(serviceSchedule.getScheduledAt());
    }

    @Test
    void testNewServiceScheduleHasNullServiceType() {
        assertNull(serviceSchedule.getServiceType());
    }

    @Test
    void testScheduledAtWithSpecificDateTime() {
        LocalDateTime specificTime = LocalDateTime.of(2024, 6, 15, 10, 30, 0);
        serviceSchedule.setScheduledAt(specificTime);
        assertEquals(LocalDateTime.of(2024, 6, 15, 10, 30, 0), serviceSchedule.getScheduledAt());
    }

    @Test
    void testMultipleVehicleAssignments() {
        Vehicle vehicle1 = new Vehicle();
        Vehicle vehicle2 = new Vehicle();

        serviceSchedule.setVehicle(vehicle1);
        assertSame(vehicle1, serviceSchedule.getVehicle());

        serviceSchedule.setVehicle(vehicle2);
        assertSame(vehicle2, serviceSchedule.getVehicle());
    }

    @Test
    void testMultipleBayAssignments() {
        Bay bay1 = new Bay();
        Bay bay2 = new Bay();

        serviceSchedule.setBay(bay1);
        assertSame(bay1, serviceSchedule.getBay());

        serviceSchedule.setBay(bay2);
        assertSame(bay2, serviceSchedule.getBay());
    }
}