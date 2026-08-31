package com.autocare.maintenance.payload.response;

import com.autocare.maintenance.model.WorkOrder;
import com.autocare.maintenance.model.WorkOrderStatus;
import com.autocare.maintenance.model.Vehicle;
import com.autocare.maintenance.model.Technician;
import com.autocare.maintenance.model.Bay;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class WorkOrderResponse {
    private Long id;
    private VehicleResponse vehicle;
    private Long technicianId;
    private Long bayId;
    private WorkOrderStatus status;
    private String description;
    private LocalDateTime createdAt;
    private List<PartLineResponse> partLines;
    private List<LaborLineResponse> laborLines;
    private List<StatusHistoryResponse> statusHistory;
    private BigDecimal totalCost;

    public static WorkOrderResponse from(WorkOrder wo, BigDecimal totalCost) {
        WorkOrderResponse r = new WorkOrderResponse();
        r.id = wo.getId();
        if (wo.getVehicle() != null) {
            r.vehicle = VehicleResponse.from(wo.getVehicle());
        }
        r.technicianId = wo.getTechnician() != null ? wo.getTechnician().getId() : null;
        r.bayId = wo.getBay() != null ? wo.getBay().getId() : null;
        r.status = wo.getStatus();
        r.description = wo.getDescription();
        r.createdAt = wo.getCreatedAt();
        r.partLines = wo.getPartLines().stream().map(PartLineResponse::from).collect(Collectors.toList());
        r.laborLines = wo.getLaborLines().stream().map(LaborLineResponse::from).collect(Collectors.toList());
        r.statusHistory = wo.getStatusHistory().stream().map(StatusHistoryResponse::from).collect(Collectors.toList());
        r.totalCost = totalCost;
        return r;
    }

    public Long getId() { return id; }
    public VehicleResponse getVehicle() { return vehicle; }
    public Long getTechnicianId() { return technicianId; }
    public Long getBayId() { return bayId; }
    public WorkOrderStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<PartLineResponse> getPartLines() { return partLines; }
    public List<LaborLineResponse> getLaborLines() { return laborLines; }
    public List<StatusHistoryResponse> getStatusHistory() { return statusHistory; }
    public BigDecimal getTotalCost() { return totalCost; }
}
