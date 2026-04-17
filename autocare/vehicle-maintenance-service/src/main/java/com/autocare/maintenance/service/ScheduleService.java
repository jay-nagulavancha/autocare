package com.autocare.maintenance.service;

import com.autocare.maintenance.exception.BayConflictException;
import com.autocare.maintenance.exception.ResourceNotFoundException;
import com.autocare.maintenance.model.Bay;
import com.autocare.maintenance.model.ServiceSchedule;
import com.autocare.maintenance.model.Vehicle;
import com.autocare.maintenance.payload.request.CreateScheduleRequest;
import com.autocare.maintenance.payload.response.ScheduleResponse;
import com.autocare.maintenance.repository.BayRepository;
import com.autocare.maintenance.repository.ServiceScheduleRepository;
import com.autocare.maintenance.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ScheduleService {

    @Autowired
    private ServiceScheduleRepository scheduleRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private BayRepository bayRepository;

    @Autowired
    private BayConflictChecker bayConflictChecker;

    @Transactional
    public ScheduleResponse createSchedule(CreateScheduleRequest request, String actorUsername) {
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .filter(v -> !v.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", request.getVehicleId()));

        Bay bay = null;
        if (request.getBayId() != null) {
            bay = bayRepository.findById(request.getBayId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bay", request.getBayId()));

            if (bayConflictChecker.hasConflict(request.getBayId(), request.getScheduledAt(), null, scheduleRepository)) {
                throw new BayConflictException();
            }
        }

        ServiceSchedule schedule = new ServiceSchedule();
        schedule.setVehicle(vehicle);
        schedule.setBay(bay);
        schedule.setScheduledAt(request.getScheduledAt());
        schedule.setServiceType(request.getServiceType());
        schedule.setStatus("CONFIRMED");

        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    public Page<ScheduleResponse> listSchedules(Pageable pageable, LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null) {
            return scheduleRepository.findByScheduledAtBetween(start, end, pageable)
                    .map(ScheduleResponse::from);
        }
        return scheduleRepository.findAll(pageable).map(ScheduleResponse::from);
    }

    public Page<ScheduleResponse> listSchedulesByVehicle(Long vehicleId, Pageable pageable) {
        vehicleRepository.findById(vehicleId)
                .filter(v -> !v.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId));
        return scheduleRepository.findByVehicleId(vehicleId, pageable).map(ScheduleResponse::from);
    }

    @Transactional
    public void cancelSchedule(Long id) {
        ServiceSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id));
        schedule.setStatus("CANCELLED");
        scheduleRepository.save(schedule);
    }
}
