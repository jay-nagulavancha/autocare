package com.autocare.maintenance.repository;

import com.autocare.maintenance.model.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByVin(String vin);
    List<Vehicle> findByOwnerUsernameAndDeletedFalse(String ownerUsername);
    Page<Vehicle> findAllByDeletedFalse(Pageable pageable);
    Page<Vehicle> findAllByDeletedFalseAndOwnerUsername(Pageable pageable, String ownerUsername);
}
