package com.autocare.maintenance.controller;

import com.autocare.maintenance.model.WorkOrderStatus;
import com.autocare.maintenance.payload.request.*;
import com.autocare.maintenance.payload.response.LaborLineResponse;
import com.autocare.maintenance.payload.response.PartLineResponse;
import com.autocare.maintenance.payload.response.WorkOrderResponse;
import com.autocare.maintenance.service.PartLaborService;
import com.autocare.maintenance.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/work-orders")
@Tag(name = "Work Orders", description = "Work order management endpoints")
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private PartLaborService partLaborService;

    @GetMapping
    @Operation(summary = "List work orders with optional filters")
    public ResponseEntity<Page<WorkOrderResponse>> listWorkOrders(
            Pageable pageable,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long technicianId) {
        return ResponseEntity.ok(workOrderService.listWorkOrders(pageable, status, vehicleId, technicianId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Create a new work order")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found")
    })
    public ResponseEntity<WorkOrderResponse> createWorkOrder(@Valid @RequestBody CreateWorkOrderRequest request,
                                                              Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workOrderService.createWorkOrder(request, auth.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get work order by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    public ResponseEntity<WorkOrderResponse> getWorkOrder(@PathVariable Long id) {
        return ResponseEntity.ok(workOrderService.getWorkOrder(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Transition work order status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "422", description = "Invalid transition")
    })
    public ResponseEntity<WorkOrderResponse> transitionStatus(@PathVariable Long id,
                                                               @Valid @RequestBody StatusTransitionRequest request,
                                                               Authentication auth) {
        return ResponseEntity.ok(workOrderService.transitionStatus(id, request.getTargetStatus(), auth.getName()));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign technician and bay to work order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assigned"),
            @ApiResponse(responseCode = "404", description = "Resource not found")
    })
    public ResponseEntity<WorkOrderResponse> assignTechnicianAndBay(@PathVariable Long id,
                                                                      @Valid @RequestBody AssignRequest request) {
        return ResponseEntity.ok(workOrderService.assignTechnicianAndBay(id, request));
    }

    // Parts endpoints
    @PostMapping("/{id}/parts")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Add part line to work order")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Part added"),
            @ApiResponse(responseCode = "422", description = "Work order is closed")
    })
    public ResponseEntity<PartLineResponse> addPartLine(@PathVariable Long id,
                                                         @Valid @RequestBody AddPartLineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partLaborService.addPartLine(id, request));
    }

    @DeleteMapping("/{id}/parts/{partLineId}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Remove part line from work order")
    @ApiResponse(responseCode = "204", description = "Removed")
    public ResponseEntity<Void> removePartLine(@PathVariable Long id, @PathVariable Long partLineId) {
        partLaborService.removePartLine(id, partLineId);
        return ResponseEntity.noContent().build();
    }

    // Labor endpoints
    @PostMapping("/{id}/labor")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Add labor line to work order")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Labor added"),
            @ApiResponse(responseCode = "422", description = "Work order is closed")
    })
    public ResponseEntity<LaborLineResponse> addLaborLine(@PathVariable Long id,
                                                           @Valid @RequestBody AddLaborLineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partLaborService.addLaborLine(id, request));
    }

    @DeleteMapping("/{id}/labor/{laborLineId}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "Remove labor line from work order")
    @ApiResponse(responseCode = "204", description = "Removed")
    public ResponseEntity<Void> removeLaborLine(@PathVariable Long id, @PathVariable Long laborLineId) {
        partLaborService.removeLaborLine(id, laborLineId);
        return ResponseEntity.noContent().build();
    }
}
