package com.danasea.backend.modules.order.infrastructure.jobs;

import com.danasea.backend.modules.order.application.usecases.AutoCompleteSubOrdersUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubOrderAutoCompletionJob {

    private final AutoCompleteSubOrdersUseCase autoCompleteSubOrdersUseCase;

    @Scheduled(fixedDelayString = "${app.scheduler.sub-order-completion-delay:60s}")
    public void processAutoCompletion() {
        try {
            OffsetDateTime now = OffsetDateTime.now(AutoCompleteSubOrdersUseCase.VIETNAM_ZONE);
            int completedCount = autoCompleteSubOrdersUseCase.executeAll(now);
            if (completedCount > 0) {
                log.info("SubOrderAutoCompletionJob: Auto-completed {} sub-order(s) at {}", completedCount, now);
            }
        } catch (Exception e) {
            log.error("SubOrderAutoCompletionJob encountered an unhandled error", e);
        }
    }
}
