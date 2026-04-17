package com.autocare.maintenance.service;

import com.autocare.maintenance.model.LaborLine;
import com.autocare.maintenance.model.PartLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

@Component
public class CostCalculator {

    public BigDecimal calculate(Collection<PartLine> parts, Collection<LaborLine> labors) {
        BigDecimal total = BigDecimal.ZERO;

        if (parts != null) {
            for (PartLine part : parts) {
                total = total.add(
                        new BigDecimal(part.getQuantity()).multiply(part.getUnitCost()));
            }
        }

        if (labors != null) {
            for (LaborLine labor : labors) {
                total = total.add(labor.getHours().multiply(labor.getRate()));
            }
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
