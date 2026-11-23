package com.danasea.backend.modules.booking.domain.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.PricingUnit;
import com.danasea.backend.modules.service.domain.models.SlotStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotValidationDetails {
    private UUID slotId;
    private UUID serviceId;
    private UUID vendorId;
    private UUID optionId;
    @Builder.Default
    private List<UUID> allowedOptionIds = new ArrayList<>();
    private String serviceName;
    private LocalDate bookingDate;
    private LocalTime bookingTime;
    private Integer capacity;
    private Integer bookedCount;
    private SlotStatus status;
    private BigDecimal price;
    private boolean servicePublished;
    private InventoryType inventoryType;
    private boolean optionsConfigured;

    @Builder.Default
    private List<SlotUnitValidationDetails> units = new ArrayList<>();

    @Builder.Default
    private Map<UUID, ServiceOptionValidationDetails> options = new HashMap<>();

    public boolean isOpen() {
        return SlotStatus.OPEN.equals(this.status);
    }

    public int getAvailableCapacity() {
        int cap = capacity != null ? capacity : 0;
        int booked = bookedCount != null ? bookedCount : 0;
        return Math.max(0, cap - booked);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlotUnitValidationDetails {
        private UUID id;
        private Integer unitNumber;
        private Integer capacity;
        private Integer bookedCount;

        public int getAvailableCapacity() {
            int cap = capacity != null ? capacity : 0;
            int booked = bookedCount != null ? bookedCount : 0;
            return Math.max(0, cap - booked);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceOptionValidationDetails {
        private UUID id;
        private String name;
        private OptionType optionType;
        private PricingUnit pricingUnit;
        private BigDecimal price;
        private Integer maxPaxPerPackage;
        private OptionStatus status;

        public boolean isActive() {
            return OptionStatus.ACTIVE.equals(this.status);
        }

        public boolean isPrivate() {
            return OptionType.PRIVATE.equals(this.optionType);
        }

        public boolean isShared() {
            return OptionType.SHARED.equals(this.optionType);
        }
    }
}
