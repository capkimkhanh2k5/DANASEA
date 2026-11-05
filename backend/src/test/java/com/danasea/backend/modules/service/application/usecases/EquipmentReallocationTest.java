package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentReallocationTest {
    @Test
    void preservesExistingBookingsAndFitsAllAffectedGroupsBeforeSelectingPartialMoves() {
        var group = new EquipmentReallocation.Commitment(UUID.randomUUID(), 5, false);
        var privateRental = new EquipmentReallocation.Commitment(UUID.randomUUID(), 6, true);
        var plan = EquipmentReallocation.plan(
                List.of(group, privateRental),
                List.of(new EquipmentReallocation.Unit(2, 8, 0),
                        new EquipmentReallocation.Unit(3, 7, 2)), false);

        assertThat(plan.complete()).isTrue();
        assertThat(plan.moves()).containsExactlyInAnyOrder(
                new EquipmentReallocation.Move(group.id(), 3, 5),
                new EquipmentReallocation.Move(privateRental.id(), 2, 8));
    }

    @Test
    void urgentPlanDoesNotSplitAGroupOrReplacePrivateRentalWithOpenSeats() {
        var privateRental = new EquipmentReallocation.Commitment(UUID.randomUUID(), 3, true);
        var group = new EquipmentReallocation.Commitment(UUID.randomUUID(), 5, false);
        var smallerGroup = new EquipmentReallocation.Commitment(UUID.randomUUID(), 2, false);

        var plan = EquipmentReallocation.plan(
                List.of(privateRental, group, smallerGroup),
                List.of(new EquipmentReallocation.Unit(2, 6, 3),
                        new EquipmentReallocation.Unit(3, 6, 3)), true);

        assertThat(plan.complete()).isFalse();
        assertThat(plan.moves()).containsExactly(new EquipmentReallocation.Move(smallerGroup.id(), 2, 2));
    }
}
