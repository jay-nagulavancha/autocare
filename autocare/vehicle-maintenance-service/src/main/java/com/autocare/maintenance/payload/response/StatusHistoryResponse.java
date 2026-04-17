package com.autocare.maintenance.payload.response;

import com.autocare.maintenance.model.WorkOrderStatus;
import com.autocare.maintenance.model.WorkOrderStatusHistory;

import java.time.LocalDateTime;

public class StatusHistoryResponse {
    private Long id;
    private WorkOrderStatus previousStatus;
    private WorkOrderStatus newStatus;
    private String changedBy;
    private LocalDateTime changedAt;

    public static StatusHistoryResponse from(WorkOrderStatusHistory h) {
        StatusHistoryResponse r = new StatusHistoryResponse();
        r.id = h.getId();
        r.previousStatus = h.getPreviousStatus();
        r.newStatus = h.getNewStatus();
        r.changedBy = h.getChangedBy();
        r.changedAt = h.getChangedAt();
        return r;
    }

    public Long getId() { return id; }
    public WorkOrderStatus getPreviousStatus() { return previousStatus; }
    public WorkOrderStatus getNewStatus() { return newStatus; }
    public String getChangedBy() { return changedBy; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
