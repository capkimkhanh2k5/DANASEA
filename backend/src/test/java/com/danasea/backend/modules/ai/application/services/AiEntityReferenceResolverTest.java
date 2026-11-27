package com.danasea.backend.modules.ai.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    void stripUuidsRemovesUuidsContainingPhoneLikeSubstringsAndHandlesBlank() {
        String text = "Kiểm tra đơn 14ceea3b-a086-4975-854a-1bdbaedc8a1d giúp tôi";
        String stripped = AiEntityReferenceResolver.stripUuids(text);
        assertThat(stripped).doesNotContain("086-4975-854");
        assertThat(AiEntityReferenceResolver.stripUuids(null)).isNull();
        assertThat(AiEntityReferenceResolver.stripUuids("")).isEmpty();
    }

    @Test
    void extractorsFilterDeduplicateAndDetectReferences() {
        assertThat(resolver.extractUuids(null)).isEmpty();
        assertThat(resolver.extractUuids("   ")).isEmpty();
        assertThat(resolver.extractCodes(null, Set.of("ORD"))).isEmpty();
        assertThat(resolver.extractCodes("   ", Set.of("ORD"))).isEmpty();
        assertThat(resolver.containsOrderLikeReference("Xin chào")).isFalse();
        assertThat(resolver.containsOrderOrDisputeCode("Xin chào")).isFalse();

        UUID id = UUID.fromString("14ceea3b-a086-4975-854a-1bdbaedc8a1d");
        assertThat(resolver.extractUuids(id + " and " + id)).containsExactly(id);
        assertThat(resolver.containsOrderLikeReference("Đơn " + id)).isTrue();
        assertThat(resolver.containsOrderOrDisputeCode("Khiếu nại #DISP-77777704")).isTrue();
        assertThat(resolver.containsOrderLikeReference("Khiếu nại #DISP-77777704")).isFalse();

        var allCodes = resolver.extractCodes("#ord-7a2f1c4d ORD-7A2F1C4D #DISP-77777704", null);
        assertThat(allCodes).hasSize(2);
        var onlyOrd = resolver.extractCodes("#ord-7a2f1c4d #DISP-77777704", Set.of("ORD"));
        assertThat(onlyOrd).extracting(AiEntityReferenceResolver.ExtractedCode::normalizedCode)
                .containsExactly("ORD-7A2F1C4D");
    }

    @Test
    void resolvesCustomerMasterOrderByRandomUuidDisplayCodeAndBlocksOtherCustomers() {
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        UUID orderId = UUID.fromString("7a2f1c4d-1234-4abc-9def-0123456789ab");
        MasterOrderJpaEntity order = master(orderId, customerA, "ORD-7A2F1C4D");

        when(masterRepo.findByCustomerIdAndOrderCodeIgnoreCase(customerA, "ORD-7A2F1C4D"))
                .thenReturn(Optional.of(order));
        when(masterRepo.findByCustomerIdAndOrderCodeIgnoreCase(customerB, "ORD-7A2F1C4D"))
                .thenReturn(Optional.empty());
        when(masterRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customerB), any(Pageable.class)))
                .thenReturn(List.of());

        var owned = resolver.resolveOrderForCustomer(customerA, "Trạng thái đơn #ORD-7A2F1C4D");
        assertThat(owned).isPresent();
        assertThat(owned.get().masterOrderId()).isEqualTo(orderId);
        assertThat(owned.get().displayCode()).isEqualTo("ORD-7A2F1C4D");

        assertThat(resolver.resolveOrderForCustomer(customerB, "Trạng thái đơn #ORD-7A2F1C4D")).isEmpty();
        assertThat(resolver.resolveOrderForCustomer(null, "#ORD-7A2F1C4D")).isEmpty();
        assertThat(resolver.resolveOrderForCustomer(customerA, "   ")).isEmpty();
    }

    @Test
    void resolvesCustomerByUuidSeedAndFallbackScans() {
        UUID customer = UUID.randomUUID();
        UUID otherCustomer = UUID.randomUUID();
        UUID masterUuid = UUID.fromString("9c112233-1234-4abc-9def-0123456789ab");
        UUID subUuid = UUID.fromString("ec938b9f-1234-4abc-9def-0123456789ab");
        UUID seedMasterUuid = UUID.fromString("00010000-0000-0000-0000-000000000042");
        UUID seedSubUuid = UUID.fromString("00020000-0000-0000-0000-000000000015");

        MasterOrderJpaEntity masterOrder = master(masterUuid, customer, null);
        SubOrderJpaEntity subOrder = sub(subUuid, masterUuid, UUID.randomUUID(), null);
        MasterOrderJpaEntity seedMaster = master(seedMasterUuid, customer, "ORD-00010042");
        SubOrderJpaEntity seedSub = sub(seedSubUuid, seedMasterUuid, UUID.randomUUID(), "SUB-00020015");

        when(masterRepo.findById(masterUuid)).thenReturn(Optional.of(masterOrder));
        when(masterRepo.findById(subUuid)).thenReturn(Optional.empty());
        when(subRepo.findById(subUuid)).thenReturn(Optional.of(subOrder));
        when(masterRepo.findById(seedMasterUuid)).thenReturn(Optional.of(seedMaster));
        when(subRepo.findById(seedSubUuid)).thenReturn(Optional.of(seedSub));

        assertThat(resolver.resolveOrderForCustomer(customer, "Check " + masterUuid))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::displayCode).isEqualTo("ORD-9C112233");
        assertThat(resolver.resolveOrderForCustomer(otherCustomer, "Check " + masterUuid)).isEmpty();
        assertThat(resolver.resolveOrderForCustomer(customer, "Check " + subUuid))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::resolvedFromSubOrder).isEqualTo(true);
        assertThat(resolver.resolveOrderForCustomer(otherCustomer, "Check " + subUuid)).isEmpty();

        assertThat(resolver.resolveOrderForCustomer(customer, "#ORD-00010042"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::masterOrderId).isEqualTo(seedMasterUuid);
        assertThat(resolver.resolveOrderForCustomer(customer, "#SUB-00020015"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::subOrderId).isEqualTo(seedSubUuid);

        when(masterRepo.findByCustomerIdAndOrderCodeIgnoreCase(customer, "ORD-9C1122")).thenReturn(Optional.empty());
        when(masterRepo.findByCustomerIdOrderByCreatedAtDesc(eq(customer), any(Pageable.class)))
                .thenReturn(List.of(masterOrder));
        assertThat(resolver.resolveOrderForCustomer(customer, "DNS-9C1122"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::masterOrderId).isEqualTo(masterUuid);

        when(subRepo.findBySubOrderCodeIgnoreCase("SUB-EC938B")).thenReturn(Optional.empty());
        when(subRepo.findByMasterOrderIdIn(List.of(masterUuid))).thenReturn(List.of(subOrder));
        assertThat(resolver.resolveOrderForCustomer(customer, "#SUB-EC938B"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::subOrderId).isEqualTo(subUuid);

        when(masterRepo.findByCustomerIdOrderByCreatedAtDesc(eq(otherCustomer), any(Pageable.class)))
                .thenReturn(List.of());
        when(subRepo.findBySubOrderCodeIgnoreCase("SUB-999999")).thenReturn(Optional.empty());
        assertThat(resolver.resolveOrderForCustomer(otherCustomer, "#SUB-999999")).isEmpty();

        AiEntityReferenceResolver nullRepos = new AiEntityReferenceResolver(null, null, null);
        assertThat(nullRepos.resolveOrderForCustomer(customer, "#ORD-7A2F1C4D")).isEmpty();
        assertThat(nullRepos.resolveOrderForCustomer(customer, "#SUB-EC938B9F")).isEmpty();
    }

    @Test
    void resolvesCustomerSubOrderCodeAndLegacyDnsCode() {
        UUID customerId = UUID.randomUUID();
        UUID masterId = UUID.fromString("9c112233-1234-4abc-9def-0123456789ab");
        UUID subId = UUID.fromString("ec938b9f-1234-4abc-9def-0123456789ab");

        MasterOrderJpaEntity master = master(masterId, customerId, DisplayCodeFormatter.format(masterId, "ORD"));
        SubOrderJpaEntity sub = sub(subId, masterId, UUID.randomUUID(), "SUB-EC938B9F");

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
    void resolvesVendorOrdersAcrossUuidSeedExactAndFallbackScansWithinScope() {
        UUID vendorA = UUID.randomUUID();
        UUID vendorB = UUID.randomUUID();
        UUID masterId = UUID.fromString("7a2f1c4d-1234-4abc-9def-0123456789ab");
        UUID subId = UUID.fromString("ec938b9f-1234-4abc-9def-0123456789ab");
        UUID seedMasterId = UUID.fromString("00010000-0000-0000-0000-000000000042");
        UUID seedSubId = UUID.fromString("00020000-0000-0000-0000-000000000015");

        MasterOrderJpaEntity masterOrder = master(masterId, UUID.randomUUID(), "ORD-7A2F1C4D");
        SubOrderJpaEntity subOrder = sub(subId, masterId, vendorA, "SUB-EC938B9F");
        SubOrderJpaEntity seedSub = sub(seedSubId, seedMasterId, vendorA, "SUB-00020015");

        assertThat(resolver.resolveOrderForVendor(null, "#SUB-EC938B9F")).isEmpty();
        assertThat(resolver.resolveOrderForVendor(vendorA, "  ")).isEmpty();
        assertThat(new AiEntityReferenceResolver(masterRepo, null, disputeRepo)
                .resolveOrderForVendor(vendorA, "#SUB-EC938B9F")).isEmpty();

        when(subRepo.findById(subId)).thenReturn(Optional.of(subOrder));
        when(subRepo.findById(masterId)).thenReturn(Optional.empty());
        when(subRepo.findByMasterOrderId(masterId)).thenReturn(List.of(subOrder));
        assertThat(resolver.resolveOrderForVendor(vendorA, "Sub " + subId))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::resolvedFromSubOrder).isEqualTo(true);
        assertThat(resolver.resolveOrderForVendor(vendorB, "Sub " + subId)).isEmpty();
        assertThat(resolver.resolveOrderForVendor(vendorA, "Master " + masterId))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::masterOrderId).isEqualTo(masterId);

        when(subRepo.findById(seedSubId)).thenReturn(Optional.of(seedSub));
        when(subRepo.findByMasterOrderId(seedMasterId)).thenReturn(List.of(seedSub));
        assertThat(resolver.resolveOrderForVendor(vendorA, "#SUB-00020015"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::subOrderId).isEqualTo(seedSubId);
        assertThat(resolver.resolveOrderForVendor(vendorA, "#ORD-00010042"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::masterOrderId).isEqualTo(seedMasterId);

        when(subRepo.findBySubOrderCodeIgnoreCase("SUB-EC938B9F")).thenReturn(Optional.of(subOrder));
        assertThat(resolver.resolveOrderForVendor(vendorA, "#SUB-EC938B9F"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::subOrderId).isEqualTo(subId);

        when(subRepo.findBySubOrderCodeIgnoreCase("SUB-EC938B")).thenReturn(Optional.empty());
        when(subRepo.findByVendorIdOrderByCreatedAtDesc(eq(vendorA), any(Pageable.class)))
                .thenReturn(List.of(subOrder));
        assertThat(resolver.resolveOrderForVendor(vendorA, "#SUB-EC938B"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::subOrderId).isEqualTo(subId);

        when(masterRepo.findByOrderCodeIgnoreCase("ORD-7A2F1C4D")).thenReturn(Optional.of(masterOrder));
        assertThat(resolver.resolveOrderForVendor(vendorA, "#ORD-7A2F1C4D"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::masterOrderId).isEqualTo(masterId);

        when(masterRepo.findByOrderCodeIgnoreCase("ORD-7A2F1C")).thenReturn(Optional.empty());
        when(masterRepo.findAllById(List.of(masterId))).thenReturn(List.of(masterOrder));
        assertThat(resolver.resolveOrderForVendor(vendorA, "DNS-7A2F1C"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::masterOrderId).isEqualTo(masterId);

        when(subRepo.findByVendorIdOrderByCreatedAtDesc(eq(vendorB), any(Pageable.class))).thenReturn(List.of());
        assertThat(resolver.resolveOrderForVendor(vendorB, "#ORD-7A2F1C4D")).isEmpty();
    }

    @Test
    void resolvesAdminOrdersAcrossUuidSeedExactAndFallbackScans() {
        UUID masterId = UUID.fromString("7a2f1c4d-1234-4abc-9def-0123456789ab");
        UUID subId = UUID.fromString("ec938b9f-1234-4abc-9def-0123456789ab");
        UUID seedMasterId = UUID.fromString("00010000-0000-0000-0000-000000000042");
        UUID seedSubId = UUID.fromString("00020000-0000-0000-0000-000000000015");

        MasterOrderJpaEntity masterOrder = master(masterId, UUID.randomUUID(), "ORD-7A2F1C4D");
        SubOrderJpaEntity subOrder = sub(subId, masterId, UUID.randomUUID(), "SUB-EC938B9F");
        MasterOrderJpaEntity seedMaster = master(seedMasterId, UUID.randomUUID(), "ORD-00010042");
        SubOrderJpaEntity seedSub = sub(seedSubId, seedMasterId, UUID.randomUUID(), "SUB-00020015");

        assertThat(resolver.resolveOrderForAdmin(null)).isEmpty();
        assertThat(resolver.resolveOrderForAdmin("  ")).isEmpty();

        when(masterRepo.findById(masterId)).thenReturn(Optional.of(masterOrder));
        when(masterRepo.findById(subId)).thenReturn(Optional.empty());
        when(subRepo.findById(subId)).thenReturn(Optional.of(subOrder));
        assertThat(resolver.resolveOrderForAdmin("Master " + masterId))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::masterOrderId).isEqualTo(masterId);
        assertThat(resolver.resolveOrderForAdmin("Sub " + subId))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::subOrderId).isEqualTo(subId);

        when(subRepo.findById(seedSubId)).thenReturn(Optional.of(seedSub));
        when(masterRepo.findById(seedMasterId)).thenReturn(Optional.of(seedMaster));
        assertThat(resolver.resolveOrderForAdmin("#SUB-00020015"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::subOrderId).isEqualTo(seedSubId);
        assertThat(resolver.resolveOrderForAdmin("#ORD-00010042"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::masterOrderId).isEqualTo(seedMasterId);

        when(subRepo.findBySubOrderCodeIgnoreCase("SUB-EC938B9F")).thenReturn(Optional.of(subOrder));
        when(masterRepo.findByOrderCodeIgnoreCase("ORD-7A2F1C4D")).thenReturn(Optional.of(masterOrder));
        assertThat(resolver.resolveOrderForAdmin("#SUB-EC938B9F"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::subOrderId).isEqualTo(subId);
        assertThat(resolver.resolveOrderForAdmin("#ORD-7A2F1C4D"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::masterOrderId).isEqualTo(masterId);

        when(subRepo.findBySubOrderCodeIgnoreCase("SUB-EC938B")).thenReturn(Optional.empty());
        when(subRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(subOrder)));
        assertThat(resolver.resolveOrderForAdmin("#SUB-EC938B"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::subOrderId).isEqualTo(subId);

        when(masterRepo.findByOrderCodeIgnoreCase("ORD-7A2F1C")).thenReturn(Optional.empty());
        when(masterRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(masterOrder)));
        assertThat(resolver.resolveOrderForAdmin("DNS-7A2F1C"))
                .get().extracting(AiEntityReferenceResolver.ResolvedOrder::masterOrderId).isEqualTo(masterId);

        assertThat(new AiEntityReferenceResolver(null, null, null).resolveOrderForAdmin("#ORD-7A2F1C4D")).isEmpty();
    }

    @Test
    void resolvesDisputeWithinVendorScopeOnlyAndHandlesUuidAndFallbackScan() {
        UUID vendorA = UUID.randomUUID();
        UUID vendorB = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        UUID seedDisputeId = UUID.fromString("77777777-7777-7777-7777-777777777704");
        UUID randomDisputeId = UUID.fromString("a1b2c3d4-1234-4abc-9def-0123456789ab");

        DisputeJpaEntity seedDispute = new DisputeJpaEntity();
        seedDispute.setId(seedDisputeId);
        seedDispute.setSubOrderId(subId);

        DisputeJpaEntity randomDispute = new DisputeJpaEntity();
        randomDispute.setId(randomDisputeId);
        randomDispute.setSubOrderId(subId);

        DisputeJpaEntity unlinkedDispute = new DisputeJpaEntity();
        unlinkedDispute.setId(UUID.randomUUID());
        unlinkedDispute.setSubOrderId(null);

        SubOrderJpaEntity sub = sub(subId, UUID.randomUUID(), vendorA, "SUB-11112222");

        when(disputeRepo.findById(seedDisputeId)).thenReturn(Optional.of(seedDispute));
        when(disputeRepo.findById(randomDisputeId)).thenReturn(Optional.of(randomDispute));
        when(disputeRepo.findById(unlinkedDispute.getId())).thenReturn(Optional.of(unlinkedDispute));
        when(disputeRepo.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(seedDispute, randomDispute)));
        when(subRepo.findById(subId)).thenReturn(Optional.of(sub));

        assertThat(resolver.resolveDisputeForVendor(null, "#DISP-77777704")).isEmpty();
        assertThat(resolver.resolveDisputeForAdmin("   ")).isEmpty();
        assertThat(new AiEntityReferenceResolver(masterRepo, subRepo, null).resolveDisputeForAdmin("#DISP-77777704")).isEmpty();

        assertThat(resolver.resolveDisputeForVendor(vendorA, "Tóm tắt #DISP-77777704")).contains(seedDisputeId);
        assertThat(resolver.resolveDisputeForVendor(vendorB, "Tóm tắt #DISP-77777704")).isEmpty();
        assertThat(resolver.resolveDisputeForAdmin("Tóm tắt #DISP-77777704")).contains(seedDisputeId);

        assertThat(resolver.resolveDisputeForVendor(vendorA, "Dispute " + randomDisputeId)).contains(randomDisputeId);
        assertThat(resolver.resolveDisputeForVendor(vendorB, "Dispute " + randomDisputeId)).isEmpty();
        assertThat(resolver.resolveDisputeForVendor(vendorA, "Unlinked " + unlinkedDispute.getId())).isEmpty();
        assertThat(resolver.resolveDisputeForAdmin("Random #DISP-A1B2C3D4")).contains(randomDisputeId);
        assertThat(resolver.resolveDisputeForVendor(vendorA, "Random #DISP-A1B2C3D4")).contains(randomDisputeId);
    }

    private static MasterOrderJpaEntity master(UUID id, UUID customerId, String orderCode) {
        MasterOrderJpaEntity entity = new MasterOrderJpaEntity();
        entity.setId(id);
        entity.setCustomerId(customerId);
        entity.setOrderCode(orderCode);
        return entity;
    }

    private static SubOrderJpaEntity sub(UUID id, UUID masterOrderId, UUID vendorId, String subOrderCode) {
        SubOrderJpaEntity entity = new SubOrderJpaEntity();
        entity.setId(id);
        entity.setMasterOrderId(masterOrderId);
        entity.setVendorId(vendorId);
        entity.setSubOrderCode(subOrderCode);
        return entity;
    }
}
