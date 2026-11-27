package com.danasea.backend.modules.report.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.danasea.backend.modules.report.application.usecases.ExportReportCsvUseCase;
import com.danasea.backend.modules.report.domain.enums.GroupByPeriod;
import com.danasea.backend.modules.report.domain.enums.ReportType;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.infrastructure.csv.Rfc4180Utf8BomCsvExporter;
import com.danasea.backend.modules.report.presentation.controllers.AdminReportController;
import com.danasea.backend.modules.report.presentation.controllers.VendorReportController;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.shared.presentation.GlobalExceptionHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.Collections;
import java.util.List;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@ExtendWith(MockitoExtension.class)
class ReportExportControllerTest {

    private MockMvc adminMockMvc;
    private MockMvc vendorMockMvc;

    @Mock private ExportReportCsvUseCase exportReportCsvUseCase;

    @Mock private VendorInternalApi vendorInternalApi;

    @InjectMocks private AdminReportController adminReportController;

    @InjectMocks private VendorReportController vendorReportController;

    private final UUID vendorUserId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminMockMvc =
                MockMvcBuilders.standaloneSetup(adminReportController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        vendorMockMvc =
                MockMvcBuilders.standaloneSetup(vendorReportController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName(
            "Admin CSV Export trả về đúng Header và 3 byte đầu là UTF-8 BOM (0xEF, 0xBB, 0xBF)")
    void adminExport_ReturnsValidUtf8BomAndHeaders() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        TimeRange range = TimeRange.of(from, to);

        // Tạo dữ liệu CSV mẫu có UTF-8 BOM chuẩn
        String csvBody =
                "Kỳ báo cáo,Tổng giá trị (GMV),Tiền thực thu,Hoa hồng sàn\r\n"
                        + "2026-10-01,10000000,9500000,950000\r\n";
        byte[] bodyBytes = csvBody.getBytes(StandardCharsets.UTF_8);
        byte[] csvWithBom = new byte[3 + bodyBytes.length];
        csvWithBom[0] = (byte) 0xEF;
        csvWithBom[1] = (byte) 0xBB;
        csvWithBom[2] = (byte) 0xBF;
        System.arraycopy(bodyBytes, 0, csvWithBom, 3, bodyBytes.length);

        when(exportReportCsvUseCase.execute(
                        eq(ReportType.REVENUE), eq(range), eq(GroupByPeriod.DAY), eq(null)))
                .thenReturn(csvWithBom);

        MvcResult result =
                adminMockMvc
                        .perform(
                                get("/api/admin/reports/export")
                                        .param("type", "revenue")
                                        .param("from", "2026-10-01")
                                        .param("to", "2026-10-31"))
                        .andExpect(status().isOk())
                        .andExpect(
                                header().string(
                                                HttpHeaders.CONTENT_TYPE,
                                                is("text/csv;charset=UTF-8")))
                        .andExpect(
                                header().string(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                is(
                                                        "attachment;"
                                                            + " filename=\"report-revenue-2026-10-01-2026-10-31.csv\"")))
                        .andReturn();

        byte[] responseBytes = result.getResponse().getContentAsByteArray();
        assertThat(responseBytes).isNotNull();
        assertThat(responseBytes.length).isGreaterThanOrEqualTo(3);

        // Kiểm tra 3 byte mở đầu là chuẩn UTF-8 BOM
        assertThat(responseBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(responseBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(responseBytes[2]).isEqualTo((byte) 0xBF);

        // Kiểm tra giải mã phần thân tiếng Việt không bị lỗi font
        String decodedContent =
                new String(responseBytes, 3, responseBytes.length - 3, StandardCharsets.UTF_8);
        assertThat(decodedContent).contains("Kỳ báo cáo");
        assertThat(decodedContent).contains("Tiền thực thu");
        assertThat(decodedContent).contains("Hoa hồng sàn");
    }

    @Test
    @DisplayName("Vendor CSV Export trả về đúng UTF-8 BOM và bảo đảm Vendor Isolation")
    void vendorExport_ReturnsValidUtf8BomAndHeaders() throws Exception {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        vendorUserId.toString(),
                        "credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(vendorUserId);
        when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 15);
        TimeRange range = TimeRange.of(from, to);

        String csvBody = "Mã đơn,Số lượng,Thực nhận đối tác\r\n2026-10-01,5,4500000\r\n";
        byte[] bodyBytes = csvBody.getBytes(StandardCharsets.UTF_8);
        byte[] csvWithBom = new byte[3 + bodyBytes.length];
        csvWithBom[0] = (byte) 0xEF;
        csvWithBom[1] = (byte) 0xBB;
        csvWithBom[2] = (byte) 0xBF;
        System.arraycopy(bodyBytes, 0, csvWithBom, 3, bodyBytes.length);

        when(exportReportCsvUseCase.execute(
                        eq(ReportType.BOOKINGS), eq(range), eq(GroupByPeriod.DAY), eq(vendorId)))
                .thenReturn(csvWithBom);

        MvcResult result =
                vendorMockMvc
                        .perform(
                                get("/api/vendor/reports/export")
                                        .param("type", "bookings")
                                        .param("from", "2026-10-01")
                                        .param("to", "2026-10-15")
                                        .param("groupBy", "day"))
                        .andExpect(status().isOk())
                        .andExpect(
                                header().string(
                                                HttpHeaders.CONTENT_TYPE,
                                                is("text/csv;charset=UTF-8")))
                        .andExpect(
                                header().string(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                is(
                                                        "attachment;"
                                                            + " filename=\"report-bookings-2026-10-01-2026-10-15.csv\"")))
                        .andReturn();

        byte[] responseBytes = result.getResponse().getContentAsByteArray();
        assertThat(responseBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(responseBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(responseBytes[2]).isEqualTo((byte) 0xBF);

        verify(exportReportCsvUseCase)
                .execute(eq(ReportType.BOOKINGS), eq(range), eq(GroupByPeriod.DAY), eq(vendorId));
    }

    @Test
    @DisplayName(
            "Admin CSV Export với from lớn hơn to (2026-05-10 > 2026-05-01) ném"
                    + " IllegalArgumentException trả về 400 Bad Request")
    void adminExport_FromDateAfterToDate_ReturnsBadRequest() throws Exception {
        adminMockMvc
                .perform(
                        get("/api/admin/reports/export")
                                .param("type", "revenue")
                                .param("from", "2026-05-10")
                                .param("to", "2026-05-01"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("Admin CSV Export với groupBy không hợp lệ (invalid_group) trả về 400 Bad Request")
    void adminExport_InvalidGroupBy_ReturnsBadRequest() throws Exception {
        adminMockMvc
                .perform(
                        get("/api/admin/reports/export")
                                .param("type", "revenue")
                                .param("from", "2026-05-01")
                                .param("to", "2026-05-10")
                                .param("groupBy", "invalid_group"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("Admin CSV Export với type không hợp lệ (unknown_type) trả về 400 Bad Request")
    void adminExport_InvalidReportType_ReturnsBadRequest() throws Exception {
        adminMockMvc
                .perform(
                        get("/api/admin/reports/export")
                                .param("type", "unknown_type")
                                .param("from", "2026-05-01")
                                .param("to", "2026-05-10"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("Admin CSV Export cho bookings và vendors trả về đúng Header và 3 byte UTF-8 BOM")
    void adminExport_BookingsAndVendors_ReturnsValidHeadersAndBom() throws Exception {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 10);
        TimeRange range = TimeRange.of(from, to);

        byte[] fakeBookingsCsv =
                new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'B', 'o', 'o', 'k'};
        when(exportReportCsvUseCase.execute(
                        eq(ReportType.BOOKINGS), eq(range), eq(GroupByPeriod.DAY), eq(null)))
                .thenReturn(fakeBookingsCsv);

        adminMockMvc
                .perform(
                        get("/api/admin/reports/export")
                                .param("type", "bookings")
                                .param("from", "2026-05-01")
                                .param("to", "2026-05-10"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, is("text/csv;charset=UTF-8")))
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_DISPOSITION,
                                        is(
                                                "attachment;"
                                                    + " filename=\"report-bookings-2026-05-01-2026-05-10.csv\"")))
                .andExpect(
                        MockMvcResultMatchers.content()
                                .bytes(fakeBookingsCsv));

        byte[] fakeVendorsCsv =
                new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'V', 'e', 'n', 'd'};
        when(exportReportCsvUseCase.execute(
                        eq(ReportType.VENDORS), eq(range), eq(GroupByPeriod.DAY), eq(null)))
                .thenReturn(fakeVendorsCsv);

        adminMockMvc
                .perform(
                        get("/api/admin/reports/export")
                                .param("type", "vendors")
                                .param("from", "2026-05-01")
                                .param("to", "2026-05-10"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, is("text/csv;charset=UTF-8")))
                .andExpect(
                        header().string(
                                        HttpHeaders.CONTENT_DISPOSITION,
                                        is(
                                                "attachment;"
                                                    + " filename=\"report-vendors-2026-05-01-2026-05-10.csv\"")))
                .andExpect(
                        MockMvcResultMatchers.content()
                                .bytes(fakeVendorsCsv));
    }

    @Test
    @DisplayName(
            "Vendor CSV Export với from lớn hơn to (2026-05-10 > 2026-05-01) ném"
                    + " IllegalArgumentException trả về 400 Bad Request")
    void vendorExport_FromDateAfterToDate_ReturnsBadRequest() throws Exception {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        vendorUserId.toString(),
                        "credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(vendorUserId);
        when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

        vendorMockMvc
                .perform(
                        get("/api/vendor/reports/export")
                                .param("type", "revenue")
                                .param("from", "2026-05-10")
                                .param("to", "2026-05-01"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("INVALID_INPUT"));
    }

    @Test
    @DisplayName(
            "Vendor CSV Export với groupBy không hợp lệ (invalid_group) trả về 400 Bad Request")
    void vendorExport_InvalidGroupBy_ReturnsBadRequest() throws Exception {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        vendorUserId.toString(),
                        "credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(vendorUserId);
        when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

        vendorMockMvc
                .perform(
                        get("/api/vendor/reports/export")
                                .param("type", "revenue")
                                .param("from", "2026-05-01")
                                .param("to", "2026-05-10")
                                .param("groupBy", "invalid_group"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("Vendor CSV Export với type không hợp lệ (unknown_type) trả về 400 Bad Request")
    void vendorExport_InvalidReportType_ReturnsBadRequest() throws Exception {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        vendorUserId.toString(),
                        "credentials",
                        List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(vendorUserId);
        when(vendorInternalApi.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));

        vendorMockMvc
                .perform(
                        get("/api/vendor/reports/export")
                                .param("type", "unknown_type")
                                .param("from", "2026-05-01")
                                .param("to", "2026-05-10"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        MockMvcResultMatchers.jsonPath(
                                        "$.code")
                                .value("INVALID_INPUT"));
    }

    @Test
    @DisplayName(
            "Kiểm tra trực tiếp Rfc4180Utf8BomCsvExporter: 3 byte mở đầu là 0xEF, 0xBB, 0xBF và"
                    + " format chuẩn RFC 4180")
    void directCsvExporter_ValidatesExactThreeByteUtf8BomAndEscaping() {
        Rfc4180Utf8BomCsvExporter exporter = new Rfc4180Utf8BomCsvExporter();
        TimeRange range = TimeRange.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10));

        byte[] revenueBytes =
                exporter.exportRevenueReport(Collections.emptyList(), range);
        byte[] bookingBytes =
                exporter.exportBookingReport(Collections.emptyList(), range);
        byte[] vendorBytes =
                exporter.exportVendorPerformanceReport(Collections.emptyList(), range);

        assertThat(revenueBytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(bookingBytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(vendorBytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);

        // Escape test RFC 4180
        assertThat(Rfc4180Utf8BomCsvExporter.escapeField("Hello, World"))
                .isEqualTo("\"Hello, World\"");
        assertThat(Rfc4180Utf8BomCsvExporter.escapeField("Danasea \"VIP\""))
                .isEqualTo("\"Danasea \"\"VIP\"\"\"");
        assertThat(Rfc4180Utf8BomCsvExporter.escapeField("Line1\nLine2"))
                .isEqualTo("\"Line1\nLine2\"");
        assertThat(Rfc4180Utf8BomCsvExporter.escapeField("NormalText")).isEqualTo("NormalText");
    }
}
