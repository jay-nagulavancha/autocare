package com.autocare.maintenance.service;

import com.autocare.maintenance.model.ServiceSchedule;
import com.autocare.maintenance.repository.ServiceScheduleRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BayConflictChecker {

    /**
     * Returns true if the proposed time conflicts with any CONFIRMED schedule on the same bay.
     * Conflict window: |proposed - existing| < 2 hours (symmetric).
     *
     * @param bayId              the bay to check
     * @param proposed           the proposed schedule time
     * @param excludeScheduleId  schedule id to exclude (for updates), null if not applicable
     * @param repo               the repository to query
     */
    public boolean hasConflict(Long bayId, LocalDateTime proposed, Long excludeScheduleId,
                               ServiceScheduleRepository repo) {
        LocalDateTime windowStart = proposed.minusHours(2);
        LocalDateTime windowEnd = proposed.plusHours(2);

        List<ServiceSchedule> conflicts = repo.findByBayIdAndStatusAndScheduledAtBetween(
                bayId, "CONFIRMED", windowStart, windowEnd);

        for (ServiceSchedule schedule : conflicts) {
            if (excludeScheduleId != null && schedule.getId().equals(excludeScheduleId)) {
                continue;
            }
            long diffMinutes = Math.abs(
                    java.time.Duration.between(proposed, schedule.getScheduledAt()).toMinutes());
            if (diffMinutes < 120) {
                return true;
            }
        }
        return false;
    }
}
