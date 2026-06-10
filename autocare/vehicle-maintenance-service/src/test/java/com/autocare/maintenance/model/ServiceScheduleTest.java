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
    void testVehicleReferenceIdentityPreservedAfterMutation() {
        Vehicle vehicle = new Vehicle();
        serviceSchedule.setVehicle(vehicle);
        // Mutate the vehicle object externally
        vehicle.setId(99L);
        // The schedule should reflect the mutation since it holds the same reference
        assertSame(vehicle, serviceSchedule.getVehicle());
        assertEquals(99L, serviceSchedule.getVehicle().getId());
    }

    @Test
    void testBayReferenceIdentityPreservedAfterMutation() {
        Bay bay = new Bay();
        serviceSchedule.setBay(bay);
        // Mutate the bay object externally
        bay.setId(55L);
        // The schedule should reflect the mutation since it holds the same reference
        assertSame(bay, serviceSchedule.getBay());
        assertEquals(55L, serviceSchedule.getBay().getId());
    }

    @Test
    void testSetVehicleReplacesExistingVehicle() {
        Vehicle vehicle1 = new Vehicle();
        vehicle1.setId(1L);
        Vehicle vehicle2 = new Vehicle();
        vehicle2.setId(2L);

        serviceSchedule.setVehicle(vehicle1);
        assertSame(vehicle1, serviceSchedule.getVehicle());

        serviceSchedule.setVehicle(vehicle2);
        assertSame(vehicle2, serviceSchedule.getVehicle());
        assertNotSame(vehicle1, serviceSchedule.getVehicle());
    }

    @Test
    void testSetBayReplacesExistingBay() {
        Bay bay1 = new Bay();
        bay1.setId(1L);
        Bay bay2 = new Bay();
        bay2.setId(2L);

        serviceSchedule.setBay(bay1);
        assertSame(bay1, serviceSchedule.getBay());

        serviceSchedule.setBay(bay2);
        assertSame(bay2, serviceSchedule.getBay());
        assertNotSame(bay1, serviceSchedule.getBay());
    }

    @Test
    void testFullScheduleSetup() {
        LocalDateTime scheduledTime = LocalDateTime.of(2024, 6, 15, 10, 30);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(10L);
        Bay bay = new Bay();
        bay.setId(5L);

        serviceSchedule.setId(1L);
        serviceSchedule.setVehicle(vehicle);
        serviceSchedule.setBay(bay);
        serviceSchedule.setScheduledAt(scheduledTime);
        serviceSchedule.setServiceType("TIRE_ROTATION");
        serviceSchedule.setStatus("CONFIRMED");

        assertEquals(1L, serviceSchedule.getId());
        assertSame(vehicle, serviceSchedule.getVehicle());
        assertSame(bay, serviceSchedule.getBay());
        assertEquals(scheduledTime, serviceSchedule.getScheduledAt());
        assertEquals("TIRE_ROTATION", serviceSchedule.getServiceType());
        assertEquals("CONFIRMED", serviceSchedule.getStatus());
    }

    @Test
    void testNewInstanceHasNullId() {
        assertNull(serviceSchedule.getId());
    }

    @Test
    void testNewInstanceHasNullVehicle() {
        assertNull(serviceSchedule.getVehicle());
    }

    @Test
    void testNewInstanceHasNullBay() {
        assertNull(serviceSchedule.getBay());
    }

    @Test
    void testNewInstanceHasNullScheduledAt() {
        assertNull(serviceSchedule.getScheduledAt());
    }

    @Test
    void testNewInstanceHasNullServiceType() {
        assertNull(serviceSchedule.getServiceType());
    }
}