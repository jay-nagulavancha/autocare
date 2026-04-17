package com.autocare.maintenance.payload.response;

import com.autocare.maintenance.model.Bay;

public class BayResponse {
    private Long id;
    private String name;
    private boolean active;
    private boolean available;

    public static BayResponse from(Bay b, boolean available) {
        BayResponse r = new BayResponse();
        r.id = b.getId();
        r.name = b.getName();
        r.active = b.isActive();
        r.available = available;
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public boolean isAvailable() { return available; }
}
