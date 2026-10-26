package com.danasea.backend.modules.flashsale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.danasea.backend.modules.booking.application.usecases.CreateBookingHoldUseCase;
import com.danasea.backend.modules.flashsale.application.FlashSaleService;
import com.danasea.backend.modules.flashsale.application.FlashSaleService.Terms;
import com.danasea.backend.modules.flashsale.domain.FlashSaleException;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.domain.ports.OrderEventPublisherPort;
import com.danasea.backend.modules.order.infrastructure.adapters.CommissionPolicyAdapter;
import com.danasea.backend.modules.settlement.domain.models.SubOrderCalculationContext;
import com.danasea.backend.modules.settlement.domain.services.SettlementCalculationEngine;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaSystemConfigRepository;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.autoconfigure.exclude=",
    "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.listener.direct.auto-startup=false",
    "spring.data.redis.database=15", "app.scheduler.enabled=false", "management.health.mail.enabled=false"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "FLASH_SALE_TEST_JDBC_URL", matches = ".*/danasea_flashsale_test")
class FlashSaleCommissionUnificationIntegrationTest {

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("FLASH_SALE_TEST_JDBC_URL"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> System.getenv("FLASH_SALE_TEST_DB_PASSWORD"));
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.data.redis.host", () -> "localhost");
        r.add("spring.data.redis.port", () -> 6380);
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate db;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JwtTokenProvider jwt;

    @Autowired
    FlashSaleService flashSaleService;

    @Autowired
    CommissionPolicyAdapter commissionPolicyAdapter;

    @Autowired
    JpaSystemConfigRepository systemConfigRepository;

    @Autowired
    SettlementCalculationEngine settlementEngine;

    @MockitoBean
    OrderEventPublisherPort orderEvents;

    @MockitoBean
    org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;

    @MockitoBean
    org.springframework.mail.javamail.JavaMailSender mail;

    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    UUID adminId, customerId, vendorUserId, vendorId, serviceId, optionId, slotId;
    String adminToken, customerToken;

    static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    @BeforeEach
    void setup() {
        assertThat(db.queryForObject("SELECT current_database()", String.class)).isEqualTo("danasea_flashsale_test");
        flashSaleService.setClock(Clock.systemUTC());

        // Wipe tables cleanly
        db.execute("TRUNCATE users,vendors,categories,services,service_options,service_slots,service_slot_units,bookings,booking_items,booking_item_allocations,master_orders,sub_orders,flash_sales,vendor_commission_policies,flash_sale_allocations,flash_sale_hold_requests CASCADE");
        db.update("DELETE FROM system_configs WHERE key IN ('PLATFORM_COMMISSION_RATE','FLASH_SALE_MAX_SUBSIDY_RATIO','FLASH_SALE_ESTIMATED_UNIT_COST','FLASH_SALE_MINIMUM_UNIT_MARGIN')");

        // Verify Redis logical database 15 and flush DB 15 cleanly for test isolation
        if (redis.getConnectionFactory() instanceof org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory lettuce) {
            assertThat(lettuce.getDatabase()).isEqualTo(15);
        }
        redis.execute((org.springframework.data.redis.connection.RedisConnection conn) -> {
            conn.serverCommands().flushDb();
            return null;
        });

        adminId = user("ADMIN");
        customerId = user("CUSTOMER");
        vendorUserId = user("VENDOR");
        vendorId = UUID.randomUUID();

        db.update("INSERT INTO vendors(id,user_id,business_name,verification_status,created_at,updated_at) VALUES (?,?,'TEST_VENDOR','APPROVED',now(),now())", vendorId, vendorUserId);

        UUID categoryId = UUID.randomUUID();
        db.update("INSERT INTO categories(id,name,slug,is_active,requires_safety_cert,created_at,updated_at) VALUES (?,'TEST','test',true,false,now(),now())", categoryId);

        serviceId = UUID.randomUUID();
        db.update("INSERT INTO services(id,vendor_id,category_id,name,status,price,weather_sensitive,view_count,waiver_required,created_at,updated_at) VALUES (?,?,?,'TEST_SERVICE','PUBLISHED',500000,false,0,false,now(),now())", serviceId, vendorId, categoryId);

        optionId = UUID.randomUUID();
        db.update("INSERT INTO service_options(id,service_id,name,option_type,pricing_unit,price,max_pax_per_package,status,created_at,updated_at) VALUES (?,?,?,'SHARED','PER_PERSON',500000,null,'ACTIVE',now(),now())", optionId, serviceId, "OPTION_500K");

        slotId = UUID.randomUUID();
        db.update("INSERT INTO service_slots(id,service_id,date,start_time,end_time,capacity,booked_count,status,inventory_type,created_at,updated_at) VALUES (?,?,?,?,?,?,0,'OPEN','PERSON_LIMIT',now(),now())",
                slotId, serviceId, java.sql.Date.valueOf(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(5)),
                java.sql.Time.valueOf(LocalTime.of(9, 0)), java.sql.Time.valueOf(LocalTime.of(10, 0)), 100);

        adminToken = token(adminId, "ADMIN");
        customerToken = token(customerId, "CUSTOMER");
    }

    UUID user(String role) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO users(id,email,password_hash,full_name,role,is_email_verified,is_locked,session_version,created_at,updated_at) VALUES (?,?,'unused','TEST',?,true,false,0,now(),now())", id, id + "@flashsale.test", role);
        return id;
    }

    String token(UUID id, String role) {
        return jwt.generateAccessToken(new Authentication(id, id + "@flashsale.test", "unused", role, true, true));
    }

    void setSystemConfig(String key, String value) {
        db.update("DELETE FROM system_configs WHERE key=?", key);
        db.update("INSERT INTO system_configs(id,key,value,description,created_at,updated_at) VALUES (gen_random_uuid(),?,?,?,now(),now())", key, value, "Test config");
    }

    HttpResponse<String> call(String method, String path, String token, Object body) throws Exception {
        return call(method, path, token, body, null);
    }

    HttpResponse<String> call(String method, String path, String token, Object body, String idempotencyKey) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("TC-01 — Platform commission only: Flash Sale activates & books without vendor policy")
    void tc01_platformCommissionOnly() throws Exception {
        // Zero vendor_commission_policies exist
        assertThat(db.queryForObject("SELECT count(*) FROM vendor_commission_policies", Integer.class)).isZero();

        // Admin configures PLATFORM_COMMISSION_RATE = 10% and safety policy
        setSystemConfig("PLATFORM_COMMISSION_RATE", "10.0");
        setSystemConfig("FLASH_SALE_MAX_SUBSIDY_RATIO", "0.80");

        // Verify resolver reads 10%
        assertThat(commissionPolicyAdapter.getCommissionRate(vendorId)).isEqualByComparingTo("0.100000");

        // Admin creates and activates Flash Sale with 30,000 VND subsidy
        var now = OffsetDateTime.now();
        var terms = new Terms(serviceId, optionId, n("30000"), now.minusMinutes(1), now.plusHours(4), 10, n("300000"));
        var created = flashSaleService.create(terms, adminId);
        var activated = flashSaleService.activate(created.flashSale().id(), adminId);

        assertThat(activated.flashSale().status()).isEqualTo("ENABLED");
        assertThat(activated.financialPreview().commissionRate()).isEqualByComparingTo("0.100000");
        assertThat(activated.financialPreview().customerPayable()).isEqualByComparingTo("470000");
        assertThat(activated.financialPreview().vendorReceivable()).isEqualByComparingTo("450000");
        assertThat(activated.financialPreview().platformContribution()).isEqualByComparingTo("20000");

        // Customer creates booking hold via HTTP API
        var item = Map.of("slotId", slotId, "optionId", optionId, "quantity", 1, "flashSaleId", activated.flashSale().id());
        var res = call("POST", "/api/bookings/hold", customerToken, Map.of("items", List.of(item)), UUID.randomUUID().toString());
        assertThat(res.statusCode()).withFailMessage("TC-01 HTTP " + res.statusCode() + " body: " + res.body()).isEqualTo(201);

        var jsonRes = json.readTree(res.body());
        assertThat(jsonRes.path("totalAmount").decimalValue()).isEqualByComparingTo("470000");

        // Allocation verified in database
        var alloc = db.queryForMap("SELECT * FROM flash_sale_allocations WHERE flash_sale_id=?", activated.flashSale().id());
        assertThat((BigDecimal) alloc.get("commission_rate")).isEqualByComparingTo("0.100000");
        assertThat((BigDecimal) alloc.get("customer_payable")).isEqualByComparingTo("470000");
        assertThat((BigDecimal) alloc.get("vendor_receivable")).isEqualByComparingTo("450000");
    }

    @Test
    @DisplayName("TC-02 — Admin changes commission via API: 10% -> 12% updates resolver & new bookings")
    void tc02_adminChangesCommission() throws Exception {
        setSystemConfig("PLATFORM_COMMISSION_RATE", "10.0");
        setSystemConfig("FLASH_SALE_MAX_SUBSIDY_RATIO", "0.80");

        // Admin calls PUT /api/admin/settings/PLATFORM_COMMISSION_RATE with 12%
        var updateRes = call("PUT", "/api/admin/settings/PLATFORM_COMMISSION_RATE", adminToken, Map.of("value", "12.0", "description", "Updated rate"));
        assertThat(updateRes.statusCode()).isEqualTo(200);

        // Verify PostgreSQL saved 12.0
        String dbVal = db.queryForObject("SELECT value FROM system_configs WHERE key='PLATFORM_COMMISSION_RATE'", String.class);
        assertThat(dbVal).isEqualTo("12.0");

        // Verify Commission Resolver reads 12%
        BigDecimal currentRate = commissionPolicyAdapter.getCommissionRate(vendorId);
        assertThat(currentRate).isEqualByComparingTo("0.120000");

        // Flash Sale policy now calculates with 12% commission (500,000 * 12% = 60,000)
        var now = OffsetDateTime.now();
        var terms = new Terms(serviceId, optionId, n("30000"), now.minusMinutes(1), now.plusHours(4), 10, n("300000"));
        var activated = flashSaleService.activate(flashSaleService.create(terms, adminId).flashSale().id(), adminId);

        assertThat(activated.financialPreview().commissionRate()).isEqualByComparingTo("0.120000");
        assertThat(activated.financialPreview().commissionAmount()).isEqualByComparingTo("60000");
        assertThat(activated.financialPreview().vendorReceivable()).isEqualByComparingTo("440000"); // 500k - 60k
        assertThat(activated.financialPreview().platformContribution()).isEqualByComparingTo("30000"); // 60k - 30k
    }

    @Test
    @DisplayName("TC-03 — Historical snapshot immutability: Bookings at 10% are not recalculated when admin updates to 12%")
    void tc03_historicalSnapshotImmutability() throws Exception {
        setSystemConfig("PLATFORM_COMMISSION_RATE", "10.0");
        setSystemConfig("FLASH_SALE_MAX_SUBSIDY_RATIO", "0.80");

        // Create campaign and hold booking at 10%
        var now = OffsetDateTime.now();
        var terms = new Terms(serviceId, optionId, n("30000"), now.minusMinutes(1), now.plusHours(4), 10, n("300000"));
        var activated = flashSaleService.activate(flashSaleService.create(terms, adminId).flashSale().id(), adminId);

        var item = Map.of("slotId", slotId, "optionId", optionId, "quantity", 1, "flashSaleId", activated.flashSale().id());
        var res = call("POST", "/api/bookings/hold", customerToken, Map.of("items", List.of(item)), UUID.randomUUID().toString());
        assertThat(res.statusCode()).withFailMessage("TC-03 HTTP " + res.statusCode() + " body: " + res.body()).isEqualTo(201);

        // Admin updates rate to 12%
        call("PUT", "/api/admin/settings/PLATFORM_COMMISSION_RATE", adminToken, Map.of("value", "12.0"));

        // Historical allocation remains strictly at 10%
        var alloc = db.queryForMap("SELECT * FROM flash_sale_allocations WHERE flash_sale_id=?", activated.flashSale().id());
        assertThat((BigDecimal) alloc.get("commission_rate")).isEqualByComparingTo("0.100000");
        assertThat((BigDecimal) alloc.get("vendor_receivable")).isEqualByComparingTo("450000");
        assertThat((BigDecimal) alloc.get("customer_payable")).isEqualByComparingTo("470000");

        // Settlement calculation using snapshot context maintains 450,000 net payout
        var context = SubOrderCalculationContext.builder()
                .subOrderId(UUID.randomUUID())
                .subtotalAmount(n("500000"))
                .commissionBasisAmount(n("500000"))
                .finalAmount(n("470000"))
                .platformDiscountAmount(n("30000"))
                .commissionRate(n("0.100000")) // snapshot rate
                .status(com.danasea.backend.modules.order.domain.models.SubOrderStatus.COMPLETED)
                .build();

        var line = settlementEngine.calculateLineItem(context);
        assertThat(line.getGrossAmount()).isEqualByComparingTo("500000");
        assertThat(line.getCommissionAmount()).isEqualByComparingTo("50000");
        assertThat(line.getNetAmount()).isEqualByComparingTo("450000"); // Vendor payout intact!
    }

    @Test
    @DisplayName("TC-04 — Active campaign: lowering commission blocks subsidy exceeding policy")
    void tc04_activeCampaignPolicyChangeEnforcement() throws Exception {
        setSystemConfig("PLATFORM_COMMISSION_RATE", "10.0"); // 10% commission = 50,000 VND
        setSystemConfig("FLASH_SALE_MAX_SUBSIDY_RATIO", "0.80"); // Max subsidy = 40,000 VND

        var now = OffsetDateTime.now();
        var terms = new Terms(serviceId, optionId, n("30000"), now.minusMinutes(1), now.plusHours(4), 10, n("300000"));
        var activated = flashSaleService.activate(flashSaleService.create(terms, adminId).flashSale().id(), adminId);

        // First booking succeeds at 10%
        var item = Map.of("slotId", slotId, "optionId", optionId, "quantity", 1, "flashSaleId", activated.flashSale().id());
        assertThat(call("POST", "/api/bookings/hold", customerToken, Map.of("items", List.of(item)), UUID.randomUUID().toString()).statusCode()).isEqualTo(201);

        // Admin lowers commission to 3% (500k * 3% = 15,000 VND commission; max subsidy at 80% = 12,000 VND)
        call("PUT", "/api/admin/settings/PLATFORM_COMMISSION_RATE", adminToken, Map.of("value", "3.0"));

        // Next booking hold attempt must be blocked because 30,000 subsidy exceeds 12,000 max allowed
        var blockedRes = call("POST", "/api/bookings/hold", customerToken, Map.of("items", List.of(item)), UUID.randomUUID().toString());
        assertThat(blockedRes.statusCode()).isEqualTo(422);
        assertThat(blockedRes.body()).contains("SUBSIDY_EXCEEDS_POLICY");
    }

    @Test
    @DisplayName("TC-05 — Missing commission: Activation rejected with COMMISSION_POLICY_MISSING without hardcoded fallback")
    void tc05_missingCommissionRejectsActivation() {
        // No PLATFORM_COMMISSION_RATE set in system_configs
        assertThatThrownBy(() -> commissionPolicyAdapter.getCommissionRate(vendorId))
                .isInstanceOf(FlashSaleException.class)
                .hasMessageContaining("COMMISSION_POLICY_MISSING");

        var now = OffsetDateTime.now();
        var terms = new Terms(serviceId, optionId, n("30000"), now.minusMinutes(1), now.plusHours(4), 10, n("300000"));
        var draft = flashSaleService.create(terms, adminId);

        // Activation must throw COMMISSION_POLICY_MISSING
        assertThatThrownBy(() -> flashSaleService.activate(draft.flashSale().id(), adminId))
                .isInstanceOf(FlashSaleException.class)
                .hasMessageContaining("COMMISSION_POLICY_MISSING");
    }

    @Test
    @DisplayName("TC-06 — Vendor policy isolation: legacy vendor_commission_policies row does NOT override PLATFORM_COMMISSION_RATE")
    void tc06_vendorPolicyIsolation() throws Exception {
        // Platform commission is 10%
        setSystemConfig("PLATFORM_COMMISSION_RATE", "10.0");
        setSystemConfig("FLASH_SALE_MAX_SUBSIDY_RATIO", "0.80");

        // Legacy table has a vendor row with 25% commission rate
        db.update("INSERT INTO vendor_commission_policies(id,vendor_id,version,commission_rate,maximum_subsidy_ratio,estimated_unit_cost,minimum_unit_margin,provenance,created_by,created_at) VALUES (gen_random_uuid(),?,1,0.250000,0.800000,5000,10000,'LEGACY_UNAUTHORIZED_OVERRIDE',?,now())", vendorId, adminId);

        // Verify resolver strictly uses platform 10%, NOT the legacy vendor override of 25%
        BigDecimal rate = commissionPolicyAdapter.getCommissionRate(vendorId);
        assertThat(rate).isEqualByComparingTo("0.100000");

        // Verify Flash Sale preview uses platform 10%
        var preview = flashSaleService.preview(serviceId, optionId, n("30000"));
        assertThat(preview.financial().commissionRate()).isEqualByComparingTo("0.100000");
        assertThat(preview.financial().commissionAmount()).isEqualByComparingTo("50000");
        assertThat(preview.financial().vendorReceivable()).isEqualByComparingTo("450000"); // Not 500k - 125k!
    }

    @Test
    @DisplayName("TC-07 — Financial invariant verification: customerPayable + subsidy = originalPrice and vendorReceivable + commission = originalPrice")
    void tc07_financialInvariants() {
        setSystemConfig("PLATFORM_COMMISSION_RATE", "10.0");
        setSystemConfig("FLASH_SALE_MAX_SUBSIDY_RATIO", "0.80");

        var policy = commissionPolicyAdapter.resolveFlashSalePolicy(vendorId);

        // Test with price 500,000, subsidy 30,000, qty 3
        var snapshot = FlashSalePricing.calculate(n("500000"), 3, policy, n("30000"));

        BigDecimal original = n("1500000.00");
        BigDecimal commission = n("150000.00");
        BigDecimal subsidy = n("90000.00");
        BigDecimal customerPayable = n("1410000.00");
        BigDecimal vendorReceivable = n("1350000.00");
        BigDecimal grossContribution = n("60000.00");

        assertThat(snapshot.originalAmount()).isEqualByComparingTo(original);
        assertThat(snapshot.commissionAmount()).isEqualByComparingTo(commission);
        assertThat(snapshot.subsidyAmount()).isEqualByComparingTo(subsidy);
        assertThat(snapshot.customerPayable()).isEqualByComparingTo(customerPayable);
        assertThat(snapshot.vendorReceivable()).isEqualByComparingTo(vendorReceivable);
        assertThat(snapshot.platformContribution()).isEqualByComparingTo(grossContribution);

        // Equations
        assertThat(snapshot.customerPayable().add(snapshot.subsidyAmount())).isEqualByComparingTo(snapshot.originalAmount());
        assertThat(snapshot.vendorReceivable().add(snapshot.commissionAmount())).isEqualByComparingTo(snapshot.originalAmount());
        assertThat(snapshot.customerPayable().subtract(snapshot.vendorReceivable())).isEqualByComparingTo(snapshot.platformContribution());
    }
}
