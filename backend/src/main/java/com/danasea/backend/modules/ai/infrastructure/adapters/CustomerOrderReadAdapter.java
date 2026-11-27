package com.danasea.backend.modules.ai.infrastructure.adapters;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.ai.application.ports.CustomerOrderReadPort;
import com.danasea.backend.modules.order.application.dtos.CancellationPreviewResult;
import com.danasea.backend.modules.order.application.dtos.GetCancellationPreviewQuery;
import com.danasea.backend.modules.order.application.dtos.GetOrderDetailQuery;
import com.danasea.backend.modules.order.application.dtos.MasterOrderDetailResult;
import com.danasea.backend.modules.order.application.dtos.SubOrderDetailResult;
import com.danasea.backend.modules.order.application.usecases.GetCancellationPreviewUseCase;
import com.danasea.backend.modules.order.application.usecases.GetOrderDetailUseCase;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.services.CustomerRefundEligibilityPolicy;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.application.api.CustomerChangeSlotReadApi;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.shared.util.DisplayCodeFormatter;

@Component
public class CustomerOrderReadAdapter implements CustomerOrderReadPort {
    private final GetOrderDetailUseCase orders;
    private final GetCancellationPreviewUseCase cancellations;
    private final CustomerChangeSlotReadApi changeSlots;
    private final JpaMasterOrderRepository masterOrderRepo;
    private final JpaSubOrderRepository subOrderRepo;
    private final JpaServiceRepository serviceRepo;
    private final JpaServiceSlotRepository slotRepo;

    public CustomerOrderReadAdapter(
            GetOrderDetailUseCase orders,
            GetCancellationPreviewUseCase cancellations,
            CustomerChangeSlotReadApi changeSlots
    ) {
        this(orders, cancellations, changeSlots, null, null, null, null);
    }

    @Autowired
    public CustomerOrderReadAdapter(
            GetOrderDetailUseCase orders,
            GetCancellationPreviewUseCase cancellations,
            CustomerChangeSlotReadApi changeSlots,
            @Autowired(required = false) JpaMasterOrderRepository masterOrderRepo,
            @Autowired(required = false) JpaSubOrderRepository subOrderRepo,
            @Autowired(required = false) JpaServiceRepository serviceRepo,
            @Autowired(required = false) JpaServiceSlotRepository slotRepo
    ) {
        this.orders = orders;
        this.cancellations = cancellations;
        this.changeSlots = changeSlots;
        this.masterOrderRepo = masterOrderRepo;
        this.subOrderRepo = subOrderRepo;
        this.serviceRepo = serviceRepo;
        this.slotRepo = slotRepo;
    }

    @Override
    public OwnedOrder read(UUID userId, UUID orderId) {
        UUID masterOrderId = resolveMasterOrderId(orderId);
        MasterOrderDetailResult order = orders.execute(new GetOrderDetailQuery(userId, masterOrderId, false));
        return toOwnedOrder(order);
    }

    @Override
    public List<OwnedOrder> listRecent(UUID userId, int limit) {
        if (userId == null || masterOrderRepo == null || subOrderRepo == null) {
            return List.of();
        }
        int bounded = Math.max(1, Math.min(limit, 20));
        List<MasterOrderJpaEntity> masters = masterOrderRepo.findByCustomerIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, bounded));
        if (masters.isEmpty()) {
            return List.of();
        }
        List<UUID> masterIds = masters.stream().map(MasterOrderJpaEntity::getId).toList();
        Map<UUID, List<SubOrderJpaEntity>> subsByMaster = subOrderRepo.findByMasterOrderIdIn(masterIds).stream()
                .collect(Collectors.groupingBy(SubOrderJpaEntity::getMasterOrderId));
        Map<UUID, ServiceJpaEntity> services = loadServices(subsByMaster.values().stream()
                .flatMap(List::stream).map(SubOrderJpaEntity::getServiceId).toList());
        Map<UUID, ServiceSlotJpaEntity> slots = loadSlots(subsByMaster.values().stream()
                .flatMap(List::stream).map(so -> so.getActualSlotId() != null ? so.getActualSlotId() : so.getSlotId()).toList());

        return masters.stream().map(mo -> {
            List<SubOrderJpaEntity> subs = subsByMaster.getOrDefault(mo.getId(), List.of());
            List<Item> items = subs.stream().map(so -> {
                ServiceJpaEntity svc = so.getServiceId() == null ? null : services.get(so.getServiceId());
                UUID slotId = so.getActualSlotId() != null ? so.getActualSlotId() : so.getSlotId();
                ServiceSlotJpaEntity slot = slotId == null ? null : slots.get(slotId);
                String subCode = (so.getSubOrderCode() != null && !so.getSubOrderCode().isBlank())
                        ? so.getSubOrderCode() : DisplayCodeFormatter.format(so.getId(), "SUB");
                return new Item(
                        so.getId(),
                        subCode,
                        so.getServiceId(),
                        svc == null ? null : svc.getName(),
                        so.getQuantity() == null ? 0 : so.getQuantity(),
                        so.getUnitPrice(),
                        so.getFinalAmount() != null ? so.getFinalAmount() : so.getSubtotalAmount(),
                        so.getStatus() == null ? "UNKNOWN" : so.getStatus().name(),
                        slot == null ? null : slot.getDate(),
                        slot == null ? null : slot.getStartTime(),
                        slot == null ? null : slot.getEndTime(),
                        svc == null ? null : (svc.getLocationName() != null ? svc.getLocationName() : svc.getAddress()),
                        so.getCheckedInAt()
                );
            }).toList();
            String orderCode = (mo.getOrderCode() != null && !mo.getOrderCode().isBlank())
                    ? mo.getOrderCode() : DisplayCodeFormatter.format(mo.getId(), "ORD");
            return new OwnedOrder(
                    mo.getId(),
                    orderCode,
                    mo.getStatus() == null ? "UNKNOWN" : mo.getStatus().name(),
                    mo.getPaymentStatus() == null ? "UNKNOWN" : mo.getPaymentStatus().name(),
                    mo.getTotalAmount(),
                    mo.getCreatedAt(),
                    items
            );
        }).toList();
    }

    @Override
    public CancellationPreviewResult previewCancellation(UUID userId, UUID orderId) {
        UUID masterOrderId = resolveMasterOrderId(orderId);
        return cancellations.execute(new GetCancellationPreviewQuery(userId, masterOrderId, false));
    }

    @Override
    public void validateChangeTarget(UUID userId, UUID orderId, UUID slotId, LocalDate requestedDate) {
        if (slotId == null) return;
        UUID masterOrderId = resolveMasterOrderId(orderId);
        var order = orders.execute(new GetOrderDetailQuery(userId, masterOrderId, false));
        var target = changeSlots.read(slotId);
        if (order.subOrders().stream().noneMatch(item -> target.serviceId().equals(item.serviceId())
                && (item.status() == SubOrderStatus.PENDING || item.status() == SubOrderStatus.CONFIRMED))) {
            throw new IllegalArgumentException("Requested slot must belong to an owned order service");
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (!"OPEN".equals(target.status()) || target.date() == null || target.startTime() == null || target.date().isBefore(today)
                || target.date().isAfter(today.plusDays(90))
                || target.date().equals(today) && !target.startTime().isAfter(LocalTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new IllegalArgumentException("Requested slot is not open for a future date");
        }
        if (requestedDate != null && !requestedDate.equals(target.date())) {
            throw new IllegalArgumentException("Requested date must match the requested slot");
        }
    }

    @Override
    public Eligibility eligibility(UUID userId, UUID orderId, CancellationPreviewResult preview) {
        UUID masterOrderId = resolveMasterOrderId(orderId);
        var order = orders.execute(new GetOrderDetailQuery(userId, masterOrderId, false));
        boolean paid = CustomerRefundEligibilityPolicy.paidOrder(order.status());
        boolean validItems = !order.subOrders().isEmpty() && order.subOrders().stream()
                .allMatch(item -> CustomerRefundEligibilityPolicy.refundableItem(item.status()));
        boolean positivePolicy = preview != null && preview.items() != null && !preview.items().isEmpty()
                && preview.items().stream().allMatch(item -> item.refundAmount() != null && item.refundAmount().signum() > 0);
        boolean change = order.status() != MasterOrderStatus.CANCELLED && order.status() != MasterOrderStatus.COMPLETED
                && order.subOrders().stream().anyMatch(item -> item.status() == SubOrderStatus.PENDING || item.status() == SubOrderStatus.CONFIRMED);
        List<String> reasons = new ArrayList<>();
        if (!paid) reasons.add("ORDER_NOT_PAID");
        if (!validItems) reasons.add("ITEM_STATE_REQUIRES_SUPPORT_REVIEW");
        if (!positivePolicy) reasons.add("POSITIVE_REFUND_PREVIEW_REQUIRED");
        return new Eligibility(paid && validItems && positivePolicy, change,
                "CORE_REFUND_ELIGIBILITY_POLICY_AND_CURRENT_CANCELLATION_PREVIEW", List.copyOf(reasons));
    }

    private UUID resolveMasterOrderId(UUID orderOrSubOrderId) {
        if (orderOrSubOrderId == null || subOrderRepo == null) {
            return orderOrSubOrderId;
        }
        try {
            if (masterOrderRepo != null && masterOrderRepo.existsById(orderOrSubOrderId)) {
                return orderOrSubOrderId;
            }
            return subOrderRepo.findMasterOrderIdById(orderOrSubOrderId).orElse(orderOrSubOrderId);
        } catch (Exception ignored) {
            return orderOrSubOrderId;
        }
    }

    private OwnedOrder toOwnedOrder(MasterOrderDetailResult order) {
        List<SubOrderDetailResult> subs = order.subOrders() == null ? List.of() : order.subOrders();
        Map<UUID, SubOrderJpaEntity> subEntities = subOrderRepo == null ? Map.of()
                : subOrderRepo.findByMasterOrderId(order.id()).stream()
                        .collect(Collectors.toMap(SubOrderJpaEntity::getId, Function.identity(), (a, b) -> a));
        Map<UUID, ServiceJpaEntity> services = loadServices(subs.stream().map(SubOrderDetailResult::serviceId).toList());
        Map<UUID, ServiceSlotJpaEntity> slots = loadSlots(subs.stream().map(SubOrderDetailResult::slotId).toList());
        List<Item> items = subs.stream().map(item -> {
            SubOrderJpaEntity entity = subEntities.get(item.id());
            ServiceJpaEntity svc = item.serviceId() == null ? null : services.get(item.serviceId());
            ServiceSlotJpaEntity slot = item.slotId() == null ? null : slots.get(item.slotId());
            String subCode = (entity != null && entity.getSubOrderCode() != null && !entity.getSubOrderCode().isBlank())
                    ? entity.getSubOrderCode() : DisplayCodeFormatter.format(item.id(), "SUB");
            OffsetDateTime checkedInAt = entity == null ? null : entity.getCheckedInAt();
            return new Item(
                    item.id(),
                    subCode,
                    item.serviceId(),
                    svc == null ? null : svc.getName(),
                    item.quantity() == null ? 0 : item.quantity(),
                    item.unitPrice(),
                    item.subtotalAmount(),
                    item.status().name(),
                    slot == null ? null : slot.getDate(),
                    slot == null ? null : slot.getStartTime(),
                    slot == null ? null : slot.getEndTime(),
                    svc == null ? null : (svc.getLocationName() != null ? svc.getLocationName() : svc.getAddress()),
                    checkedInAt
            );
        }).toList();
        String orderCode = (order.orderCode() != null && !order.orderCode().isBlank())
                ? order.orderCode() : DisplayCodeFormatter.format(order.id(), "ORD");
        return new OwnedOrder(
                order.id(),
                orderCode,
                order.status().name(),
                order.paymentStatus() == null ? "UNKNOWN" : order.paymentStatus().name(),
                order.totalAmount(),
                order.createdAt(),
                items
        );
    }

    private Map<UUID, ServiceJpaEntity> loadServices(List<UUID> ids) {
        if (serviceRepo == null || ids == null) return Map.of();
        List<UUID> clean = ids.stream().filter(id -> id != null).distinct().toList();
        if (clean.isEmpty()) return Map.of();
        return serviceRepo.findAllById(clean).stream()
                .collect(Collectors.toMap(ServiceJpaEntity::getId, Function.identity()));
    }

    private Map<UUID, ServiceSlotJpaEntity> loadSlots(List<UUID> ids) {
        if (slotRepo == null || ids == null) return Map.of();
        List<UUID> clean = ids.stream().filter(id -> id != null).distinct().toList();
        if (clean.isEmpty()) return Map.of();
        return slotRepo.findAllById(clean).stream()
                .collect(Collectors.toMap(ServiceSlotJpaEntity::getId, Function.identity()));
    }
}
