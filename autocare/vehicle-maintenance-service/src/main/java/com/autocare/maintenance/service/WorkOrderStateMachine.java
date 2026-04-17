package com.autocare.maintenance.service;

import com.autocare.maintenance.model.WorkOrderStatus;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WorkOrderStateMachine {

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALLOWED_TRANSITIONS;

    static {
        ALLOWED_TRANSITIONS = new EnumMap<>(WorkOrderStatus.class);
        ALLOWED_TRANSITIONS.put(WorkOrderStatus.OPEN,
                EnumSet.of(WorkOrderStatus.IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(WorkOrderStatus.IN_PROGRESS,
                EnumSet.of(WorkOrderStatus.PENDING_PARTS, WorkOrderStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(WorkOrderStatus.PENDING_PARTS,
                EnumSet.of(WorkOrderStatus.IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(WorkOrderStatus.COMPLETED,
                EnumSet.of(WorkOrderStatus.INVOICED));
        ALLOWED_TRANSITIONS.put(WorkOrderStatus.INVOICED,
                EnumSet.noneOf(WorkOrderStatus.class));
    }

    public boolean isValidTransition(WorkOrderStatus from, WorkOrderStatus to) {
        Set<WorkOrderStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public Set<WorkOrderStatus> allowedNext(WorkOrderStatus from) {
        return Collections.unmodifiableSet(
                ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(WorkOrderStatus.class)));
    }
}
