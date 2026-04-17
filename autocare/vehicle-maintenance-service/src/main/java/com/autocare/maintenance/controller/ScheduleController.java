package com.autocare.maintenance.controller;

import com.autocare.maintenance.payload.request.CreateScheduleRequest;
import com.autocare.maintenance.payload.response.ScheduleResponse;
import com.autocare.maintenance.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/schedules")
@Tag(name = "Schedules", description = "Service schedule management endpoints")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    @GetMapping
    @Operation(summary = "List schedules with optional date range and vehicle filter")
    public ResponseEntity<Page<ScheduleResponse>> listSchedules(
            Pageable pageable,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        if (vehicleId != null) {
            return ResponseEntity.ok(scheduleService.listSchedulesByVehicle(vehicleId, pageable));
        }
        return ResponseEntity.ok(scheduleService.listSchedules(pageable, start, end));
    }

    @PostMapping
    @Operation(summary = "Create a new service schedule")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found"),
            @ApiResponse(responseCode = "409", description = "Bay conflict")
    })
    public ResponseEntity<ScheduleResponse> createSchedule(@Valid @RequestBody CreateScheduleRequest request,
                                                             Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduleService.createSchedule(request, auth.getName()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a schedule")
    @ApiResponse(responseCode = "204", description = "Cancelled")
    public ResponseEntity<Void> cancelSchedule(@PathVariable Long id) {
        scheduleService.cancelSchedule(id);
        return ResponseEntity.noContent().build();
    }
}
