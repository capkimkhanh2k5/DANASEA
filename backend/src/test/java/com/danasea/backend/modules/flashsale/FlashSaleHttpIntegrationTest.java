package com.danasea.backend.modules.flashsale;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.danasea.backend.modules.flashsale.application.FlashSaleService;
import com.danasea.backend.modules.flashsale.application.FlashSaleService.Terms;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing.Policy;
import com.danasea.backend.modules.booking.application.usecases.ExpireBookingHoldUseCase;
import com.danasea.backend.modules.booking.infrastructure.redis.RedisInventoryLockAdapter;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.domain.ports.OrderEventPublisherPort;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","spring.autoconfigure.exclude=",
    "spring.rabbitmq.listener.simple.auto-startup=false","spring.rabbitmq.listener.direct.auto-startup=false",
    "spring.data.redis.database=15","app.scheduler.enabled=false","management.health.mail.enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="FLASH_SALE_TEST_JDBC_URL",matches=".*/danasea_flashsale_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlashSaleHttpIntegrationTest {
    @DynamicPropertySource static void infrastructure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv("FLASH_SALE_TEST_JDBC_URL"));
        r.add("spring.datasource.username",()->"postgres");
        r.add("spring.datasource.password",()->System.getenv("FLASH_SALE_TEST_DB_PASSWORD"));
        r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        r.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.data.redis.host",()->"localhost"); r.add("spring.data.redis.port",()->6380);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired JwtTokenProvider jwt;
    @Autowired FlashSaleService sales;
    @Autowired ExpireBookingHoldUseCase expiry;
    @Autowired OrderPaymentService legacyOrders;
    @Autowired com.danasea.backend.modules.order.application.PaymentWebhookSigner webhookSigner;
    @MockitoBean OrderEventPublisherPort orderEvents;
    @MockitoBean org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @MockitoBean org.springframework.mail.javamail.JavaMailSender mail;
    @MockitoSpyBean RedisInventoryLockAdapter inventory;
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    UUID admin,customer,vendorUser,vendor,service,shared,privateOption,slot,privateSlot;
    String adminToken,customerToken,vendorToken;
    static BigDecimal n(String s) { return new BigDecimal(s); }
    @BeforeAll void migratedExistingRows() throws Exception {
        assertThat(db.queryForObject("SELECT current_database()",String.class)).isEqualTo("danasea_flashsale_test");
        assertThat(db.queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",String.class)).isEqualTo("40");
        Files.writeString(Path.of("target/flash-sale-http-evidence.jsonl"),json.writeValueAsString(Map.of(
                "baseline","3c80558","runtime","Phase 1 target/classes SpringBootTest RANDOM_PORT","port",port,
                "database","danasea_flashsale_test","redisDatabase",15))+"\n");
    }
    @BeforeEach void fixture() {
        assertThat(db.queryForObject("SELECT current_database()",String.class)).isEqualTo("danasea_flashsale_test");
        sales.setClock(Clock.systemUTC()); reset(inventory);
        resetTestDatabase();
        db.update("DELETE FROM system_configs WHERE key IN ('PLATFORM_COMMISSION_RATE','FLASH_SALE_MAX_SUBSIDY_RATIO','FLASH_SALE_ESTIMATED_UNIT_COST','FLASH_SALE_MINIMUM_UNIT_MARGIN')");
        db.update("INSERT INTO system_configs(id,key,value,description,created_at,updated_at) VALUES (gen_random_uuid(),'PLATFORM_COMMISSION_RATE','10.0','Platform commission rate',now(),now())");
        db.update("INSERT INTO system_configs(id,key,value,description,created_at,updated_at) VALUES (gen_random_uuid(),'FLASH_SALE_MAX_SUBSIDY_RATIO','0.80','Max subsidy ratio',now(),now())");
        db.update("INSERT INTO system_configs(id,key,value,description,created_at,updated_at) VALUES (gen_random_uuid(),'FLASH_SALE_ESTIMATED_UNIT_COST','5000','Estimated cost',now(),now())");
        db.update("INSERT INTO system_configs(id,key,value,description,created_at,updated_at) VALUES (gen_random_uuid(),'FLASH_SALE_MINIMUM_UNIT_MARGIN','10000','Minimum margin',now(),now())");
        admin=user("ADMIN"); customer=user("CUSTOMER"); vendorUser=user("VENDOR"); vendor=UUID.randomUUID();
        db.update("INSERT INTO vendors(id,user_id,business_name,verification_status,created_at,updated_at) VALUES (?,?,'DEMO_LOCAL_TEST_ONLY','APPROVED',now(),now())",vendor,vendorUser);
        UUID category=UUID.randomUUID();
        db.update("INSERT INTO categories(id,name,slug,is_active,requires_safety_cert,created_at,updated_at) VALUES (?,'TEST','test',true,false,now(),now())",category);
        service=UUID.randomUUID();
        db.update("INSERT INTO services(id,vendor_id,category_id,name,status,price,weather_sensitive,view_count,waiver_required,created_at,updated_at) VALUES (?,?,?,'TEST','PUBLISHED',500000,false,0,false,now(),now())",service,vendor,category);
        shared=option("SHARED","PER_PERSON",500000,null); privateOption=option("PRIVATE","PER_PACKAGE",1000000,4);
        slot=slot("PERSON_LIMIT",100); privateSlot=slot("SHARED_CAPACITY_UNITS",8);
        for(int unit=1;unit<=2;unit++) db.update("INSERT INTO service_slot_units(id,slot_id,unit_number,capacity,booked_count,created_at,updated_at) VALUES (?,?,?,4,0,now(),now())",UUID.randomUUID(),privateSlot,unit);
        sales.configure(new Policy(null,vendor,1,n("0.10"),n("0.80"),n("5000"),n("10000"),"DEMO_LOCAL_TEST_ONLY"),admin);
        adminToken=token(admin,"ADMIN"); customerToken=token(customer,"CUSTOMER"); vendorToken=token(vendorUser,"VENDOR");
    }
    UUID user(String role) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO users(id,email,password_hash,full_name,role,is_email_verified,is_locked,session_version,created_at,updated_at) VALUES (?,?,'unused','TEST',?,true,false,0,now(),now())",id,id+"@flashsale.test",role);
        return id;
    }
    String token(UUID id,String role) { return jwt.generateAccessToken(new Authentication(id,id+"@flashsale.test","unused",role,true,true)); }
    UUID option(String type,String pricing,int price,Integer pax) {
        UUID id=UUID.randomUUID(); db.update("INSERT INTO service_options(id,service_id,name,option_type,pricing_unit,price,max_pax_per_package,status,created_at,updated_at) VALUES (?,?,'TEST',?,?,?,?,'ACTIVE',now(),now())",id,service,type,pricing,price,pax); return id;
    }
    UUID slot(String type,int capacity) {
        UUID id=UUID.randomUUID();
        int hour=8+db.queryForObject("SELECT count(*) FROM service_slots WHERE service_id=?",Integer.class,service);
        db.update("INSERT INTO service_slots(id,service_id,date,start_time,end_time,capacity,booked_count,status,inventory_type,created_at,updated_at) VALUES (?,?,?,?,?,?,0,'OPEN',?,now(),now())",id,service,java.sql.Date.valueOf(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(5)),java.sql.Time.valueOf(LocalTime.of(hour,0)),java.sql.Time.valueOf(LocalTime.of(hour+1,0)),capacity,type); return id;
    }
    Terms terms(UUID option,int quota,int budget,OffsetDateTime start,OffsetDateTime end) { return new Terms(service,option,n("30000"),start,end,quota,BigDecimal.valueOf(budget)); }
    UUID campaign(UUID option,int quota,int budget) {
        var now=OffsetDateTime.now(); var s=sales.create(terms(option,quota,budget,now.minusMinutes(1),now.plusHours(7).plusMinutes(13)),admin);
        sales.activate(s.flashSale().id(),admin); return s.flashSale().id();
    }
    HttpResponse<String> call(String method,String path,String token,Object body,String key) throws Exception {
        return call(method,path,token,body,key,null);
    }
    HttpResponse<String> call(String method,String path,String token,Object body,String key,String signature) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(30));
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(key!=null) builder.header("Idempotency-Key",key);
        if(signature!=null) builder.header("X-Payment-Signature",signature);
        if(body!=null) builder.header("Content-Type","application/json");
        var response=http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(signature!=null?(String)body:json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        synchronized(this) { Files.writeString(Path.of("target/flash-sale-http-evidence.jsonl"),json.writeValueAsString(Map.of("method",method,"path",path,"role",token==null?"GUEST":token.equals(adminToken)?"ADMIN":token.equals(customerToken)?"CUSTOMER":"VENDOR","status",response.statusCode(),"response",response.body()))+"\n",StandardOpenOption.APPEND); }
        return response;
    }
    JsonNode body(HttpResponse<String> response,int status) throws Exception { assertThat(response.statusCode()).as(response.body()).isEqualTo(status); return json.readTree(response.body()); }
    Object holdPayload(UUID sale,UUID option,UUID slotId,int quantity) {
        var item=new LinkedHashMap<String,Object>(); item.put("slotId",slotId); item.put("optionId",option); item.put("quantity",quantity); if(sale!=null) item.put("flashSaleId",sale);
        return Map.of("items",List.of(item));
    }
    UUID hold(UUID sale,int quantity) throws Exception { return UUID.fromString(body(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,shared,slot,quantity),UUID.randomUUID().toString()),201).get("bookingId").asText()); }
    String state(UUID booking) { return db.queryForObject("SELECT state FROM flash_sale_allocations WHERE booking_id=?",String.class,booking); }

    @Test void guestAdminRbacCrudAndPublicPrivacy() throws Exception {
        body(call("GET","/api/flash-sales/active",null,null,null),200);
        assertThat(call("GET","/api/admin/flash-sales",null,null,null).statusCode()).isEqualTo(401);
        assertThat(call("GET","/api/admin/flash-sales",vendorToken,null,null).statusCode()).isEqualTo(403);
        assertThat(call("GET","/api/admin/flash-sales",customerToken,null,null).statusCode()).isEqualTo(403);
        body(call("GET","/api/admin/flash-sales/eligible-services",adminToken,null,null),200);
        var preview=body(call("POST","/api/admin/flash-sales/preview",adminToken,Map.of("serviceId",service,"optionId",shared,"unitSubsidy",30000),null),200);
        assertThat(preview.path("financial").path("vendorReceivable").decimalValue()).isEqualByComparingTo("450000");
        assertThat(preview.path("estimatedPlatformCost").decimalValue()).isEqualByComparingTo("5000");
        body(call("GET","/api/admin/flash-sales/commission-policies?vendorId="+vendor,adminToken,null,null),200);
        var policy=body(call("POST","/api/admin/flash-sales/commission-policies",adminToken,new Policy(null,vendor,0,n("0.10"),n("0.80"),n("5000"),n("10000"),"DEMO_LOCAL_TEST_ONLY_V2"),null),200);
        assertThat(policy.path("version").asInt()).isEqualTo(2);
        var now=OffsetDateTime.now(); var t=terms(shared,10,300000,now.minusMinutes(1),now.plusHours(7).plusMinutes(13));
        UUID id=UUID.fromString(body(call("POST","/api/admin/flash-sales",adminToken,t,null),201).path("flashSale").path("id").asText());
        body(call("GET","/api/admin/flash-sales/"+id,adminToken,null,null),200);
        assertThat(call("POST","/api/admin/flash-sales",vendorToken,t,null).statusCode()).isEqualTo(403);
        assertThat(body(call("GET","/api/flash-sales/active",null,null,null),200).size()).isZero();
        body(call("PATCH","/api/admin/flash-sales/"+id,adminToken,t,null),200);
        body(call("POST","/api/admin/flash-sales/"+id+"/activate",adminToken,null,null),200);
        var publicResponse=call("GET","/api/flash-sales/"+id,null,null,null); var offer=body(publicResponse,200);
        assertThat(offer.path("originalPrice").decimalValue()).isEqualByComparingTo("500000");
        assertThat(offer.path("salePrice").decimalValue()).isEqualByComparingTo("470000");
        assertThat(offer.path("actualDiscountPercent").decimalValue()).isEqualByComparingTo("6");
        assertThat(publicResponse.body().toLowerCase()).doesNotContain("commission","budget","margin","cost","receivable");
        assertThat(call("PATCH","/api/admin/flash-sales/"+id,adminToken,t,null).statusCode()).isEqualTo(422);
        var catalog=body(call("GET","/api/services?size=100",null,null,null),200);
        assertThat(catalog.path("content").get(0).path("flashSales").size()).isEqualTo(1);
        body(call("GET","/api/services/"+service,null,null,null),200);
        body(call("POST","/api/admin/flash-sales/"+id+"/cancel",adminToken,null,null),200);
        assertThat(body(call("GET","/api/flash-sales/active",null,null,null),200).size()).isZero();
        UUID draft=sales.create(t,admin).flashSale().id();
        assertThat(call("DELETE","/api/admin/flash-sales/"+draft,adminToken,null,null).statusCode()).isEqualTo(204);
    }
    @Test void futureExpirationBoundaryAndNoFixedDuration() throws Exception {
        var now=OffsetDateTime.now(); var s=sales.create(terms(shared,2,60000,now.plusMinutes(3),now.plusMinutes(47)),admin).flashSale(); sales.activate(s.id(),admin);
        assertThat(sales.active()).isEmpty();
        sales.setClock(Clock.fixed(s.startsAt(),ZoneOffset.UTC)); assertThat(sales.active()).hasSize(1);
        sales.setClock(Clock.fixed(s.endsAt(),ZoneOffset.UTC)); assertThat(sales.active()).isEmpty();
        assertThat(call("POST","/api/bookings/hold",customerToken,holdPayload(s.id(),shared,slot,1),"expired").statusCode()).isEqualTo(422);
        sales.setClock(Clock.systemUTC());
        assertThat(call("POST","/api/admin/flash-sales",adminToken,terms(shared,1,30000,now,now.minusSeconds(1)),null).statusCode()).isEqualTo(422);
    }
    @Test void overlapMissingPolicyAndPriceChangeFailClosed() throws Exception {
        UUID first=campaign(shared,3,90000);
        var t=terms(shared,3,90000,OffsetDateTime.now(),OffsetDateTime.now().plusHours(2));
        UUID second=sales.create(t,admin).flashSale().id();
        assertThat(call("POST","/api/admin/flash-sales/"+second+"/activate",adminToken,null,null).statusCode()).isEqualTo(409);
        db.update("UPDATE service_options SET price=510000 WHERE id=?",shared);
        assertThat(sales.active()).isEmpty();
        assertThat(call("POST","/api/bookings/hold",customerToken,holdPayload(first,shared,slot,1),"changed").statusCode()).isEqualTo(422);
        sales.cancel(first,admin);
        db.execute("TRUNCATE vendor_commission_policies CASCADE");
        db.update("DELETE FROM system_configs WHERE key IN ('PLATFORM_COMMISSION_RATE','FLASH_SALE_MAX_SUBSIDY_RATIO')");
        UUID missing=sales.create(t,admin).flashSale().id();
        var failed=call("POST","/api/admin/flash-sales/"+missing+"/activate",adminToken,null,null);
        assertThat(failed.statusCode()).isEqualTo(422); assertThat(failed.body()).contains("COMMISSION_POLICY_MISSING");
    }
    @Test void holdSnapshotOrderAndVoucherSafety() throws Exception {
        UUID sale=campaign(shared,10,300000); String key="same-hold"; Object payload=holdPayload(sale,shared,slot,2);
        var response=body(call("POST","/api/bookings/hold",customerToken,payload,key),201); UUID booking=UUID.fromString(response.path("bookingId").asText());
        assertThat(response.path("totalAmount").decimalValue()).isEqualByComparingTo("940000");
        assertThat(body(call("POST","/api/bookings/hold",customerToken,payload,key),201).path("bookingId").asText()).isEqualTo(booking.toString());
        assertThat(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,shared,slot,1),key).statusCode()).isEqualTo(409);
        assertThat(state(booking)).isEqualTo("RESERVED");
        var snapshot=db.queryForMap("SELECT * FROM flash_sale_allocations WHERE booking_id=?",booking);
        assertThat((BigDecimal)snapshot.get("original_amount")).isEqualByComparingTo("1000000");
        assertThat((BigDecimal)snapshot.get("vendor_receivable")).isEqualByComparingTo("900000");
        assertThat((BigDecimal)snapshot.get("platform_contribution")).isEqualByComparingTo("40000");
        assertThat(call("POST","/api/orders",customerToken,Map.of("bookingId",booking,"discountCode","ANY"),"no-stack").statusCode()).isEqualTo(422);
        body(call("POST","/api/orders",customerToken,Map.of("bookingId",booking),"order"),201);
        var order=db.queryForMap("SELECT * FROM sub_orders WHERE booking_item_id=(SELECT id FROM booking_items WHERE booking_id=?)",booking);
        assertThat((BigDecimal)order.get("commission_amount")).isEqualByComparingTo("100000");
        assertThat((BigDecimal)order.get("vendor_payout_amount")).isEqualByComparingTo("900000");
        assertThat((BigDecimal)order.get("final_amount")).isEqualByComparingTo("940000");
        assertThat(db.queryForObject("SELECT price FROM service_options WHERE id=?",BigDecimal.class,shared)).isEqualByComparingTo("500000");
        assertThat(db.queryForObject("SELECT count(*) FROM payments",Integer.class)).isZero();
    }
    @Test void legacyOrderPathAlsoUsesSnapshot() throws Exception {
        UUID booking=hold(campaign(shared,3,90000),1);
        legacyOrders.createOrder(customer,booking,"legacy-order-key");
        assertThat(db.queryForObject("SELECT vendor_payout_amount FROM sub_orders",BigDecimal.class)).isEqualByComparingTo("450000");
        assertThat(db.queryForObject("SELECT commission_amount FROM sub_orders",BigDecimal.class)).isEqualByComparingTo("50000");
    }
    @Test void quotaBudgetCancellationAndExpiryReleaseExactlyOnce() throws Exception {
        UUID sale=campaign(shared,1,300000); UUID booking=hold(sale,1);
        assertThat(sales.active()).isEmpty(); assertThat(sales.publicDetail(sale).availability()).isEqualTo("SOLD_OUT");
        assertThat(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,shared,slot,1),"quota").statusCode()).isEqualTo(422);
        assertThat(call("DELETE","/api/bookings/hold/"+booking,customerToken,null,null).statusCode()).isEqualTo(204);
        assertThat(call("DELETE","/api/bookings/hold/"+booking,customerToken,null,null).statusCode()).isEqualTo(204);
        assertThat(state(booking)).isEqualTo("RELEASED"); assertThat(sales.active()).hasSize(1);
        sales.cancel(sale,admin); UUID budgetSale=campaign(shared,10,30000); UUID next=hold(budgetSale,1);
        assertThat(sales.active()).isEmpty();
        assertThat(call("POST","/api/bookings/hold",customerToken,holdPayload(budgetSale,shared,slot,1),"budget").statusCode()).isEqualTo(422);
        body(call("POST","/api/orders",customerToken,Map.of("bookingId",next),"pending-expiry-order"),201);
        db.update("UPDATE bookings SET hold_expires_at=now()-interval '1 minute' WHERE id=?",next);
        expiry.execute(next,OffsetDateTime.now()); expiry.execute(next,OffsetDateTime.now());
        assertThat(state(next)).isEqualTo("RELEASED"); assertThat(sales.active()).hasSize(1);
        assertThat(db.queryForObject("SELECT status FROM master_orders WHERE booking_id=?",String.class,next)).isEqualTo("CANCELLED");
        assertThat(redis.opsForHash().entries("inventory:slot:"+slot+":holds")).isEmpty();
    }
    @Test void concurrentDifferentSlotsCannotExceedBudgetOrQuota() throws Exception {
        UUID sale=campaign(shared,3,90000); UUID another=slot("PERSON_LIMIT",100); var pool=Executors.newFixedThreadPool(8);
        try {
            var ready=new CountDownLatch(1); var tasks=new ArrayList<Future<Integer>>();
            for(int i=0;i<8;i++) { final int k=i; tasks.add(pool.submit(()-> { ready.await(); return call("POST","/api/bookings/hold",customerToken,holdPayload(sale,shared,k%2==0?slot:another,1),"concurrent-"+k).statusCode(); })); }
            ready.countDown(); int accepted=0;
            for(var task:tasks) { int status=task.get(45,TimeUnit.SECONDS); assertThat(status).isIn(201,422); if(status==201) accepted++; }
            assertThat(accepted).isEqualTo(3);
            assertThat(db.queryForObject("SELECT sum(quantity) FROM flash_sale_allocations",Integer.class)).isEqualTo(3);
            assertThat(db.queryForObject("SELECT sum(subsidy_amount) FROM flash_sale_allocations",BigDecimal.class)).isEqualByComparingTo("90000");
        } finally { pool.shutdownNow(); }
    }
    @Test void databaseFailureAfterRedisAcquisitionRollsBackAndCompensates() throws Exception {
        UUID sale=campaign(shared,5,150000);
        db.execute("ALTER TABLE bookings ADD CONSTRAINT flash_sale_test_failure CHECK (false) NOT VALID");
        try { assertThat(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,shared,slot,1),"db-failure").statusCode()).isEqualTo(500); }
        finally { db.execute("ALTER TABLE bookings DROP CONSTRAINT flash_sale_test_failure"); }
        assertThat(db.queryForObject("SELECT count(*) FROM bookings",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM flash_sale_allocations",Integer.class)).isZero();
        assertThat(redis.opsForHash().entries("inventory:slot:"+slot+":holds")).isEmpty();
        body(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,shared,slot,1),"db-failure"),201);
    }
    @Test void ambiguousRedisFailureLeavesNoAllocationOrInventoryLeak() throws Exception {
        UUID sale=campaign(shared,5,150000);
        doAnswer(invocation->{ invocation.callRealMethod(); throw new IllegalStateException("Injected Redis response loss"); }).when(inventory).acquireHolds(any(),anyList(),any());
        assertThat(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,shared,slot,1),"redis-failure").statusCode()).isEqualTo(500);
        assertThat(db.queryForObject("SELECT count(*) FROM flash_sale_allocations",Integer.class)).isZero();
        assertThat(redis.opsForHash().entries("inventory:slot:"+slot+":holds")).isEmpty();
        reset(inventory); body(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,shared,slot,1),"redis-failure"),201);
    }
    @Test void ordinarySharedAndPrivateRemainBookableAndGuestHoldIsDenied() throws Exception {
        assertThat(call("POST","/api/bookings/hold",null,holdPayload(null,shared,slot,1),null).statusCode()).isEqualTo(401);
        var standard=body(call("POST","/api/bookings/hold",customerToken,holdPayload(null,shared,slot,2),null),201);
        assertThat(standard.path("totalAmount").decimalValue()).isEqualByComparingTo("1000000");
        var privateHold=body(call("POST","/api/bookings/hold",customerToken,holdPayload(null,privateOption,privateSlot,1),null),201);
        assertThat(privateHold.path("totalAmount").decimalValue()).isEqualByComparingTo("1000000");
        UUID sale=campaign(privateOption,2,60000);
        var offer=body(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,privateOption,privateSlot,1),"private-offer"),201);
        assertThat(offer.path("totalAmount").decimalValue()).isEqualByComparingTo("970000");
        assertThat(db.queryForObject("SELECT quantity FROM flash_sale_allocations",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM booking_item_allocations",Integer.class)).isEqualTo(2);
    }
    @Test void validHoldConfirmsAfterSaleEndsAndCannotBeExpiredAgain() throws Exception {
        var now=OffsetDateTime.now();
        var s=sales.create(terms(shared,2,60000,now.minusMinutes(1),now.plusSeconds(5)),admin).flashSale(); sales.activate(s.id(),admin);
        UUID booking=hold(s.id(),1);
        body(call("POST","/api/orders",customerToken,Map.of("bookingId",booking),"confirm-order"),201);
        UUID order=db.queryForObject("SELECT id FROM master_orders WHERE booking_id=?",UUID.class,booking);
        UUID payment=UUID.randomUUID();
        db.update("INSERT INTO payments(id,master_order_id,provider,amount,status,created_at,updated_at) VALUES (?,?,'SEPAY',470000,'PENDING',now(),now())",payment,order);
        long delay=Math.max(0,Duration.between(Instant.now(),s.endsAt().plusMillis(100)).toMillis());
        Thread.sleep(delay);
        assertThat(sales.active()).isEmpty();
        // Existing test-profile callback validates the real signature and lifecycle.
        // This is a controlled payment event fixture, not an external money transfer.
        String payload=json.writeValueAsString(Map.of("eventId","test-event-"+payment,"paymentId",payment,"providerTransactionId","test-txn-"+payment,"status","SUCCESS","amount",470000));
        body(call("POST","/api/payments/webhook/internal/SEPAY",adminToken,payload,null,webhookSigner.sign(payload)),200);
        body(call("POST","/api/payments/webhook/internal/SEPAY",adminToken,payload,null,webhookSigner.sign(payload)),200);
        body(call("POST","/api/bookings/"+booking+"/confirm",customerToken,null,null),200);
        body(call("POST","/api/bookings/"+booking+"/confirm",customerToken,null,null),200);
        assertThat(state(booking)).isEqualTo("CONSUMED");
        expiry.execute(booking,OffsetDateTime.now().plusDays(1));
        assertThat(state(booking)).isEqualTo("CONSUMED");
        assertThat(db.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,booking)).isEqualTo("CONFIRMED");
        assertThat(db.queryForObject("SELECT price FROM booking_items WHERE booking_id=?",BigDecimal.class,booking)).isEqualByComparingTo("470000");
        assertThat(db.queryForObject("SELECT status FROM master_orders WHERE id=?",String.class,order)).isEqualTo("PAID");
        var refund=body(call("POST","/api/orders/"+order+"/refund-request",customerToken,Map.of("reason","CUSTOMER_CANCEL"),"test-refund-key"),202).get(0);
        assertThat(refund.path("amount").decimalValue()).isEqualByComparingTo("470000");
        UUID refundId=db.queryForObject("SELECT id FROM refunds",UUID.class);
        String refundPayload=json.writeValueAsString(Map.of("eventId","test-refund-"+refundId,"refundId",refundId,"providerRefundId","test-provider-"+refundId,"status","PROCESSED","amount",470000));
        legacyOrders.processRefundWebhook(com.danasea.backend.modules.order.domain.models.PaymentProvider.SEPAY,refundPayload,webhookSigner.sign(refundPayload));
        assertThat(db.queryForObject("SELECT status FROM refunds WHERE id=?",String.class,refundId)).isEqualTo("PROCESSED");
        assertThat(state(booking)).isEqualTo("CONSUMED");
    }
    @Test void policyVersionAndCampaignCancellationCannotRepriceExistingHold() throws Exception {
        UUID sale=campaign(shared,3,90000); UUID booking=hold(sale,1);
        sales.configure(new Policy(null,vendor,1,n("0.20"),n("0.80"),n("5000"),n("10000"),"DEMO_LOCAL_TEST_ONLY_V2"),admin);
        sales.cancel(sale,admin);
        body(call("POST","/api/orders",customerToken,Map.of("bookingId",booking),"frozen-policy-order"),201);
        assertThat(db.queryForObject("SELECT commission_amount FROM sub_orders",BigDecimal.class)).isEqualByComparingTo("50000");
        assertThat(db.queryForObject("SELECT vendor_payout_amount FROM sub_orders",BigDecimal.class)).isEqualByComparingTo("450000");
        assertThat(db.queryForObject("SELECT policy_version FROM flash_sale_allocations",Integer.class)).isEqualTo(1);
        assertThat(sales.active()).isEmpty();
    }
    @Test void databaseConstraintsProtectImmutableSnapshotsAndQuota() throws Exception {
        UUID sale=campaign(shared,1,30000); hold(sale,1);
        assertThatThrownBy(()->db.execute("UPDATE vendor_commission_policies SET commission_rate=0.20"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(()->db.execute("UPDATE flash_sale_allocations SET policy_version=100"))
                .hasMessageContaining("immutable");
        assertThatThrownBy(()->db.execute("INSERT INTO flash_sale_allocations SELECT gen_random_uuid(),flash_sale_id,booking_id,gen_random_uuid(),state,pricing_unit,quantity,original_unit_price,original_amount,policy_id,policy_version,commission_rate,commission_amount,unit_subsidy,subsidy_amount,customer_payable,vendor_receivable,platform_contribution,created_at,updated_at FROM flash_sale_allocations"))
                .hasMessageContaining("quota or budget exhausted");
        assertThat(db.queryForObject("SELECT count(*) FROM flash_sale_allocations",Integer.class)).isEqualTo(1);
    }
    @Test void invalidRequestsAndCrossRoleAccessCannotReserve() throws Exception {
        UUID sale=campaign(shared,3,90000);
        assertThat(call("POST","/api/bookings/hold",vendorToken,holdPayload(sale,shared,slot,1),"vendor-hold").statusCode()).isEqualTo(403);
        assertThat(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,shared,slot,1),null).statusCode()).isEqualTo(422);
        assertThat(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,privateOption,privateSlot,1),"wrong-option").statusCode()).isEqualTo(422);
        assertThat(call("GET","/api/flash-sales/"+UUID.randomUUID(),null,null,null).statusCode()).isEqualTo(404);
        var bad=terms(shared,1,30000,OffsetDateTime.now(),OffsetDateTime.now().minusSeconds(1));
        assertThat(call("POST","/api/admin/flash-sales",adminToken,bad,null).statusCode()).isEqualTo(422);
        UUID booking=hold(sale,1);
        assertThat(call("POST","/api/checkout/discount-preview",customerToken,Map.of("bookingId",booking,"code","ANY"),null).statusCode()).isEqualTo(422);
        UUID other=user("CUSTOMER"); String otherToken=token(other,"CUSTOMER");
        assertThat(call("DELETE","/api/bookings/hold/"+booking,otherToken,null,null).statusCode()).isEqualTo(403);
        assertThat(state(booking)).isEqualTo("RESERVED");
    }
    @Test void concurrentRetriesShareExactlyOneBookingAndAllocation() throws Exception {
        UUID sale=campaign(shared,10,300000); var pool=Executors.newFixedThreadPool(6);
        try {
            var ready=new CountDownLatch(1); var tasks=new ArrayList<Future<String>>();
            for(int i=0;i<6;i++) tasks.add(pool.submit(()-> { ready.await(); return body(call("POST","/api/bookings/hold",customerToken,holdPayload(sale,shared,slot,1),"same-concurrent-key"),201).path("bookingId").asText(); }));
            ready.countDown(); var ids=new HashSet<String>(); for(var task:tasks) ids.add(task.get(45,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1); assertThat(db.queryForObject("SELECT count(*) FROM bookings",Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT count(*) FROM flash_sale_allocations",Integer.class)).isEqualTo(1);
            assertThat(redis.opsForHash().entries("inventory:slot:"+slot+":holds")).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void all24OriginalServicesAnd33OptionsWorkOnPhase1HttpRuntime() throws Exception {
        assertThat(db.queryForObject("SELECT current_database()",String.class)).isEqualTo("danasea_flashsale_test");
        resetTestDatabase();
        var expected=new HashSet<String>();
        for(var row:json.readTree(Files.readString(Path.of("../dataBase_demo/data/services.json"))).path("records")) expected.add(row.path("sqlRow").path("id").asText());
        assertThat(expected).hasSize(24);
        String sourceUrl=System.getenv("FLASH_SALE_TEST_JDBC_URL").replace("/danasea_flashsale_test","/danasea_demo");
        try(var source=java.sql.DriverManager.getConnection(sourceUrl,"postgres",System.getenv("FLASH_SALE_TEST_DB_PASSWORD"))) {
            source.setReadOnly(true); source.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ); source.setAutoCommit(false);
            try(var st=source.createStatement();var r=st.executeQuery("SELECT current_database(),current_setting('transaction_read_only')")) {
                assertThat(r.next()).isTrue(); assertThat(r.getString(1)).isEqualTo("danasea_demo"); assertThat(r.getString(2)).isEqualTo("on");
            }
            try(var st=source.createStatement();var r=st.executeQuery("SELECT user_id FROM vendors")) {
                while(r.next()) { UUID id=r.getObject(1,UUID.class); db.update("INSERT INTO users(id,email,password_hash,full_name,role,is_email_verified,is_locked,session_version,created_at,updated_at) VALUES (?,?,'unused','ISOLATED_SOURCE_FIXTURE','VENDOR',true,false,0,now(),now())",id,id+"@flashsale.test"); }
            }
            copyPublicFixture(source,"vendors","id,user_id,business_name,verification_status");
            copyPublicFixture(source,"categories","id,parent_id,name,slug,is_active,requires_safety_cert");
            copyPublicFixture(source,"services","id,vendor_id,category_id,name,description,status,price,weather_sensitive,duration_minutes,capacity_per_slot,view_count,waiver_required");
            copyPublicFixture(source,"service_options","id,service_id,name,option_type,pricing_unit,price,max_pax_per_package,status");
            copyPublicFixture(source,"service_slots","id,service_id,date,start_time,end_time,capacity,booked_count,status,inventory_type");
            copyPublicFixture(source,"service_slot_units","id,slot_id,unit_number,capacity,booked_count");
            copyPublicFixture(source,"service_images","id,service_id,url,sort_order");
            source.commit();
        }
        db.update("UPDATE service_slots SET date = CURRENT_DATE + 5");
        customer=user("CUSTOMER"); customerToken=token(customer,"CUSTOMER");
        var catalog=body(call("GET","/api/services?size=100",null,null,null),200); var actual=new HashSet<String>();
        for(var item:catalog.path("content")) actual.add(item.path("id").asText());
        assertThat(actual).isEqualTo(expected);
        int optionsVerified=0;
        for(String id:expected) {
            var detail=body(call("GET","/api/services/"+id,null,null,null),200);
            assertThat(detail.path("options").size()).isPositive();
            for(var option:detail.path("options")) {
                UUID optionId=UUID.fromString(option.path("id").asText());
                var slots=body(call("GET","/api/services/"+id+"/slots?optionId="+optionId+"&quantity=1",null,null,null),200);
                UUID selected=null; for(var candidate:slots) if(candidate.path("bookable").asBoolean()) { selected=UUID.fromString(candidate.path("slotId").asText()); break; }
                assertThat(selected).as("Bookable original option %s",optionId).isNotNull();
                var booking=body(call("POST","/api/bookings/hold",customerToken,holdPayload(null,optionId,selected,1),null),201);
                UUID bookingId=UUID.fromString(booking.path("bookingId").asText());
                try { assertThat(booking.path("totalAmount").decimalValue()).isEqualByComparingTo(option.path("price").decimalValue()); }
                finally { assertThat(call("DELETE","/api/bookings/hold/"+bookingId,customerToken,null,null).statusCode()).isEqualTo(204); }
                optionsVerified++;
            }
        }
        assertThat(optionsVerified).isEqualTo(33);
        assertThat(db.queryForObject("SELECT count(*) FROM flash_sale_allocations",Integer.class)).isZero();
    }
    private void resetTestDatabase() {
        assertThat(db.queryForObject("SELECT current_database()",String.class)).isEqualTo("danasea_flashsale_test");
        // The legacy schema has intentionally nullable/missing FKs. Do not assume
        // truncating users/bookings cascades into every catalog/financial fixture.
        db.execute("TRUNCATE users,vendors,vendor_documents,categories,services,service_options,service_slots,service_slot_units,service_images,service_safety_documents,bookings,booking_items,booking_item_allocations,master_orders,sub_orders,payments,refunds,discount_codes,discount_redemptions,flash_sales,vendor_commission_policies,flash_sale_allocations,flash_sale_hold_requests CASCADE");
        assertThat(db.queryForObject("SELECT count(*) FROM services",Integer.class)).isZero();
    }
    private void copyPublicFixture(java.sql.Connection source,String table,String columns) throws Exception {
        // Table/column names are fixed literals above. Copy only public catalog data into
        // the guarded test database; never credentials, bank details or safety attestations.
        String[] names=columns.split(","); String markers=String.join(",",Collections.nCopies(names.length,"?"));
        try(var st=source.createStatement();var rows=st.executeQuery("SELECT "+columns+" FROM "+table)) {
            while(rows.next()) { Object[] values=new Object[names.length]; for(int i=0;i<names.length;i++) values[i]=rows.getObject(i+1);
                db.update("INSERT INTO "+table+"("+columns+",created_at,updated_at) VALUES ("+markers+",now(),now())",values);
            }
        }
    }
}
