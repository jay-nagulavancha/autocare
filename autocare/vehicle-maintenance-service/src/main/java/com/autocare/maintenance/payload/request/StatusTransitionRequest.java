package com.autocare.maintenance.payload.request;

import com.autocare.maintenance.model.WorkOrderStatus;

import javax.validation.constraints.NotNull;

public class StatusTransitionRequest {

    @NotNull
    private WorkOrderStatus targetStatus;

    public WorkOrderStatus getTargetStatus() { return targetStatus; }
    public void setTargetStatus(WorkOrderStatus targetStatus) { this.targetStatus = targetStatus; }
}
