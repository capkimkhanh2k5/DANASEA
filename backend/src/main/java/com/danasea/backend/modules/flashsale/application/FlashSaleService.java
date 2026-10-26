package com.danasea.backend.modules.flashsale.application;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.booking.domain.ports.FlashSaleBookingPort;
import com.danasea.backend.modules.flashsale.domain.*;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FlashSaleService implements FlashSaleBookingPort {
    private final FlashSaleStore store;
    private final AuditLogInternalApi audit;
    private final com.danasea.backend.modules.order.infrastructure.adapters.CommissionPolicyAdapter commissionPolicy;
    private Clock clock=Clock.systemUTC();
    @org.springframework.beans.factory.annotation.Autowired
    public FlashSaleService(FlashSaleStore store,AuditLogInternalApi audit,
            @org.springframework.beans.factory.annotation.Autowired(required=false) com.danasea.backend.modules.order.infrastructure.adapters.CommissionPolicyAdapter commissionPolicy) {
        this.store=store; this.audit=audit; this.commissionPolicy=commissionPolicy;
    }
    public FlashSaleService(FlashSaleStore store,AuditLogInternalApi audit) { this(store,audit,null); }
    public void setClock(Clock clock) { this.clock=Objects.requireNonNull(clock); }
    private Policy resolvePolicy(UUID vendorId) {
        if(commissionPolicy!=null) return commissionPolicy.resolveFlashSalePolicy(vendorId);
        return store.latestPolicy(vendorId);
    }
    public record Terms(UUID serviceId, UUID optionId, BigDecimal unitSubsidy, OffsetDateTime startsAt,
            OffsetDateTime endsAt, Integer quota, BigDecimal budget) {}
    public record PublicOffer(UUID flashSaleId,UUID serviceId,UUID serviceOptionId,String serviceName,String serviceImage,
            BigDecimal originalPrice,BigDecimal salePrice,BigDecimal actualDiscountAmount,BigDecimal actualDiscountPercent,
            Instant startsAt,Instant endsAt,int remainingQuota,String availability,String currency) {}
    public record AdminDetail(FlashSale flashSale,Snapshot financialPreview,FlashSaleStore.Usage usage,String policyIssue) {}
    public record Preview(FlashSaleStore.Option option,Snapshot financial,BigDecimal estimatedPlatformCost,
            BigDecimal minimumRetainedMargin,String policyProvenance,boolean eligible,List<String> warnings) {}
    public Policy configure(Policy p,UUID actor) {
        FlashSalePricing.validate(p);
        Policy saved=store.createPolicy(new Policy(UUID.randomUUID(),p.vendorId(),1,p.rate(),p.maximumRatio(),p.unitCost(),p.minimumMargin(),p.provenance()),actor);
        log(actor,"COMMISSION_POLICY_CREATED",saved.id()); return saved;
    }
    @Transactional(readOnly=true) public List<Policy> policies(UUID vendor) { return store.policies(vendor); }
    @Transactional(readOnly=true) public List<FlashSaleStore.Option> eligible(int page,int size) { page(page,size); return store.eligible(page,size); }
    @Transactional(readOnly=true) public Preview preview(UUID service,UUID option,BigDecimal subsidy) {
        var o=eligibleOption(service,option); var policy=resolvePolicy(o.vendorId());
        return new Preview(o,FlashSalePricing.calculate(o.price(),1,policy,subsidy),policy.unitCost(),policy.minimumMargin(),policy.provenance(),true,List.of());
    }
    public AdminDetail create(Terms t,UUID actor) {
        validate(t); eligibleOption(t.serviceId(),t.optionId());
        var s=new FlashSale(UUID.randomUUID(),t.serviceId(),t.optionId(),"DRAFT",t.unitSubsidy(),t.startsAt().toInstant(),
                t.endsAt().toInstant(),t.quota(),t.budget(),null,null,actor);
        store.save(s); log(actor,"FLASH_SALE_CREATED",s.id()); return detail(s.id());
    }
    public AdminDetail update(UUID id,Terms t,UUID actor) {
        var old=store.sale(id,true); draft(old); validate(t); eligibleOption(t.serviceId(),t.optionId());
        store.save(new FlashSale(id,t.serviceId(),t.optionId(),"DRAFT",t.unitSubsidy(),t.startsAt().toInstant(),t.endsAt().toInstant(),
                t.quota(),t.budget(),null,null,old.createdBy()));
        log(actor,"FLASH_SALE_UPDATED",id); return detail(id);
    }
    public AdminDetail activate(UUID id,UUID actor) {
        var s=store.sale(id,true);
        if("ENABLED".equals(s.status())) return detail(id);
        draft(s);
        if(!clock.instant().isBefore(s.endsAt())) throw new FlashSaleException("FLASH_SALE_EXPIRED");
        var option=eligibleOption(s.serviceId(),s.optionId());
        var policy=resolvePolicy(option.vendorId());
        var snapshot=FlashSalePricing.calculate(option.price(),1,policy,s.unitSubsidy());
        if(s.budget().compareTo(snapshot.subsidyAmount())<0) throw new FlashSaleException("BUDGET_BELOW_ONE_UNIT");
        store.save(s.enabled(option.price(),policy.id())); log(actor,"FLASH_SALE_ACTIVATED",id); return detail(id);
    }
    public AdminDetail cancel(UUID id,UUID actor) {
        var s=store.sale(id,true);
        if(!"CANCELLED".equals(s.status())) { store.save(s.cancelled()); log(actor,"FLASH_SALE_CANCELLED",id); }
        return detail(id);
    }
    public void delete(UUID id,UUID actor) { draft(store.sale(id,true)); store.delete(id); log(actor,"FLASH_SALE_DELETED",id); }
    @Transactional(readOnly=true) public AdminDetail detail(UUID id) {
        var s=store.sale(id,false); Snapshot snapshot=null; String issue=null;
        try { var o=eligibleOption(s.serviceId(),s.optionId());
            Policy policy=resolvePolicy(o.vendorId());
            snapshot=FlashSalePricing.calculate(s.originalUnitPrice()!=null?s.originalUnitPrice():o.price(),1,policy,s.unitSubsidy());
        } catch(FlashSaleException e) { issue=e.code(); }
        return new AdminDetail(s,snapshot,store.usage(id),issue);
    }
    @Transactional(readOnly=true) public List<AdminDetail> list(int page,int size) {
        page(page,size); return store.sales(page,size).stream().map(s->detail(s.id())).toList();
    }
    @Transactional(readOnly=true) public List<PublicOffer> active() {
        return store.enabledSales().stream().filter(s->s.activeAt(clock.instant())).map(this::offer)
                .filter(Objects::nonNull).filter(o->"AVAILABLE".equals(o.availability())).toList();
    }
    @Transactional(readOnly=true) public PublicOffer publicDetail(UUID id) {
        var s=store.sale(id,false);
        if(!s.activeAt(clock.instant())) throw new FlashSaleException("FLASH_SALE_NOT_FOUND");
        var o=offer(s); if(o==null) throw new FlashSaleException("FLASH_SALE_NOT_FOUND"); return o;
    }
    private PublicOffer offer(FlashSale s) {
        var o=store.option(s.serviceId(),s.optionId());
        if(!o.eligible() || s.originalUnitPrice()==null || o.price().compareTo(s.originalUnitPrice())!=0) return null;
        var use=store.usage(s.id());
        int remaining=Math.max(0,Math.min(s.quota()-use.quantity(),s.budget().subtract(use.budget())
                .divide(s.unitSubsidy(),0,RoundingMode.FLOOR).min(BigDecimal.valueOf(Integer.MAX_VALUE)).intValue()));
        return new PublicOffer(s.id(),s.serviceId(),s.optionId(),o.name(),o.image(),s.originalUnitPrice(),
                s.originalUnitPrice().subtract(s.unitSubsidy()),s.unitSubsidy(),s.unitSubsidy().multiply(BigDecimal.valueOf(100))
                .divide(s.originalUnitPrice(),4,RoundingMode.HALF_UP),s.startsAt(),s.endsAt(),remaining,remaining>0?"AVAILABLE":"SOLD_OUT","VND");
    }
    @Override public void lockOffers(List<UUID> ids) { ids.stream().distinct().sorted().forEach(id->store.sale(id,true)); }
    @Override public UUID replay(UUID customer,String key,String fingerprint) {
        key(key); var old=store.lockRequest(customer,key);
        if(old==null) return null;
        if(!old.fingerprint().equals(fingerprint)) throw new FlashSaleException("IDEMPOTENCY_KEY_REUSED");
        return old.bookingId();
    }
    @Override public void remember(UUID customer,String key,String fingerprint,UUID booking) { key(key); store.saveRequest(customer,key,fingerprint,booking); }
    @Override public Snapshot reserve(UUID sale,UUID service,UUID option,BigDecimal verifiedPrice,String pricing,int quantity,UUID booking,UUID item) {
        var s=store.sale(sale,true);
        if(!s.serviceId().equals(service) || !s.optionId().equals(option)) throw new FlashSaleException("FLASH_SALE_OPTION_MISMATCH");
        if(!s.activeAt(clock.instant())) throw new FlashSaleException("FLASH_SALE_UNAVAILABLE");
        var o=eligibleOption(service,option);
        if(o.price().compareTo(verifiedPrice)!=0 || o.price().compareTo(s.originalUnitPrice())!=0 || !o.pricingUnit().equals(pricing))
            throw new FlashSaleException("FLASH_SALE_PRICE_CHANGED");
        Policy policy=resolvePolicy(o.vendorId());
        var snapshot=FlashSalePricing.calculate(s.originalUnitPrice(),quantity,policy,s.unitSubsidy());
        var usage=store.usage(s.id());
        if(quantity>s.quota()-usage.quantity()) throw new FlashSaleException("FLASH_SALE_QUOTA_EXHAUSTED");
        if(snapshot.subsidyAmount().compareTo(s.budget().subtract(usage.budget()))>0) throw new FlashSaleException("FLASH_SALE_BUDGET_EXHAUSTED");
        store.allocate(s.id(),booking,item,pricing,snapshot); return snapshot;
    }
    @Override @Transactional(readOnly=true) public Optional<Snapshot> snapshot(UUID item) { return store.snapshot(item); }
    @Override @Transactional(readOnly=true) public boolean hasAllocation(UUID booking) { return store.hasAllocation(booking); }
    private FlashSaleStore.Option eligibleOption(UUID service,UUID option) {
        var o=store.option(service,option); if(!o.eligible()) throw new FlashSaleException("FLASH_SALE_INELIGIBLE_OPTION"); return o;
    }
    private void validate(Terms t) {
        if(t==null || t.serviceId()==null || t.optionId()==null || t.startsAt()==null || t.endsAt()==null || t.quota()==null
                || t.quota()<=0 || !t.startsAt().isBefore(t.endsAt()) || !t.endsAt().toInstant().isAfter(clock.instant()))
            throw new FlashSaleException("INVALID_FLASH_SALE_TERMS");
        FlashSalePricing.money(t.unitSubsidy()); FlashSalePricing.money(t.budget());
        if(t.unitSubsidy().signum()<=0 || t.budget().signum()<=0) throw new FlashSaleException("INVALID_FLASH_SALE_TERMS");
    }
    private static void key(String key) { if(key==null || key.isBlank() || key.length()>120) throw new FlashSaleException("FLASH_SALE_IDEMPOTENCY_KEY_REQUIRED"); }
    private static void page(int page,int size) { if(page<0 || page>100000 || size<1 || size>100) throw new FlashSaleException("INVALID_PAGE"); }
    private static void draft(FlashSale s) { if(!"DRAFT".equals(s.status())) throw new FlashSaleException("FLASH_SALE_TERMS_IMMUTABLE"); }
    private void log(UUID actor,String action,UUID id) { audit.recordAuditLog(actor,action,"FLASH_SALE",id,action); }
}
