package com.autocare.auth.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/version")
public class VersionController {

    @Value("${autocare.service-id}")
    private String serviceId;

    @Value("${autocare.build.version}")
    private String version;

    @Value("${autocare.build.git-commit}")
    private String gitCommit;

    @GetMapping
    public Map<String, String> version() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("service", serviceId);
        body.put("version", version);
        body.put("gitCommit", gitCommit);
        return body;
    }
}
