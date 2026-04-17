package com.autocare.maintenance.service;

import com.autocare.maintenance.exception.ResourceNotFoundException;
import com.autocare.maintenance.model.Technician;
import com.autocare.maintenance.model.WorkOrderStatus;
import com.autocare.maintenance.payload.request.CreateTechnicianRequest;
import com.autocare.maintenance.payload.response.TechnicianResponse;
import com.autocare.maintenance.repository.TechnicianRepository;
import com.autocare.maintenance.repository.WorkOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TechnicianService {

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    public List<TechnicianResponse> listTechnicians() {
        return technicianRepository.findByActiveTrue().stream()
                .map(t -> TechnicianResponse.from(t,
                        workOrderRepository.countByTechnicianIdAndStatusNotIn(t.getId(),
                                Arrays.asList(WorkOrderStatus.COMPLETED, WorkOrderStatus.INVOICED))))
                .collect(Collectors.toList());
    }

    @Transactional
    public TechnicianResponse createTechnician(CreateTechnicianRequest request) {
        Technician technician = new Technician();
        technician.setName(request.getName());
        return TechnicianResponse.from(technicianRepository.save(technician), 0);
    }

    @Transactional
    public TechnicianResponse updateTechnician(Long id, CreateTechnicianRequest request) {
        Technician technician = technicianRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Technician", id));
        technician.setName(request.getName());
        return TechnicianResponse.from(technicianRepository.save(technician),
                workOrderRepository.countByTechnicianIdAndStatusNotIn(id,
                        Arrays.asList(WorkOrderStatus.COMPLETED, WorkOrderStatus.INVOICED)));
    }

    @Transactional
    public void deactivateTechnician(Long id) {
        Technician technician = technicianRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Technician", id));
        technician.setActive(false);
        technicianRepository.save(technician);
    }
}
