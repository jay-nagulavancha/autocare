package com.autocare.maintenance.integration;

import com.autocare.maintenance.payload.request.CreateVehicleRequest;
import com.autocare.maintenance.payload.request.UpdateVehicleRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VehicleIntegrationTest extends BaseIntegrationTest {

    private static final String VIN = "1HGBH41JXMN109186";

    // ─── Create ───────────────────────────────────────────────────────────────

    @Test @Order(1)
    void createVehicle_asAdmin_returns201() throws Exception {
        CreateVehicleRequest req = new CreateVehicleRequest();
        req.setVin(VIN);
        req.setMake("Honda");
        req.setModel("Civic");
        req.setYear(2023);
        req.setOwnerUsername("owner_" + RUN_ID);

        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vin").value(VIN))
                .andExpect(jsonPath("$.make").value("Honda"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test @Order(2)
    void createVehicle_duplicateVin_returns409() throws Exception {
        createVehicle(VIN, "owner_" + RUN_ID);

        CreateVehicleRequest req = new CreateVehicleRequest();
        req.setVin(VIN);
        req.setMake("Toyota");
        req.setModel("Camry");
        req.setYear(2022);
        req.setOwnerUsername("other_" + RUN_ID);

        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test @Order(3)
    void createVehicle_invalidVin_returns400() throws Exception {
        CreateVehicleRequest req = new CreateVehicleRequest();
        req.setVin("TOOSHORT");
        req.setMake("Honda");
        req.setModel("Civic");
        req.setYear(2023);
        req.setOwnerUsername("owner_" + RUN_ID);

        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(4)
    void createVehicle_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(5)
    void createVehicle_asCustomer_returns403() throws Exception {
        CreateVehicleRequest req = new CreateVehicleRequest();
        req.setVin(VIN);
        req.setMake("Honda");
        req.setModel("Civic");
        req.setYear(2023);
        req.setOwnerUsername("owner_" + RUN_ID);

        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearerHeader(customerToken("cust_" + RUN_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    @Test @Order(6)
    void getVehicle_existingId_returns200() throws Exception {
        var vehicle = createVehicle(VIN, "owner_" + RUN_ID);

        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.getId())
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vin").value(VIN));
    }

    @Test @Order(7)
    void getVehicle_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/99999")
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isNotFound());
    }

    @Test @Order(8)
    void listVehicles_asAdmin_returnsAll() throws Exception {
        createVehicle(VIN, "owner_" + RUN_ID);
        createVehicle("2T1BURHE0JC043821", "owner2_" + RUN_ID);

        mockMvc.perform(get("/api/v1/vehicles")
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test @Order(9)
    void listVehicles_asCustomer_returnsOnlyOwn() throws Exception {
        String customerUsername = "cust_" + RUN_ID;
        createVehicle(VIN, customerUsername);
        createVehicle("2T1BURHE0JC043821", "other_owner");

        mockMvc.perform(get("/api/v1/vehicles")
                        .header("Authorization", bearerHeader(customerToken(customerUsername))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].vin").value(VIN));
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    @Test @Order(10)
    void updateVehicle_asAdmin_returns200() throws Exception {
        var vehicle = createVehicle(VIN, "owner_" + RUN_ID);

        UpdateVehicleRequest req = new UpdateVehicleRequest();
        req.setMake("Toyota");
        req.setModel("Camry");
        req.setYear(2024);
        req.setOwnerUsername("owner_" + RUN_ID);

        mockMvc.perform(put("/api/v1/vehicles/" + vehicle.getId())
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.make").value("Toyota"))
                .andExpect(jsonPath("$.year").value(2024));
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    @Test @Order(11)
    void deleteVehicle_asAdmin_returns204_andSoftDeletes() throws Exception {
        var vehicle = createVehicle(VIN, "owner_" + RUN_ID);

        mockMvc.perform(delete("/api/v1/vehicles/" + vehicle.getId())
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isNoContent());

        // Should return 404 after soft delete
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.getId())
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isNotFound());
    }
}
