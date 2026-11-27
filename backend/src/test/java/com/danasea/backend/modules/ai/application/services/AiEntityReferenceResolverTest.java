package com.danasea.backend.modules.ai.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.shared.util.DisplayCodeFormatter;

class AiEntityReferenceResolverTest {

    private final JpaMasterOrderRepository masterRepo = mock(JpaMasterOrderRepository.class);
    private final JpaSubOrderRepository subRepo = mock(JpaSubOrderRepository.class);
    private final JpaDisputeRepository disputeRepo = mock(JpaDisputeRepository.class);
    private final AiEntityReferenceResolver resolver = new AiEntityReferenceResolver(masterRepo, subRepo, disputeRepo);

    @Test
    void stripUuidsRemovesUuidsContainingPhoneLikeSubstrings() {
        String text = "Kiểm tra đơn 14ceea3b-a086-4975-854a-1bdbaedc8a1d giúp tôi";
        String stripped = AiEntityReferenceResolver.stripUuids(text);
        assertThat(stripped).doesNotContain("086-4975-854");
    }

    @Test
    void resolvesCustomerMasterOrderByRandomUuidDisplayCodeAndBlocksOtherCustomers() {
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        UUID orderId = UUID.fromString("7a2f1c4d-1234-4abc-9def-0123456789ab");
        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(orderId);
        order.setCustomerId(customerA);
        order.setOrderCode("ORD-7A2F1C4D");

        when(masterRepo.findByCustomerIdAndOrderCodeIgnoreCase(customerA, "ORD-7A2F1C4D"))
                .thenReturn(Optional.of(order));
        when(masterRepo.findByCustomerIdAndOrderCodeIgnoreCase(customerB, "ORD-7A2F1C4D"))
                .thenReturn(Optional.empty());
        when(masterRepo.findByCustomerIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(customerB),
                org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(List.of());

        var owned = resolver.resolveOrderForCustomer(customerA, "Trạng thái đơn #ORD-7A2F1C4D");
        assertThat(owned).isPresent();
        assertThat(owned.get().masterOrderId()).isEqualTo(orderId);
        assertThat(owned.get().displayCode()).isEqualTo("ORD-7A2F1C4D");

        assertThat(resolver.resolveOrderForCustomer(customerB, "Trạng thái đơn #ORD-7A2F1C4D")).isEmpty();
    }

    @Test
    void resolvesCustomerSubOrderCodeAndLegacyDnsCode() {
        UUID customerId = UUID.randomUUID();
        UUID masterId = UUID.fromString("9c112233-1234-4abc-9def-0123456789ab");
        UUID subId = UUID.fromString("ec938b9f-1234-4abc-9def-0123456789ab");

        MasterOrderJpaEntity master = new MasterOrderJpaEntity();
        master.setId(masterId);
        master.setCustomerId(customerId);
        master.setOrderCode(DisplayCodeFormatter.format(masterId, "ORD"));

        SubOrderJpaEntity sub = new SubOrderJpaEntity();
        sub.setId(subId);
        sub.setMasterOrderId(masterId);
        sub.setSubOrderCode("SUB-EC938B9F");

        when(subRepo.findBySubOrderCodeIgnoreCase("SUB-EC938B9F")).thenReturn(Optional.of(sub));
        when(masterRepo.findById(masterId)).thenReturn(Optional.of(master));
        when(masterRepo.findByCustomerIdAndOrderCodeIgnoreCase(customerId, "ORD-9C112233"))
                .thenReturn(Optional.of(master));

        var subResolved = resolver.resolveOrderForCustomer(customerId, "Kiểm tra đơn phụ #SUB-EC938B9F");
        assertThat(subResolved).isPresent();
        assertThat(subResolved.get().masterOrderId()).isEqualTo(masterId);
        assertThat(subResolved.get().subOrderId()).isEqualTo(subId);
        assertThat(subResolved.get().resolvedFromSubOrder()).isTrue();

        var dnsResolved = resolver.resolveOrderForCustomer(customerId, "DNS-9C112233");
        assertThat(dnsResolved).isPresent();
        assertThat(dnsResolved.get().masterOrderId()).isEqualTo(masterId);
    }

    @Test
    void resolvesDisputeWithinVendorScopeOnly() {
        UUID vendorA = UUID.randomUUID();
        UUID vendorB = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        UUID disputeId = UUID.fromString("77777777-7777-7777-7777-777777777704");

        DisputeJpaEntity dispute = new DisputeJpaEntity();
        dispute.setId(disputeId);
        dispute.setSubOrderId(subId);
        SubOrderJpaEntity sub = new SubOrderJpaEntity();
        sub.setId(subId);
        sub.setVendorId(vendorA);

        when(disputeRepo.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepo.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dispute)));
        when(subRepo.findById(subId)).thenReturn(Optional.of(sub));

        assertThat(resolver.resolveDisputeForVendor(vendorA, "Tóm tắt #DISP-77777704")).contains(disputeId);
        assertThat(resolver.resolveDisputeForVendor(vendorB, "Tóm tắt #DISP-77777704")).isEmpty();
        assertThat(resolver.resolveDisputeForAdmin("Tóm tắt #DISP-77777704")).contains(disputeId);
    }
}
