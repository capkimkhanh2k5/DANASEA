package com.danasea.backend.modules.ai.application.services;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.shared.util.DisplayCodeFormatter;

/**
 * Resolves free-text entity references (raw 36-char UUIDs and standardized display codes such as
 * {@code #ORD-XXXXXXXX}, {@code #SUB-XXXXXXXX}, legacy {@code DNS-XXXXXXXX}, and {@code #DISP-XXXXXXXX})
 * with strict actor-scoping to prevent IDOR across customers and vendors.
 */
@Service
public class AiEntityReferenceResolver {

    public static final Pattern UUID_PATTERN = Pattern.compile(
            "(?i)(?<![0-9a-f])[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}(?![0-9a-f])");

    public static final Pattern DISPLAY_CODE_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9])#?(ORD|SUB|DNS|DISP|SRV|SLOT|REV|REF|PAY|STL|VND)-([0-9a-f]{6,12}(?:-\\d+)?)(?![a-z0-9-])");

    private static final Set<String> ORDER_PREFIXES = Set.of("ORD", "SUB", "DNS");
    private static final Set<String> DISPUTE_PREFIXES = Set.of("DISP");

    public record ResolvedOrder(
            UUID masterOrderId,
            UUID subOrderId,
            String displayCode,
            boolean resolvedFromSubOrder
    ) {}

    public record ExtractedCode(
            String raw,
            String prefix,
            String hexPart,
            String normalizedCode
    ) {}

    private final JpaMasterOrderRepository masterOrderRepo;
    private final JpaSubOrderRepository subOrderRepo;
    private final JpaDisputeRepository disputeRepo;

    @Autowired
    public AiEntityReferenceResolver(
            @Autowired(required = false) JpaMasterOrderRepository masterOrderRepo,
            @Autowired(required = false) JpaSubOrderRepository subOrderRepo,
            @Autowired(required = false) JpaDisputeRepository disputeRepo
    ) {
        this.masterOrderRepo = masterOrderRepo;
        this.subOrderRepo = subOrderRepo;
        this.disputeRepo = disputeRepo;
    }

    public List<UUID> extractUuids(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<UUID> found = new LinkedHashSet<>();
        Matcher m = UUID_PATTERN.matcher(text);
        while (m.find()) {
            try {
                found.add(UUID.fromString(m.group()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return List.copyOf(found);
    }

    public List<ExtractedCode> extractCodes(String text, Set<String> allowedPrefixes) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ExtractedCode> codes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = DISPLAY_CODE_PATTERN.matcher(text);
        while (m.find()) {
            String prefix = m.group(1).toUpperCase(Locale.ROOT);
            if (allowedPrefixes != null && !allowedPrefixes.contains(prefix)) {
                continue;
            }
            String hexPart = m.group(2).toUpperCase(Locale.ROOT);
            String normalized = prefix + "-" + hexPart;
            if (seen.add(normalized)) {
                codes.add(new ExtractedCode(m.group(), prefix, hexPart, normalized));
            }
        }
        return codes;
    }

    public boolean containsOrderLikeReference(String text) {
        return !extractUuids(text).isEmpty() || !extractCodes(text, ORDER_PREFIXES).isEmpty();
    }

    public boolean containsOrderOrDisputeCode(String text) {
        return !extractUuids(text).isEmpty()
                || !extractCodes(text, Set.of("ORD", "SUB", "DNS", "DISP")).isEmpty();
    }

    public static String stripUuids(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return UUID_PATTERN.matcher(text).replaceAll(" ");
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedOrder> resolveOrderForCustomer(UUID customerId, String rawRefOrText) {
        if (customerId == null || rawRefOrText == null || rawRefOrText.isBlank()) {
            return Optional.empty();
        }
        for (UUID uuid : extractUuids(rawRefOrText)) {
            Optional<ResolvedOrder> byUuid = resolveCustomerOrderByUuid(customerId, uuid);
            if (byUuid.isPresent()) {
                return byUuid;
            }
        }
        for (ExtractedCode code : extractCodes(rawRefOrText, ORDER_PREFIXES)) {
            if ("SUB".equals(code.prefix())) {
                Optional<ResolvedOrder> bySub = resolveCustomerSubOrderByCode(customerId, code);
                if (bySub.isPresent()) {
                    return bySub;
                }
            } else {
                Optional<ResolvedOrder> byMaster = resolveCustomerMasterOrderByCode(customerId, code);
                if (byMaster.isPresent()) {
                    return byMaster;
                }
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedOrder> resolveOrderForVendor(UUID vendorId, String rawRefOrText) {
        if (vendorId == null || rawRefOrText == null || rawRefOrText.isBlank() || subOrderRepo == null) {
            return Optional.empty();
        }
        for (UUID uuid : extractUuids(rawRefOrText)) {
            Optional<SubOrderJpaEntity> directSub = subOrderRepo.findById(uuid)
                    .filter(so -> vendorId.equals(so.getVendorId()));
            if (directSub.isPresent()) {
                SubOrderJpaEntity so = directSub.get();
                return Optional.of(new ResolvedOrder(so.getMasterOrderId(), so.getId(), effectiveSubCode(so), true));
            }
            Optional<SubOrderJpaEntity> fromMaster = subOrderRepo.findByMasterOrderId(uuid).stream()
                    .filter(so -> vendorId.equals(so.getVendorId()))
                    .findFirst();
            if (fromMaster.isPresent()) {
                SubOrderJpaEntity so = fromMaster.get();
                return Optional.of(new ResolvedOrder(so.getMasterOrderId(), so.getId(), effectiveSubCode(so), false));
            }
        }
        for (ExtractedCode code : extractCodes(rawRefOrText, ORDER_PREFIXES)) {
            if ("SUB".equals(code.prefix())) {
                Optional<UUID> seedUuid = DisplayCodeFormatter.tryReconstructSeedUuid(code.normalizedCode());
                if (seedUuid.isPresent()) {
                    Optional<SubOrderJpaEntity> seeded = subOrderRepo.findById(seedUuid.get())
                            .filter(so -> vendorId.equals(so.getVendorId()));
                    if (seeded.isPresent()) {
                        return Optional.of(new ResolvedOrder(seeded.get().getMasterOrderId(), seeded.get().getId(),
                                effectiveSubCode(seeded.get()), true));
                    }
                }
                Optional<SubOrderJpaEntity> exact = subOrderRepo.findBySubOrderCodeIgnoreCase(code.normalizedCode())
                        .filter(so -> vendorId.equals(so.getVendorId()));
                if (exact.isPresent()) {
                    return Optional.of(new ResolvedOrder(exact.get().getMasterOrderId(), exact.get().getId(),
                            effectiveSubCode(exact.get()), true));
                }
                List<SubOrderJpaEntity> recent = subOrderRepo.findByVendorIdOrderByCreatedAtDesc(
                        vendorId, PageRequest.of(0, 100));
                for (SubOrderJpaEntity so : recent) {
                    if (matchesSubCode(so, code)) {
                        return Optional.of(new ResolvedOrder(so.getMasterOrderId(), so.getId(), effectiveSubCode(so), true));
                    }
                }
            } else {
                String ordCode = toOrdCode(code);
                Optional<UUID> seedUuid = DisplayCodeFormatter.tryReconstructSeedUuid(ordCode);
                if (seedUuid.isPresent()) {
                    Optional<SubOrderJpaEntity> seeded = subOrderRepo.findByMasterOrderId(seedUuid.get()).stream()
                            .filter(so -> vendorId.equals(so.getVendorId()))
                            .findFirst();
                    if (seeded.isPresent()) {
                        return Optional.of(new ResolvedOrder(seeded.get().getMasterOrderId(), seeded.get().getId(), ordCode, false));
                    }
                }
                if (masterOrderRepo != null) {
                    Optional<MasterOrderJpaEntity> exact = masterOrderRepo.findByOrderCodeIgnoreCase(ordCode);
                    if (exact.isPresent()) {
                        Optional<SubOrderJpaEntity> ownedSub = subOrderRepo.findByMasterOrderId(exact.get().getId()).stream()
                                .filter(so -> vendorId.equals(so.getVendorId()))
                                .findFirst();
                        if (ownedSub.isPresent()) {
                            return Optional.of(new ResolvedOrder(exact.get().getId(), ownedSub.get().getId(),
                                    effectiveOrderCode(exact.get()), false));
                        }
                    }
                }
                List<SubOrderJpaEntity> recent = subOrderRepo.findByVendorIdOrderByCreatedAtDesc(
                        vendorId, PageRequest.of(0, 100));
                List<UUID> masterIds = recent.stream().map(SubOrderJpaEntity::getMasterOrderId).distinct().toList();
                if (masterOrderRepo != null && !masterIds.isEmpty()) {
                    Map<UUID, MasterOrderJpaEntity> masters = masterOrderRepo.findAllById(masterIds).stream()
                            .collect(Collectors.toMap(MasterOrderJpaEntity::getId, Function.identity()));
                    for (SubOrderJpaEntity so : recent) {
                        MasterOrderJpaEntity mo = masters.get(so.getMasterOrderId());
                        if (mo != null && matchesMasterCode(mo, code)) {
                            return Optional.of(new ResolvedOrder(mo.getId(), so.getId(), effectiveOrderCode(mo), false));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedOrder> resolveOrderForAdmin(String rawRefOrText) {
        if (rawRefOrText == null || rawRefOrText.isBlank()) {
            return Optional.empty();
        }
        for (UUID uuid : extractUuids(rawRefOrText)) {
            if (masterOrderRepo != null) {
                Optional<MasterOrderJpaEntity> mo = masterOrderRepo.findById(uuid);
                if (mo.isPresent()) {
                    return Optional.of(new ResolvedOrder(mo.get().getId(), null, effectiveOrderCode(mo.get()), false));
                }
            }
            if (subOrderRepo != null) {
                Optional<SubOrderJpaEntity> so = subOrderRepo.findById(uuid);
                if (so.isPresent()) {
                    return Optional.of(new ResolvedOrder(so.get().getMasterOrderId(), so.get().getId(),
                            effectiveSubCode(so.get()), true));
                }
            }
        }
        for (ExtractedCode code : extractCodes(rawRefOrText, ORDER_PREFIXES)) {
            if ("SUB".equals(code.prefix()) && subOrderRepo != null) {
                Optional<UUID> seedUuid = DisplayCodeFormatter.tryReconstructSeedUuid(code.normalizedCode());
                if (seedUuid.isPresent()) {
                    Optional<SubOrderJpaEntity> seeded = subOrderRepo.findById(seedUuid.get());
                    if (seeded.isPresent()) {
                        return Optional.of(new ResolvedOrder(seeded.get().getMasterOrderId(), seeded.get().getId(),
                                effectiveSubCode(seeded.get()), true));
                    }
                }
                Optional<SubOrderJpaEntity> exact = subOrderRepo.findBySubOrderCodeIgnoreCase(code.normalizedCode());
                if (exact.isPresent()) {
                    return Optional.of(new ResolvedOrder(exact.get().getMasterOrderId(), exact.get().getId(),
                            effectiveSubCode(exact.get()), true));
                }
                for (SubOrderJpaEntity so : subOrderRepo.findAll(
                        PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt")))) {
                    if (matchesSubCode(so, code)) {
                        return Optional.of(new ResolvedOrder(so.getMasterOrderId(), so.getId(), effectiveSubCode(so), true));
                    }
                }
            } else if (masterOrderRepo != null) {
                String ordCode = toOrdCode(code);
                Optional<UUID> seedUuid = DisplayCodeFormatter.tryReconstructSeedUuid(ordCode);
                if (seedUuid.isPresent()) {
                    Optional<MasterOrderJpaEntity> seeded = masterOrderRepo.findById(seedUuid.get());
                    if (seeded.isPresent()) {
                        return Optional.of(new ResolvedOrder(seeded.get().getId(), null, effectiveOrderCode(seeded.get()), false));
                    }
                }
                Optional<MasterOrderJpaEntity> exact = masterOrderRepo.findByOrderCodeIgnoreCase(ordCode);
                if (exact.isPresent()) {
                    return Optional.of(new ResolvedOrder(exact.get().getId(), null, effectiveOrderCode(exact.get()), false));
                }
                for (MasterOrderJpaEntity mo : masterOrderRepo.findAll(
                        PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt")))) {
                    if (matchesMasterCode(mo, code)) {
                        return Optional.of(new ResolvedOrder(mo.getId(), null, effectiveOrderCode(mo), false));
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<UUID> resolveDisputeForAdmin(String rawRefOrText) {
        return resolveDisputeInternal(null, rawRefOrText);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> resolveDisputeForVendor(UUID vendorId, String rawRefOrText) {
        if (vendorId == null) {
            return Optional.empty();
        }
        return resolveDisputeInternal(vendorId, rawRefOrText);
    }

    private Optional<UUID> resolveDisputeInternal(UUID vendorIdOrNull, String rawRefOrText) {
        if (disputeRepo == null || rawRefOrText == null || rawRefOrText.isBlank()) {
            return Optional.empty();
        }
        for (UUID uuid : extractUuids(rawRefOrText)) {
            Optional<DisputeJpaEntity> disputeOpt = disputeRepo.findById(uuid);
            if (disputeOpt.isPresent() && isDisputeInScope(disputeOpt.get(), vendorIdOrNull)) {
                return Optional.of(disputeOpt.get().getId());
            }
        }
        for (ExtractedCode code : extractCodes(rawRefOrText, DISPUTE_PREFIXES)) {
            Optional<UUID> seedUuid = DisplayCodeFormatter.tryReconstructSeedUuid(code.normalizedCode());
            if (seedUuid.isPresent()) {
                Optional<DisputeJpaEntity> seeded = disputeRepo.findById(seedUuid.get());
                if (seeded.isPresent() && isDisputeInScope(seeded.get(), vendorIdOrNull)) {
                    return Optional.of(seeded.get().getId());
                }
            }
            for (DisputeJpaEntity d : disputeRepo.findAll(
                    PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt")))) {
                if (DisplayCodeFormatter.format(d.getId(), "DISP").equalsIgnoreCase(code.normalizedCode())
                        && isDisputeInScope(d, vendorIdOrNull)) {
                    return Optional.of(d.getId());
                }
            }
        }
        return Optional.empty();
    }

    private boolean isDisputeInScope(DisputeJpaEntity dispute, UUID vendorIdOrNull) {
        if (vendorIdOrNull == null) {
            return true;
        }
        if (dispute.getSubOrderId() == null || subOrderRepo == null) {
            return false;
        }
        return subOrderRepo.findById(dispute.getSubOrderId())
                .map(so -> vendorIdOrNull.equals(so.getVendorId()))
                .orElse(false);
    }

    private Optional<ResolvedOrder> resolveCustomerOrderByUuid(UUID customerId, UUID uuid) {
        if (masterOrderRepo != null) {
            Optional<MasterOrderJpaEntity> mo = masterOrderRepo.findById(uuid)
                    .filter(order -> customerId.equals(order.getCustomerId()));
            if (mo.isPresent()) {
                return Optional.of(new ResolvedOrder(mo.get().getId(), null, effectiveOrderCode(mo.get()), false));
            }
        }
        if (subOrderRepo != null && masterOrderRepo != null) {
            Optional<SubOrderJpaEntity> so = subOrderRepo.findById(uuid);
            if (so.isPresent()) {
                Optional<MasterOrderJpaEntity> parent = masterOrderRepo.findById(so.get().getMasterOrderId())
                        .filter(order -> customerId.equals(order.getCustomerId()));
                if (parent.isPresent()) {
                    return Optional.of(new ResolvedOrder(parent.get().getId(), so.get().getId(),
                            effectiveSubCode(so.get()), true));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedOrder> resolveCustomerMasterOrderByCode(UUID customerId, ExtractedCode code) {
        if (masterOrderRepo == null) {
            return Optional.empty();
        }
        String ordCode = toOrdCode(code);
        Optional<UUID> seedUuid = DisplayCodeFormatter.tryReconstructSeedUuid(ordCode);
        if (seedUuid.isPresent()) {
            Optional<MasterOrderJpaEntity> seeded = masterOrderRepo.findById(seedUuid.get())
                    .filter(mo -> customerId.equals(mo.getCustomerId()));
            if (seeded.isPresent()) {
                return Optional.of(new ResolvedOrder(seeded.get().getId(), null, effectiveOrderCode(seeded.get()), false));
            }
        }
        Optional<MasterOrderJpaEntity> exact = masterOrderRepo.findByCustomerIdAndOrderCodeIgnoreCase(customerId, ordCode);
        if (exact.isPresent()) {
            return Optional.of(new ResolvedOrder(exact.get().getId(), null, effectiveOrderCode(exact.get()), false));
        }
        List<MasterOrderJpaEntity> owned = masterOrderRepo.findByCustomerIdOrderByCreatedAtDesc(
                customerId, PageRequest.of(0, 100));
        for (MasterOrderJpaEntity mo : owned) {
            if (matchesMasterCode(mo, code)) {
                return Optional.of(new ResolvedOrder(mo.getId(), null, effectiveOrderCode(mo), false));
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedOrder> resolveCustomerSubOrderByCode(UUID customerId, ExtractedCode code) {
        if (masterOrderRepo == null || subOrderRepo == null) {
            return Optional.empty();
        }
        Optional<UUID> seedUuid = DisplayCodeFormatter.tryReconstructSeedUuid(code.normalizedCode());
        if (seedUuid.isPresent()) {
            Optional<SubOrderJpaEntity> seeded = subOrderRepo.findById(seedUuid.get());
            if (seeded.isPresent()) {
                Optional<MasterOrderJpaEntity> parent = masterOrderRepo.findById(seeded.get().getMasterOrderId())
                        .filter(mo -> customerId.equals(mo.getCustomerId()));
                if (parent.isPresent()) {
                    return Optional.of(new ResolvedOrder(parent.get().getId(), seeded.get().getId(),
                            effectiveSubCode(seeded.get()), true));
                }
            }
        }
        Optional<SubOrderJpaEntity> exact = subOrderRepo.findBySubOrderCodeIgnoreCase(code.normalizedCode());
        if (exact.isPresent()) {
            Optional<MasterOrderJpaEntity> parent = masterOrderRepo.findById(exact.get().getMasterOrderId())
                    .filter(mo -> customerId.equals(mo.getCustomerId()));
            if (parent.isPresent()) {
                return Optional.of(new ResolvedOrder(parent.get().getId(), exact.get().getId(),
                        effectiveSubCode(exact.get()), true));
            }
        }
        List<MasterOrderJpaEntity> ownedMasters = masterOrderRepo.findByCustomerIdOrderByCreatedAtDesc(
                customerId, PageRequest.of(0, 50));
        if (ownedMasters.isEmpty()) {
            return Optional.empty();
        }
        List<UUID> masterIds = ownedMasters.stream().map(MasterOrderJpaEntity::getId).toList();
        for (SubOrderJpaEntity so : subOrderRepo.findByMasterOrderIdIn(masterIds)) {
            if (matchesSubCode(so, code)) {
                return Optional.of(new ResolvedOrder(so.getMasterOrderId(), so.getId(), effectiveSubCode(so), true));
            }
        }
        return Optional.empty();
    }

    private static String toOrdCode(ExtractedCode code) {
        return "DNS".equals(code.prefix()) ? "ORD-" + code.hexPart() : code.normalizedCode();
    }

    private static boolean matchesMasterCode(MasterOrderJpaEntity mo, ExtractedCode code) {
        String target = toOrdCode(code);
        if (mo.getOrderCode() != null && mo.getOrderCode().equalsIgnoreCase(target)) {
            return true;
        }
        if (DisplayCodeFormatter.format(mo.getId(), "ORD").equalsIgnoreCase(target)) {
            return true;
        }
        return "DNS".equals(code.prefix()) && mo.getId() != null
                && mo.getId().toString().replace("-", "").toUpperCase(Locale.ROOT).startsWith(code.hexPart());
    }

    private static boolean matchesSubCode(SubOrderJpaEntity so, ExtractedCode code) {
        if (so.getSubOrderCode() != null && so.getSubOrderCode().equalsIgnoreCase(code.normalizedCode())) {
            return true;
        }
        if (DisplayCodeFormatter.format(so.getId(), "SUB").equalsIgnoreCase(code.normalizedCode())) {
            return true;
        }
        return code.hexPart().length() == 6 && so.getId() != null
                && so.getId().toString().replace("-", "").toUpperCase(Locale.ROOT).startsWith(code.hexPart());
    }

    private static String effectiveOrderCode(MasterOrderJpaEntity mo) {
        return (mo.getOrderCode() != null && !mo.getOrderCode().isBlank())
                ? mo.getOrderCode()
                : DisplayCodeFormatter.format(mo.getId(), "ORD");
    }

    private static String effectiveSubCode(SubOrderJpaEntity so) {
        return (so.getSubOrderCode() != null && !so.getSubOrderCode().isBlank())
                ? so.getSubOrderCode()
                : DisplayCodeFormatter.format(so.getId(), "SUB");
    }
}
