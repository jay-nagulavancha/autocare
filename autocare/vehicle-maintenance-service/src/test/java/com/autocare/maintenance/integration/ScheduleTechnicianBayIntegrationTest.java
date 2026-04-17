package com.autocare.maintenance.integration;

import com.autocare.maintenance.model.Bay;
import com.autocare.maintenance.model.ServiceSchedule;
import com.autocare.maintenance.model.Vehicle;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScheduleTechnicianBayIntegrationTest extends BaseIntegrationTest {

    // ─── Schedules ────────────────────────────────────────────────────────────

    @Test @Order(1)
    void createSchedule_withoutBay_returns201() throws Exception {
        Vehicle vehicle = createVehicle("1HGBH41JXMN200001", "owner_" + RUN_ID);
        String scheduledAt = LocalDateTime.now().plusDays(1)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", vehicle.getId(),
                                "scheduledAt", scheduledAt,
                                "serviceType", "Oil Change"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceType").value("Oil Change"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test @Order(2)
    void createSchedule_withBay_returns201() throws Exception {
        Vehicle vehicle = createVehicle("1HGBH41JXMN200002", "owner_" + RUN_ID);
        Bay bay = createBay("Bay_" + RUN_ID);
        String scheduledAt = LocalDateTime.now().plusDays(2)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", vehicle.getId(),
                                "scheduledAt", scheduledAt,
                                "serviceType", "Tire Rotation",
                                "bayId", bay.getId()
                        ))))
                .andExpect(status().isCreated());
    }

    @Test @Order(3)
    void createSchedule_bayConflictWithin2Hours_returns409() throws Exception {
        Vehicle vehicle = createVehicle("1HGBH41JXMN200003", "owner_" + RUN_ID);
        Bay bay = createBay("ConflictBay_" + RUN_ID);
        LocalDateTime baseTime = LocalDateTime.now().plusDays(3).withMinute(0).withSecond(0).withNano(0);

        // First booking
        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", vehicle.getId(),
                                "scheduledAt", baseTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                "serviceType", "Service A",
                                "bayId", bay.getId()
                        ))))
                .andExpect(status().isCreated());

        // Second booking 1 hour later — within 2-hour window → conflict
        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", vehicle.getId(),
                                "scheduledAt", baseTime.plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                "serviceType", "Service B",
                                "bayId", bay.getId()
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bay is not available for the requested time slot"));
    }

    @Test @Order(4)
    void createSchedule_bayNoConflictAfter2Hours_returns201() throws Exception {
        Vehicle vehicle = createVehicle("1HGBH41JXMN200004", "owner_" + RUN_ID);
        Bay bay = createBay("NoBayConflict_" + RUN_ID);
        LocalDateTime baseTime = LocalDateTime.now().plusDays(4).withMinute(0).withSecond(0).withNano(0);

        // First booking
        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", vehicle.getId(),
                                "scheduledAt", baseTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                "serviceType", "Service A",
                                "bayId", bay.getId()
                        ))))
                .andExpect(status().isCreated());

        // Second booking 3 hours later — outside 2-hour window → no conflict
        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", vehicle.getId(),
                                "scheduledAt", baseTime.plusHours(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                "serviceType", "Service B",
                                "bayId", bay.getId()
                        ))))
                .andExpect(status().isCreated());
    }

    @Test @Order(5)
    void listSchedulesByVehicle_returnsCorrectSchedules() throws Exception {
        Vehicle vehicle = createVehicle("1HGBH41JXMN200005", "owner_" + RUN_ID);
        String scheduledAt = LocalDateTime.now().plusDays(5)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", vehicle.getId(),
                                "scheduledAt", scheduledAt,
                                "serviceType", "Inspection"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/schedules?vehicleId=" + vehicle.getId())
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test @Order(6)
    void cancelSchedule_returns204() throws Exception {
        Vehicle vehicle = createVehicle("1HGBH41JXMN200006", "owner_" + RUN_ID);
        String scheduledAt = LocalDateTime.now().plusDays(6)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String response = mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "vehicleId", vehicle.getId(),
                                "scheduledAt", scheduledAt,
                                "serviceType", "Checkup"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long scheduleId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/v1/schedules/" + scheduleId)
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isNoContent());
    }

    // ─── Technicians ──────────────────────────────────────────────────────────

    @Test @Order(7)
    void listTechnicians_returnsActiveTechnicians() throws Exception {
        createTechnician("Tech1_" + RUN_ID);
        createTechnician("Tech2_" + RUN_ID);

        mockMvc.perform(get("/api/v1/technicians")
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test @Order(8)
    void createTechnician_asAdmin_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/technicians")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "NewTech_" + RUN_ID
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("NewTech_" + RUN_ID));
    }

    @Test @Order(9)
    void createTechnician_asCustomer_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/technicians")
                        .header("Authorization", bearerHeader(customerToken("cust_" + RUN_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "ShouldFail_" + RUN_ID
                        ))))
                .andExpect(status().isForbidden());
    }

    // ─── Bays ─────────────────────────────────────────────────────────────────

    @Test @Order(10)
    void listBays_returnsActiveBays() throws Exception {
        createBay("Bay1_" + RUN_ID);
        createBay("Bay2_" + RUN_ID);

        mockMvc.perform(get("/api/v1/bays")
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test @Order(11)
    void createBay_asAdmin_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/bays")
                        .header("Authorization", bearerHeader(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "NewBay_" + RUN_ID
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("NewBay_" + RUN_ID));
    }

    @Test @Order(12)
    void deactivateBay_asAdmin_returns204() throws Exception {
        Bay bay = createBay("DeactivateBay_" + RUN_ID);

        mockMvc.perform(delete("/api/v1/bays/" + bay.getId())
                        .header("Authorization", bearerHeader(adminToken())))
                .andExpect(status().isNoContent());
    }
}
