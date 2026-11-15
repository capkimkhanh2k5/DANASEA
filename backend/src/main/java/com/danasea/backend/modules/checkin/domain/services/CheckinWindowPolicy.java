package com.danasea.backend.modules.checkin.domain.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.danasea.backend.configs.properties.CheckinProperties;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.service.domain.models.OperationType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;

@Component
public class CheckinWindowPolicy {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CheckinProperties properties;

    public CheckinWindowPolicy(CheckinProperties properties) {
        this.properties = properties;
    }

    public record CheckinValidationResult(
            boolean isLate,
            OffsetDateTime opensAt,
            OffsetDateTime standardClosesAt,
            OffsetDateTime absoluteClosesAt,
            String note) {
    }

    public CheckinValidationResult validate(ServiceSlotJpaEntity slot, OffsetDateTime now) {
        return validate(slot, OperationType.FIXED_DEPARTURE, OptionType.SHARED, null, now, false);
    }

    public CheckinValidationResult validate(
            ServiceSlotJpaEntity slot,
            OperationType operationType,
            Integer durationMinutes,
            OffsetDateTime now,
            boolean allowLateOverride) {
        return validate(slot, operationType, OptionType.SHARED, durationMinutes, now, allowLateOverride);
    }

    public CheckinValidationResult validate(
            ServiceSlotJpaEntity slot,
            OperationType operationType,
            OptionType optionType,
            Integer durationMinutes,
            OffsetDateTime now,
            boolean allowLateOverride) {
        if (slot == null || slot.getDate() == null || slot.getStartTime() == null || slot.getEndTime() == null) {
            throw new InvalidCheckinStateException("The service slot has incomplete check-in timing data.");
        }
        if (slot.getStatus() == SlotStatus.CANCELLED || slot.getStatus() == SlotStatus.BLOCKED) {
            throw new InvalidCheckinStateException("This departure was cancelled or blocked.");
        }

        OperationType opType = operationType != null ? operationType : OperationType.FIXED_DEPARTURE;
        OptionType optType = optionType != null ? optionType : OptionType.SHARED;

        OffsetDateTime start = LocalDateTime.of(slot.getDate(), slot.getStartTime())
                .atZone(VIETNAM_ZONE).toOffsetDateTime();
        OffsetDateTime end = LocalDateTime.of(
                slot.getEndTime().isBefore(slot.getStartTime()) ? slot.getDate().plusDays(1) : slot.getDate(),
                slot.getEndTime())
                .atZone(VIETNAM_ZONE).toOffsetDateTime();

        // 1. Mở check-in: start - opensBefore (mặc định 2 giờ)
        OffsetDateTime opensAt = start.minus(properties.opensBefore());
        if (now.isBefore(opensAt)) {
            throw new InvalidCheckinStateException("Check-in is not open yet. It opens at " + opensAt + ".");
        }

        boolean isLate = false;
        String note = null;
        OffsetDateTime standardClosesAt;
        OffsetDateTime absoluteClosesAt;

        if (optType == OptionType.SHARED) {
            // TẤT CẢ DỊCH VỤ GHÉP ĐOÀN (SHARED) - CẢ FIXED_DEPARTURE VÀ DURATION_BASED:
            // Theo quyết định PO:
            // - Mở check-in từ start - 2h.
            // - Mốc có mặt chuẩn: start - 30m.
            // - Du di thêm 15 phút: (start - 30m, start - 15m] -> Check-in hợp lệ trực tiếp, isLate = true.
            // - Deadline cuối: start - 15m.
            // - Quá deadline (now > start - 15m): TỪ CHỐI TUYỆT ĐỐI, dù còn thời gian trong ca.
            //   Không chấp nhận allowLateOverride sau cutoff.
            standardClosesAt = start.minusMinutes(30);
            absoluteClosesAt = start.minusMinutes(15);

            if (now.isAfter(absoluteClosesAt)) {
                throw new InvalidCheckinStateException(
                        String.format("Check-in is closed at %s (15 minutes before scheduled start %s). Customer missed check-in deadline. Please contact hotline / vendor.",
                                absoluteClosesAt, start));
            } else if (now.isAfter(standardClosesAt)) {
                isLate = true;
                note = "Khách check-in trong khoảng du di 15 phút trước hạn chót.";
            }
        } else {
            // DỊCH VỤ THUÊ RIÊNG (PRIVATE) - CẢ FIXED_DEPARTURE VÀ DURATION_BASED:
            // Theo quyết định PO:
            // - Chuyến thuê riêng có thể xuất phát linh hoạt trong thời gian đặt.
            // - Khách không phải xin override chỉ vì đến muộn.
            // - Nếu now < bookedSlotEnd (end), ca còn hợp lệ -> cho Check-in.
            // - Chỉ được sử dụng thời gian còn lại của ca đã đặt, không bù giờ/gia hạn.
            // - now >= bookedSlotEnd (end) -> từ chối tuyệt đối.
            if (opType == OperationType.FIXED_DEPARTURE) {
                standardClosesAt = start.minusMinutes(30);
            } else {
                long slotDuration = durationMinutes != null && durationMinutes > 0
                        ? durationMinutes
                        : Duration.between(start, end).toMinutes();
                if (slotDuration <= 0) {
                    slotDuration = 30; // fallback
                }
                long graceMinutes = Math.min(15, Math.max(1, slotDuration / 2));
                standardClosesAt = start.plusMinutes(graceMinutes);
            }
            absoluteClosesAt = end;

            if (!now.isBefore(absoluteClosesAt)) {
                throw new InvalidCheckinStateException(
                        String.format("The service slot ended at %s. Check-in is permanently closed.", end));
            }

            if (now.isAfter(standardClosesAt)) {
                isLate = true;
                Duration remainingTime = Duration.between(now, end);
                long remainingMinutes = remainingTime.toMinutes();
                if (remainingMinutes > 0) {
                    note = String.format("Khách thuê riêng đến trễ, được sử dụng thời lượng còn lại đến %s (còn %d phút, không bù giờ).",
                            end.toLocalTime(), remainingMinutes);
                } else {
                    note = String.format("Khách thuê riêng đến trễ, được sử dụng thời lượng còn lại đến %s (còn %d giây, không bù giờ).",
                            end.toLocalTime(), remainingTime.getSeconds());
                }
            }
        }

        return new CheckinValidationResult(isLate, opensAt, standardClosesAt, absoluteClosesAt, note);
    }
}
