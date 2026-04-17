package com.autocare.maintenance.payload.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class CreateScheduleRequest {

    @NotNull
    private Long vehicleId;

    private Long bayId;

    @NotNull
    private LocalDateTime scheduledAt;

    @NotBlank
    private String serviceType;

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public Long getBayId() { return bayId; }
    public void setBayId(Long bayId) { this.bayId = bayId; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
}
