package com.danasea.backend.modules.weather;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.application.services.VendorFaultCompensationService;
import com.danasea.backend.modules.order.application.services.RescheduleSupport;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderDisruptionJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRescheduleProposalRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.weather.application.services.WeatherBookingCancellationService;
import com.danasea.backend.modules.weather.infrastructure.persistence.repositories.JpaSafetyRuleEvaluationRepository;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

final class WeatherCancellationFixture {
    static void install(
            Object coordinator,
            JpaSubOrderRepository subs,
            JpaRefundRepository refunds,
            JpaServiceSlotRepository slots,
            UUID slotId) {
        var support = mock(RescheduleSupport.class);
        lenient()
                .when(support.lock(any()))
                .thenAnswer(
                        inv -> {
                            UUID id = inv.getArgument(0);
                            for (var sub : subs.findBySlotId(slotId)) {
                                if (id.equals(sub.getId())) {
                                    var master = new MasterOrderJpaEntity();
                                    master.setPaymentStatus(PaymentOrderStatus.PAID);
                                    master.setStatus(MasterOrderStatus.PAID);
                                    master.setCustomerId(UUID.randomUUID());
                                    return new RescheduleSupport.LockedOrder(master, sub);
                                }
                            }
                            throw new IllegalStateException("Booking fixture not found");
                        });
        var slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setDate(LocalDate.now().plusDays(2));
        slot.setStartTime(LocalTime.NOON);
        lenient().when(slots.findById(any())).thenReturn(Optional.of(slot));
        lenient().when(slots.findByIdForUpdate(any())).thenReturn(Optional.of(slot));
        var disruptions = mock(JpaSubOrderDisruptionRepository.class);
        lenient()
                .when(disruptions.saveAndFlush(any()))
                .thenAnswer(
                        inv -> {
                            var d = (SubOrderDisruptionJpaEntity) inv.getArgument(0);
                            if (d.getId() == null) d.setId(UUID.randomUUID());
                            return d;
                        });
        lenient().when(disruptions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient()
                .when(refunds.saveAndFlush(any()))
                .thenAnswer(
                        inv -> {
                            var r = (RefundJpaEntity) inv.getArgument(0);
                            if (r.getId() == null) r.setId(UUID.randomUUID());
                            return r;
                        });
        var decisions =
                new DisruptionDecisionService(
                        support,
                        disruptions,
                        subs,
                        refunds,
                        mock(JpaDiscountCodeRepository.class),
                        slots,
                        mock(JpaSubOrderRescheduleProposalRepository.class),
                        mock(JpaSafetyRuleEvaluationRepository.class),
                        mock(AuditLogInternalApi.class),
                        mock(SendNotificationUseCase.class),
                        mock(VendorFaultCompensationService.class));
        ReflectionTestUtils.setField(
                coordinator,
                "weatherCancellation",
                new WeatherBookingCancellationService(decisions));
    }
}
