package com.autocare.maintenance.repository;

import com.autocare.maintenance.model.ServiceSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ServiceScheduleRepository extends JpaRepository<ServiceSchedule, Long> {
    List<ServiceSchedule> findByBayIdAndStatusAndScheduledAtBetween(
            Long bayId, String status, LocalDateTime start, LocalDateTime end);
    Page<ServiceSchedule> findByVehicleId(Long vehicleId, Pageable pageable);
    Page<ServiceSchedule> findByScheduledAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
