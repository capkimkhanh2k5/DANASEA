package com.danasea.backend.modules.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.danasea.backend.modules.report.domain.models.TimeRange;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import java.time.Duration;

@DisplayName("TimeRange Adversarial & Boundary Stress Test Suite")
class TimeRangeAdversarialTest {

    private static final ZoneOffset VN_OFFSET = ZoneOffset.ofHours(7);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private TimeZone originalDefaultTimeZone;

    @BeforeEach
    void setUp() {
        originalDefaultTimeZone = TimeZone.getDefault();
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalDefaultTimeZone);
    }

    @Nested
    @DisplayName("1. Nanosecond & UTC+7 Timezone Precision Verification")
    class TimezonePrecisionTests {

        @Test
        @DisplayName(
                "StartDateTime phải chính xác 00:00:00.000000000 và EndDateTime phải chính xác"
                        + " 23:59:59.999999999 với offset +07:00")
        void shouldVerifyExactNanosecondBoundaries() {
            LocalDate date = LocalDate.of(2026, 10, 8);
            TimeRange range = TimeRange.of(date, date);

            OffsetDateTime start = range.getStartDateTime();
            OffsetDateTime end = range.getEndDateTime();

            // Kiểm chứng startDateTime
            assertThat(start.getOffset()).isEqualTo(VN_OFFSET);
            assertThat(start.getHour()).isZero();
            assertThat(start.getMinute()).isZero();
            assertThat(start.getSecond()).isZero();
            assertThat(start.getNano()).isZero();
            assertThat(start.toLocalTime()).isEqualTo(LocalTime.MIN);

            // Kiểm chứng endDateTime
            assertThat(end.getOffset()).isEqualTo(VN_OFFSET);
            assertThat(end.getHour()).isEqualTo(23);
            assertThat(end.getMinute()).isEqualTo(59);
            assertThat(end.getSecond()).isEqualTo(59);
            assertThat(end.getNano()).isEqualTo(999_999_999);
            assertThat(end.toLocalTime()).isEqualTo(LocalTime.MAX);

            // Kiểm chứng khoảng nano giữa start và end trong 1 ngày là đúng 86,400,000,000,000 - 1
            // ns
            long nanosDifference = Duration.between(start, end).toNanos();
            assertThat(nanosDifference).isEqualTo(86_399_999_999_999L);
        }

        @Test
        @DisplayName(
                "Chuyển đổi sang UTC Instant: mốc 00:00:00+07:00 tương ứng 17:00:00Z ngày hôm"
                        + " trước")
        void shouldMatchAccurateUtcInstantEquivalence() {
            LocalDate date = LocalDate.of(2026, 10, 1);
            TimeRange range = TimeRange.of(date, date);

            Instant startInstant = range.getStartDateTime().toInstant();
            Instant endInstant = range.getEndDateTime().toInstant();

            // 2026-10-01 00:00:00+07:00 == 2026-09-30 17:00:00Z
            assertThat(startInstant).isEqualTo(Instant.parse("2026-09-30T17:00:00.000000000Z"));

            // 2026-10-01 23:59:59.999999999+07:00 == 2026-10-01 16:59:59.999999999Z
            assertThat(endInstant).isEqualTo(Instant.parse("2026-10-01T16:59:59.999999999Z"));
        }

        @Test
        @DisplayName("Xác minh contains() với các mốc thời gian UTC (Z) ở ranh giới 1 nanosecond")
        void shouldHandleUtcTimestampsAcrossOneNanosecondBoundary() {
            LocalDate date = LocalDate.of(2026, 10, 1);
            TimeRange range = TimeRange.of(date, date);

            // 1 ns trước khi ngày bắt đầu ở UTC: 2026-09-30 16:59:59.999999999Z -> ngoài khoảng
            OffsetDateTime justBeforeStartUtc =
                    OffsetDateTime.parse("2026-09-30T16:59:59.999999999Z");
            assertThat(range.contains(justBeforeStartUtc)).isFalse();

            // Đúng điểm bắt đầu ở UTC: 2026-09-30 17:00:00.000000000Z -> trong khoảng
            OffsetDateTime exactStartUtc = OffsetDateTime.parse("2026-09-30T17:00:00.000000000Z");
            assertThat(range.contains(exactStartUtc)).isTrue();

            // Đúng điểm kết thúc ở UTC: 2026-10-01 16:59:59.999999999Z -> trong khoảng
            OffsetDateTime exactEndUtc = OffsetDateTime.parse("2026-10-01T16:59:59.999999999Z");
            assertThat(range.contains(exactEndUtc)).isTrue();

            // 1 ns sau khi ngày kết thúc ở UTC: 2026-10-01 17:00:00.000000000Z -> ngoài khoảng
            OffsetDateTime justAfterEndUtc = OffsetDateTime.parse("2026-10-01T17:00:00.000000000Z");
            assertThat(range.contains(justAfterEndUtc)).isFalse();
        }

        @Test
        @DisplayName(
                "Xác minh contains() với các múi giờ khác nhau trên thế giới (Tokyo +09:00, New"
                        + " York -04:00)")
        void shouldHandleMultiTimezoneOffsetsCorrectly() {
            LocalDate date = LocalDate.of(2026, 10, 1);
            TimeRange range = TimeRange.of(date, date);

            // Tokyo (UTC+9): 2026-10-01 02:00:00+09:00 == 2026-10-01 00:00:00+07:00 -> TRUE
            OffsetDateTime tokyoStart =
                    OffsetDateTime.of(2026, 10, 1, 2, 0, 0, 0, ZoneOffset.ofHours(9));
            assertThat(range.contains(tokyoStart)).isTrue();

            // New York EDT (UTC-4): 2026-09-30 13:00:00-04:00 == 2026-10-01 00:00:00+07:00 -> TRUE
            OffsetDateTime nyStart =
                    OffsetDateTime.of(2026, 9, 30, 13, 0, 0, 0, ZoneOffset.ofHours(-4));
            assertThat(range.contains(nyStart)).isTrue();

            // London BST (UTC+1): 2026-10-01 17:59:59.999999999+01:00 == 2026-10-01
            // 23:59:59.999999999+07:00 -> TRUE
            OffsetDateTime londonEnd =
                    OffsetDateTime.of(2026, 10, 1, 17, 59, 59, 999_999_999, ZoneOffset.ofHours(1));
            assertThat(range.contains(londonEnd)).isTrue();

            // London BST (UTC+1): 2026-10-01 18:00:00.000000000+01:00 == 2026-10-02 00:00:00+07:00
            // -> FALSE
            OffsetDateTime londonPast =
                    OffsetDateTime.of(2026, 10, 1, 18, 0, 0, 0, ZoneOffset.ofHours(1));
            assertThat(range.contains(londonPast)).isFalse();
        }
    }

    @Nested
    @DisplayName("2. Leap Year (29/02) Boundary Stress Tests")
    class LeapYearBoundaryTests {

        @Test
        @DisplayName("TimeRange cho đúng ngày nhuận 29/02/2024 (from = to = 2024-02-29)")
        void shouldHandleSingleLeapDayCorrectly() {
            LocalDate leapDay = LocalDate.of(2024, 2, 29);
            TimeRange range = TimeRange.of(leapDay, leapDay);

            assertThat(range.getDaysCount()).isEqualTo(1);
            assertThat(range.getFrom()).isEqualTo(leapDay);
            assertThat(range.getTo()).isEqualTo(leapDay);
            assertThat(range.getStartDateTime())
                    .isEqualTo(OffsetDateTime.of(2024, 2, 29, 0, 0, 0, 0, VN_OFFSET));
            assertThat(range.getEndDateTime())
                    .isEqualTo(OffsetDateTime.of(2024, 2, 29, 23, 59, 59, 999_999_999, VN_OFFSET));

            assertThat(range.contains(LocalDate.of(2024, 2, 28))).isFalse();
            assertThat(range.contains(LocalDate.of(2024, 2, 29))).isTrue();
            assertThat(range.contains(LocalDate.of(2024, 3, 1))).isFalse();
        }

        @Test
        @DisplayName(
                "Khoảng thời gian bắc qua ngày nhuận 28/02 đến 01/03 trong năm nhuận 2024: 3 ngày")
        void shouldHandleRangeAcrossLeapDayInLeapYear() {
            LocalDate from = LocalDate.of(2024, 2, 28);
            LocalDate to = LocalDate.of(2024, 3, 1);
            TimeRange range = TimeRange.of(from, to);

            assertThat(range.getDaysCount()).isEqualTo(3);
            assertThat(range.contains(LocalDate.of(2024, 2, 28))).isTrue();
            assertThat(range.contains(LocalDate.of(2024, 2, 29))).isTrue();
            assertThat(range.contains(LocalDate.of(2024, 3, 1))).isTrue();
            assertThat(range.contains(LocalDate.of(2024, 2, 27))).isFalse();
            assertThat(range.contains(LocalDate.of(2024, 3, 2))).isFalse();
        }

        @Test
        @DisplayName("Khoảng thời gian 28/02 đến 01/03 trong năm thường 2025: 2 ngày")
        void shouldHandleRangeAcrossEndOfFebruaryInNonLeapYear() {
            LocalDate from = LocalDate.of(2025, 2, 28);
            LocalDate to = LocalDate.of(2025, 3, 1);
            TimeRange range = TimeRange.of(from, to);

            assertThat(range.getDaysCount()).isEqualTo(2);
            assertThat(range.contains(LocalDate.of(2025, 2, 28))).isTrue();
            assertThat(range.contains(LocalDate.of(2025, 3, 1))).isTrue();
            assertThat(range.contains(LocalDate.of(2025, 2, 27))).isFalse();
            assertThat(range.contains(LocalDate.of(2025, 3, 2))).isFalse();
        }

        @Test
        @DisplayName("Trọn vẹn năm nhuận 2024 (01/01/2024 đến 31/12/2024): 366 ngày")
        void shouldCountExactly366DaysInFullLeapYear() {
            TimeRange range = TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            assertThat(range.getDaysCount()).isEqualTo(366);
        }

        @Test
        @DisplayName("Trọn vẹn năm thường 2025 (01/01/2025 đến 31/12/2025): 365 ngày")
        void shouldCountExactly365DaysInFullNonLeapYear() {
            TimeRange range = TimeRange.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
            assertThat(range.getDaysCount()).isEqualTo(365);
        }

        @Test
        @DisplayName("Năm thế kỷ nhuận 2000 (chia hết cho 400): 29/02/2000 hợp lệ")
        void shouldSupportCenturyLeapYear2000() {
            LocalDate leap2000 = LocalDate.of(2000, 2, 29);
            TimeRange range = TimeRange.of(leap2000, leap2000);
            assertThat(range.getDaysCount()).isEqualTo(1);
            assertThat(range.contains(leap2000)).isTrue();
        }
    }

    @Nested
    @DisplayName("3. Year-End Transition (31/12 sang 01/01) Stress Tests")
    class YearEndTransitionTests {

        @Test
        @DisplayName("Giao thừa chuyển năm: 31/12/2025 đến 01/01/2026 gồm 2 ngày")
        void shouldHandleNewYearEveTransition() {
            LocalDate from = LocalDate.of(2025, 12, 31);
            LocalDate to = LocalDate.of(2026, 1, 1);
            TimeRange range = TimeRange.of(from, to);

            assertThat(range.getDaysCount()).isEqualTo(2);
            assertThat(range.getFrom()).isEqualTo(from);
            assertThat(range.getTo()).isEqualTo(to);

            // Ranh giới thời gian tại giao thừa
            OffsetDateTime newYearEveMoment =
                    OffsetDateTime.of(2025, 12, 31, 23, 59, 59, 999_999_999, VN_OFFSET);
            OffsetDateTime happyNewYearMoment =
                    OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, VN_OFFSET);

            assertThat(range.contains(newYearEveMoment)).isTrue();
            assertThat(range.contains(happyNewYearMoment)).isTrue();

            // Trước và sau ranh giới
            OffsetDateTime before = range.getStartDateTime().minusNanos(1);
            OffsetDateTime after = range.getEndDateTime().plusNanos(1);
            assertThat(range.contains(before)).isFalse();
            assertThat(range.contains(after)).isFalse();
        }

        @Test
        @DisplayName(
                "Khoảng chuyển giao xuyên 3 năm: 31/12/2023 đến 01/01/2025 (bao gồm cả năm nhuận"
                        + " 2024)")
        void shouldHandleMultiYearRangeIncludingLeapYear() {
            LocalDate from = LocalDate.of(2023, 12, 31);
            LocalDate to = LocalDate.of(2025, 1, 1);
            TimeRange range = TimeRange.of(from, to);

            // 1 ngày (2023) + 366 ngày (2024) + 1 ngày (2025) = 368 ngày
            assertThat(range.getDaysCount()).isEqualTo(368);
            assertThat(range.contains(LocalDate.of(2023, 12, 31))).isTrue();
            assertThat(range.contains(LocalDate.of(2024, 2, 29))).isTrue();
            assertThat(range.contains(LocalDate.of(2025, 1, 1))).isTrue();
            assertThat(range.contains(LocalDate.of(2023, 12, 30))).isFalse();
            assertThat(range.contains(LocalDate.of(2025, 1, 2))).isFalse();
        }
    }

    @Nested
    @DisplayName("4. Single Day Boundary Tests (from == to)")
    class SingleDayBoundaryTests {

        @ParameterizedTest
        @ValueSource(
                strings = {"2026-01-01", "2026-06-15", "2026-10-08", "2026-12-31", "2024-02-29"})
        @DisplayName("Bất kỳ ngày đơn lẻ nào đều phải có daysCount = 1 và start/end cùng ngày")
        void shouldHandleArbitrarySingleDayDates(String dateStr) {
            LocalDate date = LocalDate.parse(dateStr);
            TimeRange range = TimeRange.of(date, date);

            assertThat(range.getDaysCount()).isEqualTo(1);
            assertThat(range.getFrom()).isEqualTo(date);
            assertThat(range.getTo()).isEqualTo(date);
            assertThat(range.getStartDateTime().toLocalDate()).isEqualTo(date);
            assertThat(range.getEndDateTime().toLocalDate()).isEqualTo(date);
            assertThat(range.getStartDateTime().toLocalTime()).isEqualTo(LocalTime.MIN);
            assertThat(range.getEndDateTime().toLocalTime()).isEqualTo(LocalTime.MAX);

            assertThat(range.contains(date)).isTrue();
            assertThat(range.contains(date.minusDays(1))).isFalse();
            assertThat(range.contains(date.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("Kiểm tra ranh giới 1 nanosecond cho ngày đơn lẻ")
        void shouldTestNanosecondPrecisionForSingleDay() {
            LocalDate day = LocalDate.of(2026, 7, 20);
            TimeRange range = TimeRange.of(day, day);

            OffsetDateTime start = range.getStartDateTime();
            OffsetDateTime end = range.getEndDateTime();

            assertThat(range.contains(start)).isTrue();
            assertThat(range.contains(end)).isTrue();
            assertThat(range.contains(start.minusNanos(1))).isFalse();
            assertThat(range.contains(end.plusNanos(1))).isFalse();
        }
    }

    @Nested
    @DisplayName("5. Exception Handling Tests (from > to và null checks)")
    class ExceptionHandlingTests {

        @ParameterizedTest
        @CsvSource({
            "2026-10-09, 2026-10-08, 'Chênh lệch 1 ngày'",
            "2026-11-01, 2026-10-31, 'Chênh lệch tháng'",
            "2027-01-01, 2026-12-31, 'Chênh lệch năm'",
            "2024-03-01, 2024-02-29, 'Chênh lệch sau ngày nhuận'",
            "2026-01-01, 2025-12-31, 'Giao thừa nghịch đảo'"
        })
        @DisplayName("Ném IllegalArgumentException khi from > to")
        void shouldThrowIllegalArgumentExceptionWhenFromIsAfterTo(
                String fromStr, String toStr, String description) {
            LocalDate from = LocalDate.parse(fromStr);
            LocalDate to = LocalDate.parse(toStr);

            assertThatThrownBy(() -> TimeRange.of(from, to))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("From date cannot be after to date");
        }

        @Test
        @DisplayName("Ném IllegalArgumentException khi một hoặc cả hai tham số null")
        void shouldThrowIllegalArgumentExceptionWhenParametersAreNull() {
            LocalDate valid = LocalDate.of(2026, 10, 8);

            assertThatThrownBy(() -> TimeRange.of(null, valid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("From date and to date cannot be null");

            assertThatThrownBy(() -> TimeRange.of(valid, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("From date and to date cannot be null");

            assertThatThrownBy(() -> TimeRange.of(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("From date and to date cannot be null");
        }
    }

    @Nested
    @DisplayName("6. Environment Resilience (JVM Default TimeZone Independence)")
    class EnvironmentResilienceTests {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "UTC",
                    "America/New_York",
                    "America/Los_Angeles",
                    "Europe/London",
                    "Asia/Tokyo",
                    "Pacific/Auckland",
                    "Pacific/Honolulu"
                })
        @DisplayName(
                "TimeRange phải luôn luôn sinh ra múi giờ UTC+7 (+07:00) bất kể TimeZone mặc định"
                        + " của JVM bị đổi")
        void shouldAlwaysProduceUtcPlus7RegardlessOfJvmDefaultTimeZone(String zoneId) {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId));

            LocalDate from = LocalDate.of(2026, 5, 1);
            LocalDate to = LocalDate.of(2026, 5, 31);
            TimeRange range = TimeRange.of(from, to);

            assertThat(range.getStartDateTime().getOffset())
                    .as("Offset của startDateTime phải luôn luôn là +07:00")
                    .isEqualTo(VN_OFFSET);

            assertThat(range.getEndDateTime().getOffset())
                    .as("Offset của endDateTime phải luôn luôn là +07:00")
                    .isEqualTo(VN_OFFSET);

            assertThat(range.getStartDateTime().getHour()).isZero();
            assertThat(range.getEndDateTime().getHour()).isEqualTo(23);
            assertThat(range.getEndDateTime().getNano()).isEqualTo(999_999_999);
            assertThat(range.getDaysCount()).isEqualTo(31);
        }
    }

    @Nested
    @DisplayName("7. Scale & Extreme Century Stress Tests")
    class ScaleStressTests {

        @Test
        @DisplayName("Khoảng 100 năm (2000-01-01 đến 2099-12-31): tính toán chính xác 36,525 ngày")
        void shouldHandleCenturyRangeAccurately() {
            LocalDate startCentury = LocalDate.of(2000, 1, 1);
            LocalDate endCentury = LocalDate.of(2099, 12, 31);

            assertThatThrownBy(() -> TimeRange.of(startCentury, endCentury))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("3660 days");
        }

        @Test
        @DisplayName("Toàn vẹn equals, hashCode và toString của TimeRange")
        void shouldMaintainContractIntegrity() {
            LocalDate d1 = LocalDate.of(2026, 1, 1);
            LocalDate d2 = LocalDate.of(2026, 1, 2);

            TimeRange r1 = TimeRange.of(d1, d2);
            TimeRange r2 = TimeRange.of(d1, d2);
            TimeRange r3 = TimeRange.of(d1, d1);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
            assertThat(r1).isNotEqualTo(r3);
            assertThat(r1.toString()).contains("2026-01-01").contains("2026-01-02");
        }
    }
}
