package com.autocare.maintenance.repository;

import com.autocare.maintenance.model.PartLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartLineRepository extends JpaRepository<PartLine, Long> {
    List<PartLine> findByWorkOrderId(Long workOrderId);
}
