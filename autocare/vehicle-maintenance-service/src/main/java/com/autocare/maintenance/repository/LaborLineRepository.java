package com.autocare.maintenance.repository;

import com.autocare.maintenance.model.LaborLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LaborLineRepository extends JpaRepository<LaborLine, Long> {
    List<LaborLine> findByWorkOrderId(Long workOrderId);
}
