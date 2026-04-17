package com.autocare.maintenance.service;

import com.autocare.maintenance.exception.InvalidStatusTransitionException;
import com.autocare.maintenance.exception.ResourceNotFoundException;
import com.autocare.maintenance.model.*;
import com.autocare.maintenance.payload.request.AssignRequest;
import com.autocare.maintenance.payload.request.CreateWorkOrderRequest;
import com.autocare.maintenance.payload.response.WorkOrderResponse;
import com.autocare.maintenance.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class WorkOrderService {

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private BayRepository bayRepository;

    @Autowired
    private WorkOrderStateMachine stateMachine;

    @Autowired
    private CostCalculator costCalculator;

    @Transactional
    public WorkOrderResponse createWorkOrder(CreateWorkOrderRequest request, String actorUsername) {
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .filter(v -> !v.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", request.getVehicleId()));

        WorkOrder workOrder = new WorkOrder();
        workOrder.setVehicle(vehicle);
        workOrder.setDescription(request.getDescription());
        workOrder.setStatus(WorkOrderStatus.OPEN);

        WorkOrder saved = workOrderRepository.save(workOrder);
        return WorkOrderResponse.from(saved, costCalculator.calculate(saved.getPartLines(), saved.getLaborLines()));
    }

    public WorkOrderResponse getWorkOrder(Long id) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder", id));
        return WorkOrderResponse.from(workOrder,
                costCalculator.calculate(workOrder.getPartLines(), workOrder.getLaborLines()));
    }

    public Page<WorkOrderResponse> listWorkOrders(Pageable pageable, WorkOrderStatus status,
                                                   Long vehicleId, Long technicianId) {
        return workOrderRepository.findByFilters(status, vehicleId, technicianId, pageable)
                .map(wo -> WorkOrderResponse.from(wo,
                        costCalculator.calculate(wo.getPartLines(), wo.getLaborLines())));
    }

    @Transactional
    public WorkOrderResponse transitionStatus(Long id, WorkOrderStatus target, String actorUsername) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder", id));

        WorkOrderStatus current = workOrder.getStatus();
        if (!stateMachine.isValidTransition(current, target)) {
            throw new InvalidStatusTransitionException(current, target);
        }

        WorkOrderStatusHistory history = new WorkOrderStatusHistory();
        history.setWorkOrder(workOrder);
        history.setPreviousStatus(current);
        history.setNewStatus(target);
        history.setChangedBy(actorUsername);
        workOrder.getStatusHistory().add(history);
        workOrder.setStatus(target);

        WorkOrder saved = workOrderRepository.save(workOrder);
        return WorkOrderResponse.from(saved,
                costCalculator.calculate(saved.getPartLines(), saved.getLaborLines()));
    }

    @Transactional
    public WorkOrderResponse assignTechnicianAndBay(Long id, AssignRequest request) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder", id));

        Technician technician = technicianRepository.findById(request.getTechnicianId())
                .orElseThrow(() -> new ResourceNotFoundException("Technician", request.getTechnicianId()));

        Bay bay = bayRepository.findById(request.getBayId())
                .orElseThrow(() -> new ResourceNotFoundException("Bay", request.getBayId()));

        workOrder.setTechnician(technician);
        workOrder.setBay(bay);

        WorkOrder saved = workOrderRepository.save(workOrder);
        return WorkOrderResponse.from(saved,
                costCalculator.calculate(saved.getPartLines(), saved.getLaborLines()));
    }

    public void checkTechnicianAccess(WorkOrder workOrder, String actorUsername, String role) {
        if ("ROLE_TECHNICIAN".equals(role)) {
            if (workOrder.getTechnician() == null ||
                    !workOrder.getTechnician().getName().equals(actorUsername)) {
                throw new AccessDeniedException("Access denied: work order not assigned to you");
            }
        }
    }
}
