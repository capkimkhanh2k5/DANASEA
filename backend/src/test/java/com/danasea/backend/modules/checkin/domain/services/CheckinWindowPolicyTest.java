package com.danasea.backend.modules.checkin.domain.services;

import com.danasea.backend.configs.properties.CheckinProperties;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.service.domain.models.OperationType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CheckinWindowPolicyTest — Dual Operation Check-in Window & Late Policy Verification")
class CheckinWindowPolicyTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private CheckinWindowPolicy policy;

    @BeforeEach
    void setUp() {
        CheckinProperties properties = new CheckinProperties(
                "secret-for-test-32bytes-padding!",
                Duration.ofHours(2), // opensBefore
                Duration.ofMinutes(15) // closesAfter
        );
        policy = new CheckinWindowPolicy(properties);
    }

    private ServiceSlotJpaEntity createSlot(LocalDate date, LocalTime start, LocalTime end, SlotStatus status) {
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setDate(date);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setStatus(status);
        return slot;
    }

    @Nested
    @DisplayName("1. Fixed Departure Operations (Chuyến cố định, e.g. Tàu cao tốc / Ca nô)")
    class FixedDepartureTests {

        private final LocalDate date = LocalDate.of(2026, 11, 7);
        private final LocalTime start = LocalTime.of(9, 0);
        private final LocalTime end = LocalTime.of(10, 30);

        @Test
        @DisplayName("Before opening window (>2h before departure) -> Blocked")
        void testBeforeOpen_Throws() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(6, 50)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.FIXED_DEPARTURE, null, now, false))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("Check-in is not open yet");
        }

        @Test
        @DisplayName("On-time check-in window (between 2h before and 30m before) -> Success (isLate=false)")
        void testStandardWindow_Success() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 25)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.FIXED_DEPARTURE, null, now, false);

            assertThat(result.isLate()).isFalse();
            assertThat(result.note()).isNull();
            assertThat(result.standardClosesAt())
                    .isEqualTo(LocalDateTime.of(date, LocalTime.of(8, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime());
            assertThat(result.absoluteClosesAt())
                    .isEqualTo(LocalDateTime.of(date, LocalTime.of(8, 45)).atZone(VIETNAM_ZONE).toOffsetDateTime());
        }

        @Test
        @DisplayName("Late arrival in grace period (between 30m and 15m before departure) -> Valid directly with isLate=true")
        void testLateArrival_WithinGracePeriod_DirectSuccess() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 35)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.FIXED_DEPARTURE, null, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("du di 15 phút");
        }

        @Test
        @DisplayName("Late arrival in grace period with override -> Also allowed with isLate=true")
        void testLateArrival_WithOverride_Success() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 35)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.FIXED_DEPARTURE, null, now, true);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("du di 15 phút");
        }

        @Test
        @DisplayName("Arrival exactly at hard cutoff (start-15m, 08:45:00) -> Still within cutoff with isLate=true")
        void testExactHardCutoff_Success() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 45, 0)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.FIXED_DEPARTURE, null, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("du di 15 phút");
        }

        @Test
        @DisplayName("Arrival 1 second past cutoff (08:45:01) -> Strictly blocked even with override")
        void testOneSecondPastCutoff_ThrowsEvenWithOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 45, 1)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.FIXED_DEPARTURE, null, now, true))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("missed check-in deadline");
        }

        @Test
        @DisplayName("Arrival past hard cutoff (<15m before departure) -> Strictly blocked even with override")
        void testAfterHardCutoff_ThrowsEvenWithOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 50)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.FIXED_DEPARTURE, null, now, true))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("missed check-in deadline");
        }

        @Test
        @DisplayName("Arrival after departure time -> Strictly blocked even with override")
        void testAfterDeparture_ThrowsEvenWithOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(9, 5)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.FIXED_DEPARTURE, null, now, true))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("missed check-in deadline");
        }
    }

    @Nested
    @DisplayName("2. Shared Duration Based Operations (Theo lượt/thời lượng ghép đoàn, e.g. Chèo SUP / Lặn biển)")
    class DurationBasedTests {

        private final LocalDate date = LocalDate.of(2026, 11, 7);
        private final LocalTime start60 = LocalTime.of(14, 0);
        private final LocalTime end60 = LocalTime.of(15, 0); // 60 minutes
        private final LocalTime start30 = LocalTime.of(8, 0);
        private final LocalTime end30 = LocalTime.of(8, 30); // 30 minutes

        @Test
        @DisplayName("60m slot: On-time check-in before T-30m (13:20) -> Success (isLate=false)")
        void test60mSlot_BeforeT30_Success() {
            ServiceSlotJpaEntity slot = createSlot(date, start60, end60, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(13, 20)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.SHARED, 60, now, false);

            assertThat(result.isLate()).isFalse();
            assertThat(result.note()).isNull();
            assertThat(result.standardClosesAt())
                    .isEqualTo(LocalDateTime.of(date, LocalTime.of(13, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime());
            assertThat(result.absoluteClosesAt())
                    .isEqualTo(LocalDateTime.of(date, LocalTime.of(13, 45)).atZone(VIETNAM_ZONE).toOffsetDateTime());
        }

        @Test
        @DisplayName("60m slot: Arrival in grace period (T-30 to T-15, 13:35) -> Valid directly with isLate=true")
        void test60mSlot_GracePeriod_Success() {
            ServiceSlotJpaEntity slot = createSlot(date, start60, end60, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(13, 35)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.SHARED, 60, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("du di 15 phút");
        }

        @Test
        @DisplayName("60m slot: Arrival at exact cutoff T-15 (13:45:00) -> Valid directly with isLate=true")
        void test60mSlot_ExactCutoff_Success() {
            ServiceSlotJpaEntity slot = createSlot(date, start60, end60, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(13, 45, 0)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.SHARED, 60, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("du di 15 phút");
        }

        @Test
        @DisplayName("60m slot: Arrival 1 second past cutoff (13:45:01) -> Strictly blocked even with override")
        void test60mSlot_PastCutoff_ThrowsEvenWithOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start60, end60, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(13, 45, 1)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.DURATION_BASED, OptionType.SHARED, 60, now, true))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("missed check-in deadline");
        }

        @Test
        @DisplayName("60m slot: Arrival during slot (14:10) -> Strictly blocked even with override")
        void test60mSlot_DuringSlot_ThrowsEvenWithOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start60, end60, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(14, 10)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.DURATION_BASED, OptionType.SHARED, 60, now, true))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("missed check-in deadline");
        }

        @Test
        @DisplayName("30m slot: Arrival in grace period (07:40 for 08:00 slot) -> Valid with isLate=true")
        void test30mSlot_GracePeriod_Success() {
            ServiceSlotJpaEntity slot = createSlot(date, start30, end30, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(7, 40)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.SHARED, 30, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("du di 15 phút");
        }

        @Test
        @DisplayName("30m slot: Arrival past cutoff (07:50 for 08:00 slot) -> Strictly blocked even with override")
        void test30mSlot_PastCutoff_ThrowsEvenWithOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start30, end30, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(7, 50)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.DURATION_BASED, OptionType.SHARED, 30, now, true))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("missed check-in deadline");
        }
    }

    @Nested
    @DisplayName("3. Private Duration Based Operations (Thuê riêng SUP/Jetski theo thời lượng)")
    class PrivateDurationBasedTests {

        private final LocalDate date = LocalDate.of(2026, 11, 7);
        private final LocalTime start = LocalTime.of(8, 0);
        private final LocalTime end = LocalTime.of(8, 30); // 30 minutes duration

        @Test
        @DisplayName("Customer arrives 08:00 -> Start of slot -> Allowed on-time")
        void testArrive0800_StartOfSlot_OnTime() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 0)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.PRIVATE, 30, now, false);

            assertThat(result.isLate()).isFalse();
            assertThat(result.note()).isNull();
        }

        @Test
        @DisplayName("Customer arrives 08:10 -> Within grace period (15m) -> Allowed on-time")
        void testArrive0810_OnTime() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 10)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.PRIVATE, 30, now, false);

            assertThat(result.isLate()).isFalse();
            assertThat(result.note()).isNull();
        }

        @Test
        @DisplayName("Customer arrives 08:20 (past 15m grace) -> Allowed directly for remaining 10 mins (isLate=true, no extension)")
        void testArrive0820_AllowedForRemainingDurationWithoutOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 20)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.PRIVATE, 30, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("còn 10 phút, không bù giờ");
            assertThat(result.absoluteClosesAt())
                    .isEqualTo(LocalDateTime.of(date, end).atZone(VIETNAM_ZONE).toOffsetDateTime());
        }

        @Test
        @DisplayName("Customer arrives 08:25 (5 minutes remaining) -> Allowed directly (isLate=true, no override needed)")
        void testArrive0825_5MinsRemaining_AllowedWithoutOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 25)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.PRIVATE, 30, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("còn 5 phút, không bù giờ");
        }

        @Test
        @DisplayName("Customer arrives 08:27 (3 minutes remaining) -> Allowed directly (isLate=true, no override needed)")
        void testArrive0827_3MinsRemaining_AllowedWithoutOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 27)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.PRIVATE, 30, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("còn 3 phút, không bù giờ");
        }

        @Test
        @DisplayName("Customer arrives 08:29 (1 minute remaining) -> Allowed directly (isLate=true, no override needed)")
        void testArrive0829_1MinRemaining_AllowedWithoutOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 29)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.PRIVATE, 30, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("còn 1 phút, không bù giờ");
        }

        @Test
        @DisplayName("Customer arrives 08:29:45 (15 seconds remaining) -> Allowed directly (isLate=true, seconds counted)")
        void testArrive0829_SecondsRemaining_AllowedWithoutOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 29, 45)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.DURATION_BASED, OptionType.PRIVATE, 30, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("còn 15 giây, không bù giờ");
        }

        @Test
        @DisplayName("Customer arrives 08:30:00 (exact slot end) -> Strictly blocked at boundary")
        void testArrive0830_ExactSlotEnd_ThrowsEvenWithOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 30, 0)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.DURATION_BASED, OptionType.PRIVATE, 30, now, true))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("The service slot ended at");
        }

        @Test
        @DisplayName("Customer arrives 08:31 (after slot end) -> Strictly blocked even with override")
        void testArrive0831_PastSlotEnd_ThrowsEvenWithOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 31)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.DURATION_BASED, OptionType.PRIVATE, 30, now, true))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("The service slot ended at");
        }
    }

    @Nested
    @DisplayName("4. Private Fixed Departure Operations (Thuê nguyên chuyến cano/du thuyền)")
    class PrivateFixedDepartureTests {

        private final LocalDate date = LocalDate.of(2026, 11, 7);
        private final LocalTime start = LocalTime.of(9, 0);
        private final LocalTime end = LocalTime.of(10, 30);

        @Test
        @DisplayName("Arrives before start-30m (08:25) -> On-time success")
        void testCharterOnTime_Success() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 25)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.FIXED_DEPARTURE, OptionType.PRIVATE, null, now, false);

            assertThat(result.isLate()).isFalse();
            assertThat(result.note()).isNull();
        }

        @Test
        @DisplayName("Arrives between start-15m and start (08:50) without override -> Allowed directly with remaining duration")
        void testCharterLateArrival_BeforeStart_AllowedWithoutOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 50)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.FIXED_DEPARTURE, OptionType.PRIVATE, null, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("không bù giờ");
            assertThat(result.absoluteClosesAt())
                    .isEqualTo(LocalDateTime.of(date, end).atZone(VIETNAM_ZONE).toOffsetDateTime());
        }

        @Test
        @DisplayName("Arrives after start time (09:30, 60m remaining) without override -> Allowed directly for remaining duration")
        void testCharterArrival_AfterStartBeforeEnd_AllowedWithoutOverride() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(9, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.FIXED_DEPARTURE, OptionType.PRIVATE, null, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("còn 60 phút, không bù giờ");
        }

        @Test
        @DisplayName("Arrives 15 seconds before slot end (10:29:45) -> Allowed for remaining 15 seconds")
        void testCharterArrival_SecondsRemaining_Success() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(10, 29, 45)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            var result = policy.validate(slot, OperationType.FIXED_DEPARTURE, OptionType.PRIVATE, null, now, false);

            assertThat(result.isLate()).isTrue();
            assertThat(result.note()).contains("còn 15 giây, không bù giờ");
        }

        @Test
        @DisplayName("Arrives at exact slot end (10:30:00) -> Strictly blocked")
        void testCharterArrival_ExactSlotEnd_Throws() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(10, 30, 0)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.FIXED_DEPARTURE, OptionType.PRIVATE, null, now, true))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("The service slot ended at");
        }

        @Test
        @DisplayName("Arrives after slot end (10:35) -> Strictly blocked")
        void testCharterArrival_PastSlotEnd_Throws() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.OPEN);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(10, 35)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.FIXED_DEPARTURE, OptionType.PRIVATE, null, now, true))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("The service slot ended at");
        }

        @Test
        @DisplayName("Blocked slot -> Strictly blocked")
        void testCharterBlockedSlot_Throws() {
            ServiceSlotJpaEntity slot = createSlot(date, start, end, SlotStatus.BLOCKED);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(8, 25)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, OperationType.FIXED_DEPARTURE, OptionType.PRIVATE, null, now, false))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("cancelled or blocked");
        }
    }

    @Nested
    @DisplayName("5. Edge Cases & Boundary Conditions")
    class EdgeCases {

        @Test
        @DisplayName("Cancelled slot -> Strictly throws")
        void testCancelledSlot_Throws() {
            LocalDate date = LocalDate.of(2026, 11, 7);
            ServiceSlotJpaEntity slot = createSlot(date, LocalTime.of(10, 0), LocalTime.of(11, 0),
                    SlotStatus.CANCELLED);
            OffsetDateTime now = LocalDateTime.of(date, LocalTime.of(9, 30)).atZone(VIETNAM_ZONE).toOffsetDateTime();

            assertThatThrownBy(() -> policy.validate(slot, now))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("departure was cancelled");
        }

        @Test
        @DisplayName("Incomplete slot data -> Throws")
        void testIncompleteData_Throws() {
            ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
            OffsetDateTime now = OffsetDateTime.now();

            assertThatThrownBy(() -> policy.validate(slot, now))
                    .isInstanceOf(InvalidCheckinStateException.class)
                    .hasMessageContaining("incomplete check-in timing data");
        }
    }
}
