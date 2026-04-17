package com.autocare.maintenance.repository;

import com.autocare.maintenance.model.WorkOrder;
import com.autocare.maintenance.model.WorkOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    @Query("SELECT wo FROM WorkOrder wo WHERE " +
           "(:status IS NULL OR wo.status = :status) AND " +
           "(:vehicleId IS NULL OR wo.vehicle.id = :vehicleId) AND " +
           "(:technicianId IS NULL OR wo.technician.id = :technicianId)")
    Page<WorkOrder> findByFilters(
            @Param("status") WorkOrderStatus status,
            @Param("vehicleId") Long vehicleId,
            @Param("technicianId") Long technicianId,
            Pageable pageable);

    List<WorkOrder> findByTechnicianIdAndStatusNotIn(Long technicianId, List<WorkOrderStatus> statuses);

    long countByTechnicianIdAndStatusNotIn(Long technicianId, List<WorkOrderStatus> statuses);
}
