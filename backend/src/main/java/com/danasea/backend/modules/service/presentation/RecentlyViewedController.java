package com.danasea.backend.modules.service.presentation;

import com.danasea.backend.modules.service.application.dtos.RecentlyViewedResult;
import com.danasea.backend.modules.service.application.usecases.GetRecentlyViewedUseCase;
import com.danasea.backend.modules.service.presentation.dtos.RecentlyViewedResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recently-viewed")
@RequiredArgsConstructor
public class RecentlyViewedController {

    private final GetRecentlyViewedUseCase getRecentlyViewedUseCase;

    @GetMapping
    public ResponseEntity<List<RecentlyViewedResponse>> getRecentlyViewed(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        UUID userId = SecurityUtils.getCurrentUserId().orElse(null);
        
        List<RecentlyViewedResult> results = getRecentlyViewedUseCase.execute(userId, sessionId);
        
        List<RecentlyViewedResponse> responses = results.stream().map(r -> RecentlyViewedResponse.builder()
                .id(r.getId())
                .serviceId(r.getServiceId())
                .serviceName(r.getServiceName())
                .primaryImageUrl(r.getPrimaryImageUrl())
                .viewedAt(r.getViewedAt())
                .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }
}
