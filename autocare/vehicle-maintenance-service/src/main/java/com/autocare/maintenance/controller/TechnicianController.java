package com.autocare.maintenance.controller;

import com.autocare.maintenance.payload.request.CreateTechnicianRequest;
import com.autocare.maintenance.payload.response.TechnicianResponse;
import com.autocare.maintenance.service.TechnicianService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/technicians")
@Tag(name = "Technicians", description = "Technician management endpoints")
public class TechnicianController {

    @Autowired
    private TechnicianService technicianService;

    @GetMapping
    @Operation(summary = "List active technicians with work order count")
    public ResponseEntity<List<TechnicianResponse>> listTechnicians() {
        return ResponseEntity.ok(technicianService.listTechnicians());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new technician")
    @ApiResponse(responseCode = "201", description = "Created")
    public ResponseEntity<TechnicianResponse> createTechnician(@Valid @RequestBody CreateTechnicianRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(technicianService.createTechnician(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update technician")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    public ResponseEntity<TechnicianResponse> updateTechnician(@PathVariable Long id,
                                                                @Valid @RequestBody CreateTechnicianRequest request) {
        return ResponseEntity.ok(technicianService.updateTechnician(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate technician")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deactivated"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    public ResponseEntity<Void> deactivateTechnician(@PathVariable Long id) {
        technicianService.deactivateTechnician(id);
        return ResponseEntity.noContent().build();
    }
}
