package com.autocare.maintenance.payload.request;

import javax.validation.constraints.NotBlank;

public class CreateBayRequest {

    @NotBlank
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
