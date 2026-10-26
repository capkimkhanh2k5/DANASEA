package com.danasea.backend.modules.flashsale.infrastructure;

import com.danasea.backend.modules.flashsale.application.FlashSaleStore;
import com.danasea.backend.modules.flashsale.domain.*;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class JdbcFlashSaleStore implements FlashSaleStore {
    private final JdbcTemplate db;
    public JdbcFlashSaleStore(JdbcTemplate db) { this.db=db; }
    private static final String OPTION_SQL = """
        SELECT s.id service_id,o.id option_id,s.vendor_id,o.price,o.pricing_unit,s.name,
        (SELECT i.url FROM service_images i WHERE i.service_id=s.id ORDER BY i.sort_order LIMIT 1) image,
        (s.status='PUBLISHED' AND o.status='ACTIVE' AND c.is_active AND v.verification_status='APPROVED') eligible
        FROM service_options o JOIN services s ON s.id=o.service_id
        JOIN categories c ON c.id=s.category_id JOIN vendors v ON v.id=s.vendor_id
        """;
    private static final RowMapper<Option> OPTION = (r,n) -> new Option(uuid(r,"service_id"),uuid(r,"option_id"),
            uuid(r,"vendor_id"),r.getBigDecimal("price"),r.getString("pricing_unit"),r.getString("name"),r.getString("image"),r.getBoolean("eligible"));
    private static UUID uuid(ResultSet r,String name) throws SQLException { return r.getObject(name,UUID.class); }
    private static final RowMapper<Policy> POLICY = (r,n) -> new Policy(uuid(r,"id"),uuid(r,"vendor_id"),r.getInt("version"),
            r.getBigDecimal("commission_rate"),r.getBigDecimal("maximum_subsidy_ratio"),r.getBigDecimal("estimated_unit_cost"),
            r.getBigDecimal("minimum_unit_margin"),r.getString("provenance"));
    private static final RowMapper<FlashSale> SALE = (r,n) -> new FlashSale(uuid(r,"id"),uuid(r,"service_id"),uuid(r,"option_id"),
            r.getString("status"),r.getBigDecimal("unit_subsidy"),r.getTimestamp("starts_at").toInstant(),r.getTimestamp("ends_at").toInstant(),
            r.getInt("quota"),r.getBigDecimal("budget"),r.getBigDecimal("original_unit_price"),uuid(r,"policy_id"),uuid(r,"created_by"));
    private static final RowMapper<Snapshot> SNAPSHOT = (r,n) -> new Snapshot(r.getBigDecimal("original_unit_price"),r.getInt("quantity"),
            r.getBigDecimal("original_amount"),uuid(r,"policy_id"),r.getInt("policy_version"),r.getBigDecimal("commission_rate"),
            r.getBigDecimal("commission_amount"),r.getBigDecimal("unit_subsidy"),r.getBigDecimal("subsidy_amount"),r.getBigDecimal("customer_payable"),
            r.getBigDecimal("vendor_receivable"),r.getBigDecimal("platform_contribution"),null);
    @Override public Option option(UUID service,UUID option) {
        return db.query(OPTION_SQL+" WHERE s.id=? AND o.id=?",OPTION,service,option).stream().findFirst()
                .orElseThrow(() -> new FlashSaleException("OPTION_NOT_FOUND"));
    }
    @Override public List<Option> eligible(int page,int size) {
        return db.query("SELECT * FROM ("+OPTION_SQL+") q WHERE eligible ORDER BY service_id,option_id LIMIT ? OFFSET ?",OPTION,size,page*size);
    }
    @Override public Policy policy(UUID id) {
        return db.query("SELECT * FROM vendor_commission_policies WHERE id=?",POLICY,id).stream().findFirst()
                .orElseThrow(() -> new FlashSaleException("COMMISSION_POLICY_MISSING"));
    }
    @Override public Policy latestPolicy(UUID vendor) {
        return db.query("SELECT * FROM vendor_commission_policies WHERE vendor_id=? ORDER BY version DESC LIMIT 1",POLICY,vendor).stream().findFirst()
                .orElseThrow(() -> new FlashSaleException("COMMISSION_POLICY_MISSING"));
    }
    @Override public Policy createPolicy(Policy p,UUID actor) {
        if(db.queryForList("SELECT id FROM vendors WHERE id=? FOR UPDATE",p.vendorId()).isEmpty()) throw new FlashSaleException("VENDOR_NOT_FOUND");
        int version=db.queryForObject("SELECT coalesce(max(version),0)+1 FROM vendor_commission_policies WHERE vendor_id=?",Integer.class,p.vendorId());
        db.update("""
            INSERT INTO vendor_commission_policies(id,vendor_id,version,commission_rate,maximum_subsidy_ratio,
                estimated_unit_cost,minimum_unit_margin,provenance,created_by) VALUES (?,?,?,?,?,?,?,?,?)
            """,p.id(),p.vendorId(),version,p.rate(),p.maximumRatio(),p.unitCost(),p.minimumMargin(),p.provenance(),actor);
        return policy(p.id());
    }
    @Override public List<Policy> policies(UUID vendor) { return db.query("SELECT * FROM vendor_commission_policies WHERE vendor_id=? ORDER BY version DESC LIMIT 100",POLICY,vendor); }
    @Override public FlashSale sale(UUID id,boolean lock) {
        return db.query("SELECT * FROM flash_sales WHERE id=?"+(lock?" FOR UPDATE":""),SALE,id).stream().findFirst()
                .orElseThrow(() -> new FlashSaleException("FLASH_SALE_NOT_FOUND"));
    }
    @Override public List<FlashSale> sales(int page,int size) { return db.query("SELECT * FROM flash_sales ORDER BY created_at DESC,id LIMIT ? OFFSET ?",SALE,size,page*size); }
    @Override public List<FlashSale> enabledSales() { return db.query("SELECT * FROM flash_sales WHERE status='ENABLED' AND ends_at>CURRENT_TIMESTAMP ORDER BY ends_at,id",SALE); }
    @Override public void save(FlashSale s) {
        db.update("""
            INSERT INTO flash_sales(id,service_id,option_id,status,unit_subsidy,starts_at,ends_at,quota,budget,original_unit_price,policy_id,created_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO UPDATE SET service_id=EXCLUDED.service_id,option_id=EXCLUDED.option_id,
            status=EXCLUDED.status,unit_subsidy=EXCLUDED.unit_subsidy,starts_at=EXCLUDED.starts_at,ends_at=EXCLUDED.ends_at,
            quota=EXCLUDED.quota,budget=EXCLUDED.budget,original_unit_price=EXCLUDED.original_unit_price,policy_id=EXCLUDED.policy_id
            """,s.id(),s.serviceId(),s.optionId(),s.status(),s.unitSubsidy(),Timestamp.from(s.startsAt()),Timestamp.from(s.endsAt()),
                s.quota(),s.budget(),s.originalUnitPrice(),s.policyId(),s.createdBy());
    }
    @Override public void delete(UUID id) { db.update("DELETE FROM flash_sales WHERE id=?",id); }
    @Override public Usage usage(UUID id) {
        return db.queryForObject("""
            SELECT coalesce(sum(quantity) FILTER(WHERE state<>'RELEASED'),0) q,
            coalesce(sum(subsidy_amount) FILTER(WHERE state<>'RELEASED'),0) b,
            coalesce(sum(quantity) FILTER(WHERE state='RESERVED'),0) reserved_q,
            coalesce(sum(quantity) FILTER(WHERE state='CONSUMED'),0) consumed_q,
            coalesce(sum(quantity) FILTER(WHERE state='RELEASED'),0) released_q,
            coalesce(sum(subsidy_amount) FILTER(WHERE state='RESERVED'),0) reserved_b,
            coalesce(sum(subsidy_amount) FILTER(WHERE state='CONSUMED'),0) consumed_b,
            coalesce(sum(subsidy_amount) FILTER(WHERE state='RELEASED'),0) released_b
            FROM flash_sale_allocations WHERE flash_sale_id=?
            """,(r,n)->new Usage(r.getInt("q"),r.getBigDecimal("b"),r.getInt("reserved_q"),r.getInt("consumed_q"),
                r.getLong("released_q"),r.getBigDecimal("reserved_b"),r.getBigDecimal("consumed_b"),r.getBigDecimal("released_b")),id);
    }
    @Override public void allocate(UUID sale,UUID booking,UUID item,String pricing,Snapshot s) {
        db.update("""
            INSERT INTO flash_sale_allocations(id,flash_sale_id,booking_id,booking_item_id,state,pricing_unit,quantity,
            original_unit_price,original_amount,policy_id,policy_version,commission_rate,commission_amount,unit_subsidy,subsidy_amount,
            customer_payable,vendor_receivable,platform_contribution) VALUES (?,?,?,?,'RESERVED',?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),sale,booking,item,pricing,s.quantity(),s.originalUnitPrice(),s.originalAmount(),s.policyId(),s.policyVersion(),
                s.commissionRate(),s.commissionAmount(),s.unitSubsidy(),s.subsidyAmount(),s.customerPayable(),s.vendorReceivable(),s.platformContribution());
    }
    @Override public Optional<Snapshot> snapshot(UUID item) { return db.query("SELECT * FROM flash_sale_allocations WHERE booking_item_id=?",SNAPSHOT,item).stream().findFirst(); }
    @Override public boolean hasAllocation(UUID booking) { return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM flash_sale_allocations WHERE booking_id=?)",Boolean.class,booking)); }
    @Override public Request lockRequest(UUID customer,String key) {
        db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",customer+":"+key);
        return db.query("SELECT booking_id,fingerprint FROM flash_sale_hold_requests WHERE customer_id=? AND request_key=?",
                (r,n)->new Request(uuid(r,"booking_id"),r.getString("fingerprint")),customer,key).stream().findFirst().orElse(null);
    }
    @Override public void saveRequest(UUID customer,String key,String fingerprint,UUID booking) {
        db.update("INSERT INTO flash_sale_hold_requests(customer_id,request_key,fingerprint,booking_id) VALUES (?,?,?,?)",customer,key,fingerprint,booking);
    }
}
