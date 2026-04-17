package com.autocare.maintenance.payload.response;

import com.autocare.maintenance.model.Technician;

public class TechnicianResponse {
    private Long id;
    private String name;
    private boolean active;
    private long workOrderCount;

    public static TechnicianResponse from(Technician t, long workOrderCount) {
        TechnicianResponse r = new TechnicianResponse();
        r.id = t.getId();
        r.name = t.getName();
        r.active = t.isActive();
        r.workOrderCount = workOrderCount;
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public long getWorkOrderCount() { return workOrderCount; }
}
