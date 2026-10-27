package com.danasea.backend.modules.ai.application.ports;

import java.time.Duration;
import java.util.List;
import com.danasea.backend.shared.infrastructure.http.HttpRequestDeadline;

public final class AiExecutionBudget implements AutoCloseable {
    public static final Duration LLM_REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private static final ThreadLocal<AiExecutionBudget> CURRENT = new ThreadLocal<>();
    private final AiExecutionBudget previous;
    private final long deadline;
    private int modelCalls;
    private boolean compactionUsed;
    private List<String> compactionSelection = List.of();
    private final int maximumModelCalls;
    private final HttpRequestDeadline httpDeadline;

    private AiExecutionBudget(Duration duration, int maximumModelCalls) {
        previous = CURRENT.get();
        long requestedDeadline = System.nanoTime() + duration.toNanos();
        deadline = previous == null ? requestedDeadline : Math.min(previous.deadline, requestedDeadline);
        httpDeadline = HttpRequestDeadline.open(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
        this.maximumModelCalls = maximumModelCalls;
        CURRENT.set(this);
    }

    public static AiExecutionBudget open(Duration duration, int maximumModelCalls) { return new AiExecutionBudget(duration, maximumModelCalls); }
    
    public static boolean hasTimeFor(Duration transportTimeout) {
        AiExecutionBudget budget = CURRENT.get();
        return budget == null || System.nanoTime() + transportTimeout.toNanos() < budget.deadline;
    }

    public static Duration remaining(Duration maximum) {
        AiExecutionBudget budget = CURRENT.get();
        return budget == null ? maximum : Duration.ofNanos(Math.max(0,
                Math.min(maximum.toNanos(), budget.deadline - System.nanoTime())));
    }

    public static boolean permitDecision(Duration transportTimeout) {
        AiExecutionBudget budget = CURRENT.get();
        if (budget == null) return true;
        if (budget.modelCalls >= budget.maximumModelCalls || System.nanoTime() + transportTimeout.toNanos() >= budget.deadline) return false;
        budget.modelCalls++;
        return true;
    }

    public static boolean canAttemptCompaction(Duration minimumRemaining) {
        if (!hasTimeFor(minimumRemaining)) return false;
        AiExecutionBudget root = rootBudget();
        return root == null || !root.compactionUsed;
    }

    public static boolean permitCompaction(Duration minimumRemaining) {
        if (!hasTimeFor(minimumRemaining)) return false;
        AiExecutionBudget root = rootBudget();
        if (root == null) return true;
        if (root.compactionUsed) return false;
        root.compactionUsed = true;
        return true;
    }

    public static void recordCompactionSelection(List<String> selectedIds) {
        AiExecutionBudget root = rootBudget();
        if (root != null && selectedIds != null) {
            root.compactionSelection = List.copyOf(selectedIds);
        }
    }

    public static List<String> getCompactionSelection() {
        AiExecutionBudget root = rootBudget();
        return root == null ? List.of() : root.compactionSelection;
    }

    private static AiExecutionBudget rootBudget() {
        AiExecutionBudget budget = CURRENT.get();
        if (budget == null) return null;
        while (budget.previous != null) budget = budget.previous;
        return budget;
    }

    @Override 
    public void close() {
        httpDeadline.close();
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
