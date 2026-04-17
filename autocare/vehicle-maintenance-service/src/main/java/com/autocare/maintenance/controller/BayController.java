package com.autocare.maintenance.controller;

import com.autocare.maintenance.payload.request.CreateBayRequest;
import com.autocare.maintenance.payload.response.BayResponse;
import com.autocare.maintenance.service.BayService;
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
@RequestMapping("/api/v1/bays")
@Tag(name = "Bays", description = "Bay management endpoints")
public class BayController {

    @Autowired
    private BayService bayService;

    @GetMapping
    @Operation(summary = "List bays with availability status")
    public ResponseEntity<List<BayResponse>> listBays() {
        return ResponseEntity.ok(bayService.listBays());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new bay")
    @ApiResponse(responseCode = "201", description = "Created")
    public ResponseEntity<BayResponse> createBay(@Valid @RequestBody CreateBayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bayService.createBay(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update bay")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    public ResponseEntity<BayResponse> updateBay(@PathVariable Long id,
                                                   @Valid @RequestBody CreateBayRequest request) {
        return ResponseEntity.ok(bayService.updateBay(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate bay")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deactivated"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    public ResponseEntity<Void> deactivateBay(@PathVariable Long id) {
        bayService.deactivateBay(id);
        return ResponseEntity.noContent().build();
    }
}
