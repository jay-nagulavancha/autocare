package com.autocare.maintenance.model;

import javax.persistence.*;
import javax.validation.constraints.DecimalMin;
import java.math.BigDecimal;

@Entity
@Table(name = "labor_lines")
public class LaborLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Column(nullable = false)
    private String description;

    @DecimalMin("0.01")
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal hours;

    @DecimalMin("0.01")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    // EI_EXPOSE_REP/EI_EXPOSE_REP2: WorkOrder is a JPA-managed entity reference;
    // defensive copying would break proxy identity, lazy-loading, and dirty-tracking.
    @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
    public WorkOrder getWorkOrder() { return workOrder; }
    @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
    public void setWorkOrder(WorkOrder workOrder) { this.workOrder = workOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getHours() { return hours; }
    public void setHours(BigDecimal hours) { this.hours = hours; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
}
