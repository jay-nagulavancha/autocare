package com.autocare.maintenance.payload.response;

import com.autocare.maintenance.model.ServiceSchedule;

import java.time.LocalDateTime;

public class ScheduleResponse {
    private Long id;
    private Long vehicleId;
    private Long bayId;
    private LocalDateTime scheduledAt;
    private String serviceType;
    private String status;

    public static ScheduleResponse from(ServiceSchedule s) {
        ScheduleResponse r = new ScheduleResponse();
        r.id = s.getId();
        r.vehicleId = s.getVehicle() != null ? s.getVehicle().getId() : null;
        r.bayId = s.getBay() != null ? s.getBay().getId() : null;
        r.scheduledAt = s.getScheduledAt();
        r.serviceType = s.getServiceType();
        r.status = s.getStatus();
        return r;
    }

    public Long getId() { return id; }
    public Long getVehicleId() { return vehicleId; }
    public Long getBayId() { return bayId; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public String getServiceType() { return serviceType; }
    public String getStatus() { return status; }
}
