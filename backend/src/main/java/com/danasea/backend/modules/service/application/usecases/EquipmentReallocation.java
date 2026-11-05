package com.danasea.backend.modules.service.application.usecases;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class EquipmentReallocation {

    private static final int MAX_SEARCH_STEPS = 20_000;
    private static final int MAX_SEARCH_DEPTH = 200;

    record Commitment(UUID id, int seats, boolean privateRental) {}
    record Unit(int number, int capacity, int occupied) {}
    record Move(UUID commitmentId, int unitNumber, int allocatedSeats) {}
    record Plan(boolean complete, List<Move> moves) {}

    private EquipmentReallocation() {}

    static Plan plan(List<Commitment> commitments, List<Unit> units, boolean allowPartial) {
        List<Commitment> constrainedFirst = commitments.stream()
                .sorted(Comparator.comparing(Commitment::privateRental).reversed()
                        .thenComparing(Comparator.comparingInt(Commitment::seats).reversed()))
                .toList();
        int[] occupied = units.stream().mapToInt(Unit::occupied).toArray();
        List<Move> moves = new ArrayList<>();
        if (commitments.size() <= MAX_SEARCH_DEPTH
                && fitAll(constrainedFirst, units, occupied, moves, 0, new int[]{MAX_SEARCH_STEPS})) {
            return new Plan(true, List.copyOf(moves));
        }
        if (!allowPartial) {
            return new Plan(false, List.of());
        }

        // The caller supplies confirmation order for partial urgent moves.
        for (Commitment commitment : commitments) {
            int best = -1;
            for (int i = 0; i < units.size(); i++) {
                Unit unit = units.get(i);
                if (fits(commitment, unit, occupied[i])
                        && (best < 0 || unit.capacity() - occupied[i]
                                < units.get(best).capacity() - occupied[best])) {
                    best = i;
                }
            }
            if (best >= 0) {
                Unit unit = units.get(best);
                int allocation = commitment.privateRental() ? unit.capacity() : commitment.seats();
                occupied[best] += allocation;
                moves.add(new Move(commitment.id(), unit.number(), allocation));
            }
        }
        return new Plan(moves.size() == commitments.size(), List.copyOf(moves));
    }

    private static boolean fitAll(List<Commitment> commitments, List<Unit> units, int[] occupied,
            List<Move> moves, int index, int[] budget) {
        if (index == commitments.size()) {
            return true;
        }
        if (budget[0]-- <= 0) {
            return false;
        }
        Commitment commitment = commitments.get(index);
        Set<UnitCapacity> equivalent = new HashSet<>();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (!fits(commitment, unit, occupied[i])
                    || !equivalent.add(new UnitCapacity(unit.capacity(), occupied[i]))) {
                continue;
            }
            int allocation = commitment.privateRental() ? unit.capacity() : commitment.seats();
            occupied[i] += allocation;
            moves.add(new Move(commitment.id(), unit.number(), allocation));
            if (fitAll(commitments, units, occupied, moves, index + 1, budget)) {
                return true;
            }
            occupied[i] -= allocation;
            moves.removeLast();
        }
        return false;
    }

    private static boolean fits(Commitment commitment, Unit unit, int occupied) {
        return unit.capacity() - occupied >= commitment.seats()
                && (!commitment.privateRental() || occupied == 0);
    }

    private record UnitCapacity(int capacity, int occupied) {}
}
