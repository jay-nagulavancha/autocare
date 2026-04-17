package com.autocare.maintenance.integration;

import com.autocare.maintenance.model.Vehicle;
import com.autocare.maintenance.model.WorkOrder;
import com.autocare.maintenance.model.WorkOrderStatus;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkOrderIntegrationTest extends BaseIntegrationTest {

    private static final String VIN = "3VWFE21C04M000001";

    // ─── Create ───────────────────────────────────────────────────────────────

    @Test @Order(1)
    void createWorkOrder_asAdmin_returns201WithStatusOpen() throws Exception {
        Vehicle vehicle = createVehicle(VIN, "owner_" + RUN_ID);

        mockMvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", vehicle.getId(),
                                "description", "Oil change"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test @Order(2)
    void createWorkOrder_unknownVehicle_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", 99999,
                                "description", "Test"
                        ))))
                .andExpect(status().isNotFound());
    }

    @Test @Order(3)
    void createWorkOrder_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    @Test @Order(4)
    void getWorkOrder_existingId_returnsFullDetail() throws Exception {
        Vehicle vehicle = createVehicle(VIN, "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "Brake inspection");

        mockMvc.perform(get("/api/v1/work-orders/" + wo.getId())
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(wo.getId()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.vehicle").isNotEmpty())
                .andExpect(jsonPath("$.vehicle.vin").value(VIN))
                .andExpect(jsonPath("$.partLines").isArray())
                .andExpect(jsonPath("$.laborLines").isArray())
                .andExpect(jsonPath("$.statusHistory").isArray())
                .andExpect(jsonPath("$.totalCost").value(0));
    }

    @Test @Order(5)
    void getWorkOrder_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders/99999")
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isNotFound());
    }

    @Test @Order(6)
    void listWorkOrders_filterByStatus_returnsMatchingOnly() throws Exception {
        Vehicle vehicle = createVehicle(VIN, "owner_" + RUN_ID);
        createWorkOrder(vehicle, "WO 1");
        WorkOrder wo2 = createWorkOrder(vehicle, "WO 2");
        wo2.setStatus(WorkOrderStatus.IN_PROGRESS);
        workOrderRepository.save(wo2);

        mockMvc.perform(get("/api/v1/work-orders?status=OPEN")
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status", everyItem(is("OPEN"))));
    }

    // ─── Status transitions ───────────────────────────────────────────────────

    @Test @Order(7)
    void transitionStatus_openToInProgress_returns200() throws Exception {
        Vehicle vehicle = createVehicle(VIN, "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "Tire rotation");

        mockMvc.perform(patch("/api/v1/work-orders/" + wo.getId() + "/status")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "IN_PROGRESS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test @Order(8)
    void transitionStatus_invalidTransition_returns422() throws Exception {
        Vehicle vehicle = createVehicle(VIN, "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "Test WO");

        // OPEN → COMPLETED is invalid (must go through IN_PROGRESS)
        mockMvc.perform(patch("/api/v1/work-orders/" + wo.getId() + "/status")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "COMPLETED"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test @Order(9)
    void transitionStatus_fullLifecycle_succeeds() throws Exception {
        Vehicle vehicle = createVehicle(VIN, "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "Full lifecycle WO");
        Long id = wo.getId();

        // OPEN → IN_PROGRESS
        mockMvc.perform(patch("/api/v1/work-orders/" + id + "/status")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "IN_PROGRESS"))))
                .andExpect(status().isOk());

        // IN_PROGRESS → COMPLETED
        mockMvc.perform(patch("/api/v1/work-orders/" + id + "/status")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "COMPLETED"))))
                .andExpect(status().isOk());

        // COMPLETED → INVOICED
        mockMvc.perform(patch("/api/v1/work-orders/" + id + "/status")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "INVOICED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVOICED"));
    }

    @Test @Order(10)
    void transitionStatus_recordsStatusHistory() throws Exception {
        Vehicle vehicle = createVehicle(VIN, "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "History test WO");

        mockMvc.perform(patch("/api/v1/work-orders/" + wo.getId() + "/status")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "IN_PROGRESS"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/work-orders/" + wo.getId())
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.statusHistory[0].previousStatus").value("OPEN"))
                .andExpect(jsonPath("$.statusHistory[0].newStatus").value("IN_PROGRESS"));
    }

    // ─── Assign ───────────────────────────────────────────────────────────────

    @Test @Order(11)
    void assignTechnicianAndBay_asAdmin_returns200() throws Exception {
        Vehicle vehicle = createVehicle(VIN, "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "Assignment test");
        var tech = createTechnician("Tech_" + RUN_ID);
        var bay = createBay("Bay_" + RUN_ID);

        mockMvc.perform(put("/api/v1/work-orders/" + wo.getId() + "/assign")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicianId", tech.getId(),
                                "bayId", bay.getId()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianId").value(tech.getId()))
                .andExpect(jsonPath("$.bayId").value(bay.getId()));
    }

    @Test @Order(12)
    void assignTechnician_unknownTechnicianId_returns404() throws Exception {
        Vehicle vehicle = createVehicle(VIN, "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "Bad assign test");
        var bay = createBay("Bay_" + RUN_ID);

        mockMvc.perform(put("/api/v1/work-orders/" + wo.getId() + "/assign")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicianId", 99999,
                                "bayId", bay.getId()
                        ))))
                .andExpect(status().isNotFound());
    }
}
