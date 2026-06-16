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
        assertSame(vehicle, retrieved);
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
        assertSame(bay, retrieved);
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
    void testSetStatusCompleted() {
        serviceSchedule.setStatus("COMPLETED");
        assertEquals("COMPLETED", serviceSchedule.getStatus());
    }

    @Test
    void testSetStatusCancelled() {
        serviceSchedule.setStatus("CANCELLED");
        assertEquals("CANCELLED", serviceSchedule.getStatus());
    }

    @Test
    void testVehicleReferenceExposure_modifyingExternalVehicleAffectsInternal() {
        // This test documents the EI_EXPOSE_REP behavior:
        // The getter returns the same reference (mutable object exposure)
        Vehicle vehicle = new Vehicle();
        serviceSchedule.setVehicle(vehicle);
        Vehicle retrieved = serviceSchedule.getVehicle();
        // Since the fix uses @SuppressWarnings rather than defensive copy,
        // the reference is still the same object
        assertSame(vehicle, retrieved);
    }

    @Test
    void testBayReferenceExposure_modifyingExternalBayAffectsInternal() {
        // This test documents the EI_EXPOSE_REP behavior for Bay:
        // The getter returns the same reference (mutable object exposure)
        Bay bay = new Bay();
        serviceSchedule.setBay(bay);
        Bay retrieved = serviceSchedule.getBay();
        assertSame(bay, retrieved);
    }

    @Test
    void testSetVehicleTwice() {
        Vehicle vehicle1 = new Vehicle();
        Vehicle vehicle2 = new Vehicle();
        serviceSchedule.setVehicle(vehicle1);
        serviceSchedule.setVehicle(vehicle2);
        assertSame(vehicle2, serviceSchedule.getVehicle());
    }

    @Test
    void testSetBayTwice() {
        Bay bay1 = new Bay();
        Bay bay2 = new Bay();
        serviceSchedule.setBay(bay1);
        serviceSchedule.setBay(bay2);
        assertSame(bay2, serviceSchedule.getBay());
    }

    @Test
    void testScheduledAtWithSpecificDateTime() {
        LocalDateTime specificDate = LocalDateTime.of(2024, 6, 15, 10, 30, 0);
        serviceSchedule.setScheduledAt(specificDate);
        assertEquals(LocalDateTime.of(2024, 6, 15, 10, 30, 0), serviceSchedule.getScheduledAt());
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
    void testServiceTypeVariousValues() {
        String[] serviceTypes = {"OIL_CHANGE", "TIRE_ROTATION", "BRAKE_INSPECTION", "FULL_SERVICE"};
        for (String type : serviceTypes) {
            serviceSchedule.setServiceType(type);
            assertEquals(type, serviceSchedule.getServiceType());
        }
    }
}