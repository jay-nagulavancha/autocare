package com.autocare.maintenance.service;

import com.autocare.maintenance.exception.ResourceNotFoundException;
import com.autocare.maintenance.model.Bay;
import com.autocare.maintenance.payload.request.CreateBayRequest;
import com.autocare.maintenance.payload.response.BayResponse;
import com.autocare.maintenance.repository.BayRepository;
import com.autocare.maintenance.repository.ServiceScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BayService {

    @Autowired
    private BayRepository bayRepository;

    @Autowired
    private ServiceScheduleRepository scheduleRepository;

    public List<BayResponse> listBays() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoHoursLater = now.plusHours(2);
        return bayRepository.findByActiveTrue().stream()
                .map(bay -> {
                    boolean hasConflict = !scheduleRepository
                            .findByBayIdAndStatusAndScheduledAtBetween(bay.getId(), "CONFIRMED", now, twoHoursLater)
                            .isEmpty();
                    return BayResponse.from(bay, !hasConflict);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public BayResponse createBay(CreateBayRequest request) {
        Bay bay = new Bay();
        bay.setName(request.getName());
        return BayResponse.from(bayRepository.save(bay), true);
    }

    @Transactional
    public BayResponse updateBay(Long id, CreateBayRequest request) {
        Bay bay = bayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bay", id));
        bay.setName(request.getName());
        return BayResponse.from(bayRepository.save(bay), true);
    }

    @Transactional
    public void deactivateBay(Long id) {
        Bay bay = bayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bay", id));
        bay.setActive(false);
        bayRepository.save(bay);
    }
}
