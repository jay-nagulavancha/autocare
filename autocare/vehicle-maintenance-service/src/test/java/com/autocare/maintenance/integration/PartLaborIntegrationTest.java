package com.autocare.maintenance.integration;

import com.autocare.maintenance.model.Vehicle;
import com.autocare.maintenance.model.WorkOrder;
import com.autocare.maintenance.model.WorkOrderStatus;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PartLaborIntegrationTest extends BaseIntegrationTest {

    @Test @Order(1)
    void addPartLine_toOpenWorkOrder_returns201() throws Exception {
        Vehicle vehicle = createVehicle("1HGBH41JXMN100001", "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "Parts test");

        mockMvc.perform(post("/api/v1/work-orders/" + wo.getId() + "/parts")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "partName", "Oil filter",
                                "quantity", 1,
                                "unitCost", 12.99
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partName").value("Oil filter"));
    }

    @Test @Order(2)
    void addLaborLine_toOpenWorkOrder_returns201() throws Exception {
        Vehicle vehicle = createVehicle("1HGBH41JXMN100002", "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "Labor test");

        mockMvc.perform(post("/api/v1/work-orders/" + wo.getId() + "/labor")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Oil change labor",
                                "hours", 0.5,
                                "rate", 80.00
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Oil change labor"));
    }

    @Test @Order(3)
    void addPartLine_toCompletedWorkOrder_returns422() throws Exception {
        Vehicle vehicle = createVehicle("1HGBH41JXMN100003", "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "Closed WO test");
        wo.setStatus(WorkOrderStatus.COMPLETED);
        workOrderRepository.save(wo);

        mockMvc.perform(post("/api/v1/work-orders/" + wo.getId() + "/parts")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "partName", "Brake pad",
                                "quantity", 2,
                                "unitCost", 45.00
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Cannot modify line items on a closed work order"));
    }

    @Test @Order(4)
    void totalCost_calculatedCorrectly() throws Exception {
        Vehicle vehicle = createVehicle("1HGBH41JXMN100004", "owner_" + RUN_ID);
        WorkOrder wo = createWorkOrder(vehicle, "Cost calc test");

        // Add part: 2 × 10.00 = 20.00
        mockMvc.perform(post("/api/v1/work-orders/" + wo.getId() + "/parts")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "partName", "Part A",
                                "quantity", 2,
                                "unitCost", 10.00
                        ))))
                .andExpect(status().isCreated());

        // Add labor: 1.5 × 80.00 = 120.00
        mockMvc.perform(post("/api/v1/work-orders/" + wo.getId() + "/labor")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Labor A",
                                "hours", 1.5,
                                "rate", 80.00
                        ))))
                .andExpect(status().isCreated());

        // Total = 20.00 + 120.00 = 140.00
        mockMvc.perform(get("/api/v1/work-orders/" + wo.getId())
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(140.00));
    }
}
