package com.autocare.maintenance.payload.response;

import com.autocare.maintenance.model.LaborLine;

import java.math.BigDecimal;

public class LaborLineResponse {
    private Long id;
    private String description;
    private BigDecimal hours;
    private BigDecimal rate;

    public static LaborLineResponse from(LaborLine l) {
        LaborLineResponse r = new LaborLineResponse();
        r.id = l.getId();
        r.description = l.getDescription();
        r.hours = l.getHours();
        r.rate = l.getRate();
        return r;
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public BigDecimal getHours() { return hours; }
    public BigDecimal getRate() { return rate; }
}
