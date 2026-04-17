package com.autocare.maintenance.repository;

import com.autocare.maintenance.model.Bay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BayRepository extends JpaRepository<Bay, Long> {
    List<Bay> findByActiveTrue();
}
