package com.autocare.maintenance.payload.request;

import javax.validation.constraints.NotNull;

public class AssignRequest {

    @NotNull
    private Long technicianId;

    @NotNull
    private Long bayId;

    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }
    public Long getBayId() { return bayId; }
    public void setBayId(Long bayId) { this.bayId = bayId; }
}
