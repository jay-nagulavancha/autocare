package com.autocare.maintenance.payload.response;

import com.autocare.maintenance.model.Vehicle;

import java.time.LocalDateTime;

public class VehicleResponse {
    private Long id;
    private String vin;
    private String make;
    private String model;
    private Integer year;
    private String ownerUsername;
    private LocalDateTime createdAt;

    public static VehicleResponse from(Vehicle v) {
        VehicleResponse r = new VehicleResponse();
        r.id = v.getId();
        r.vin = v.getVin();
        r.make = v.getMake();
        r.model = v.getModel();
        r.year = v.getYear();
        r.ownerUsername = v.getOwnerUsername();
        r.createdAt = v.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getVin() { return vin; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public Integer getYear() { return year; }
    public String getOwnerUsername() { return ownerUsername; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
