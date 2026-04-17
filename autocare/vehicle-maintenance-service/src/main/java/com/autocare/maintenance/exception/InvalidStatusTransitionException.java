package com.autocare.maintenance.exception;

import com.autocare.maintenance.model.WorkOrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(WorkOrderStatus from, WorkOrderStatus to) {
        super("Invalid status transition from " + from + " to " + to);
    }
}
