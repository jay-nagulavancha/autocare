package com.autocare.maintenance.payload.response;

import com.autocare.maintenance.model.PartLine;

import java.math.BigDecimal;

public class PartLineResponse {
    private Long id;
    private String partName;
    private Integer quantity;
    private BigDecimal unitCost;

    public static PartLineResponse from(PartLine p) {
        PartLineResponse r = new PartLineResponse();
        r.id = p.getId();
        r.partName = p.getPartName();
        r.quantity = p.getQuantity();
        r.unitCost = p.getUnitCost();
        return r;
    }

    public Long getId() { return id; }
    public String getPartName() { return partName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
}
