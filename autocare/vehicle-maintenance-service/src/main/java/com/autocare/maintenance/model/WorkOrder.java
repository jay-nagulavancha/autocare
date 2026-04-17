package com.autocare.maintenance.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "work_orders")
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "technician_id")
    private Technician technician;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bay_id")
    private Bay bay;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private WorkOrderStatus status = WorkOrderStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<PartLine> partLines = new LinkedHashSet<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<LaborLine> laborLines = new LinkedHashSet<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<WorkOrderStatusHistory> statusHistory = new LinkedHashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    public Technician getTechnician() { return technician; }
    public void setTechnician(Technician technician) { this.technician = technician; }
    public Bay getBay() { return bay; }
    public void setBay(Bay bay) { this.bay = bay; }
    public WorkOrderStatus getStatus() { return status; }
    public void setStatus(WorkOrderStatus status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Set<PartLine> getPartLines() { return partLines; }
    public void setPartLines(Set<PartLine> partLines) { this.partLines = partLines; }
    public Set<LaborLine> getLaborLines() { return laborLines; }
    public void setLaborLines(Set<LaborLine> laborLines) { this.laborLines = laborLines; }
    public Set<WorkOrderStatusHistory> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(Set<WorkOrderStatusHistory> statusHistory) { this.statusHistory = statusHistory; }
}
