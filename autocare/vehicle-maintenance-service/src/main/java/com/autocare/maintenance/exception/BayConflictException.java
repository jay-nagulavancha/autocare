package com.autocare.maintenance.exception;

public class BayConflictException extends RuntimeException {
    public BayConflictException() {
        super("Bay is not available for the requested time slot");
    }
}
