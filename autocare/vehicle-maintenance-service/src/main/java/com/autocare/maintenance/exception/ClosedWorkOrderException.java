package com.autocare.maintenance.exception;

public class ClosedWorkOrderException extends RuntimeException {
    public ClosedWorkOrderException() {
        super("Cannot modify line items on a closed work order");
    }
}
