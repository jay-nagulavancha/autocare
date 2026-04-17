package com.autocare.maintenance.service;

import com.autocare.maintenance.exception.DuplicateVinException;
import com.autocare.maintenance.exception.ResourceNotFoundException;
import com.autocare.maintenance.model.Vehicle;
import com.autocare.maintenance.payload.request.CreateVehicleRequest;
import com.autocare.maintenance.payload.request.UpdateVehicleRequest;
import com.autocare.maintenance.payload.response.VehicleResponse;
import com.autocare.maintenance.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request, String actorUsername) {
        if (vehicleRepository.findByVin(request.getVin()).isPresent()) {
            throw new DuplicateVinException(request.getVin());
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setVin(request.getVin());
        vehicle.setMake(request.getMake());
        vehicle.setModel(request.getModel());
        vehicle.setYear(request.getYear());
        vehicle.setOwnerUsername(request.getOwnerUsername());
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    public VehicleResponse getVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .filter(v -> !v.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id));
        return VehicleResponse.from(vehicle);
    }

    public Page<VehicleResponse> listVehicles(Pageable pageable, String authenticatedUsername, String role) {
        if ("ROLE_CUSTOMER".equals(role)) {
            return vehicleRepository.findAllByDeletedFalseAndOwnerUsername(pageable, authenticatedUsername)
                    .map(VehicleResponse::from);
        }
        return vehicleRepository.findAllByDeletedFalse(pageable).map(VehicleResponse::from);
    }

    @Transactional
    public VehicleResponse updateVehicle(Long id, UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .filter(v -> !v.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id));
        if (request.getMake() != null) vehicle.setMake(request.getMake());
        if (request.getModel() != null) vehicle.setModel(request.getModel());
        if (request.getYear() != null) vehicle.setYear(request.getYear());
        if (request.getOwnerUsername() != null) vehicle.setOwnerUsername(request.getOwnerUsername());
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .filter(v -> !v.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id));
        vehicle.setDeleted(true);
        vehicleRepository.save(vehicle);
    }
}
