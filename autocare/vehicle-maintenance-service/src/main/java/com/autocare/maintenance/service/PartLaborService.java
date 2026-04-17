package com.autocare.maintenance.service;

import com.autocare.maintenance.exception.ClosedWorkOrderException;
import com.autocare.maintenance.exception.ResourceNotFoundException;
import com.autocare.maintenance.model.*;
import com.autocare.maintenance.payload.request.AddLaborLineRequest;
import com.autocare.maintenance.payload.request.AddPartLineRequest;
import com.autocare.maintenance.payload.response.LaborLineResponse;
import com.autocare.maintenance.payload.response.PartLineResponse;
import com.autocare.maintenance.repository.LaborLineRepository;
import com.autocare.maintenance.repository.PartLineRepository;
import com.autocare.maintenance.repository.WorkOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class PartLaborService {

    private static final List<WorkOrderStatus> CLOSED_STATUSES =
            Arrays.asList(WorkOrderStatus.COMPLETED, WorkOrderStatus.INVOICED);

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private PartLineRepository partLineRepository;

    @Autowired
    private LaborLineRepository laborLineRepository;

    private WorkOrder getOpenWorkOrder(Long workOrderId) {
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder", workOrderId));
        if (CLOSED_STATUSES.contains(workOrder.getStatus())) {
            throw new ClosedWorkOrderException();
        }
        return workOrder;
    }

    @Transactional
    public PartLineResponse addPartLine(Long workOrderId, AddPartLineRequest request) {
        WorkOrder workOrder = getOpenWorkOrder(workOrderId);
        PartLine partLine = new PartLine();
        partLine.setWorkOrder(workOrder);
        partLine.setPartName(request.getPartName());
        partLine.setQuantity(request.getQuantity());
        partLine.setUnitCost(request.getUnitCost());
        return PartLineResponse.from(partLineRepository.save(partLine));
    }

    @Transactional
    public void removePartLine(Long workOrderId, Long partLineId) {
        getOpenWorkOrder(workOrderId);
        PartLine partLine = partLineRepository.findById(partLineId)
                .orElseThrow(() -> new ResourceNotFoundException("PartLine", partLineId));
        partLineRepository.delete(partLine);
    }

    @Transactional
    public LaborLineResponse addLaborLine(Long workOrderId, AddLaborLineRequest request) {
        WorkOrder workOrder = getOpenWorkOrder(workOrderId);
        LaborLine laborLine = new LaborLine();
        laborLine.setWorkOrder(workOrder);
        laborLine.setDescription(request.getDescription());
        laborLine.setHours(request.getHours());
        laborLine.setRate(request.getRate());
        return LaborLineResponse.from(laborLineRepository.save(laborLine));
    }

    @Transactional
    public void removeLaborLine(Long workOrderId, Long laborLineId) {
        getOpenWorkOrder(workOrderId);
        LaborLine laborLine = laborLineRepository.findById(laborLineId)
                .orElseThrow(() -> new ResourceNotFoundException("LaborLine", laborLineId));
        laborLineRepository.delete(laborLine);
    }
}
