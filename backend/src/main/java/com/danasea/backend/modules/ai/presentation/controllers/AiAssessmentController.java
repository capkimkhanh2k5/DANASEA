package com.danasea.backend.modules.ai.presentation.controllers;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.application.usecases.AssessTextUseCase;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.models.DecisionTask;
import com.danasea.backend.security.infrastructure.SecurityUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AiAssessmentController {
    public record TextRequest(String text) {}
    private final AssessTextUseCase texts;
    private final DecisionModelPort decisions;

    @PostMapping("/content-assessments")
    public AssessTextUseCase.Result assess(@RequestBody TextRequest request) {
        return texts.execute(SecurityUtils.getCurrentUserId().orElseThrow(), request.text(), true);
    }

    @PostMapping("/classifications/service")
    public DecisionResult classify(@RequestBody TextRequest request) {
        if (request.text() == null || request.text().isBlank() || request.text().length() > 2500) throw new IllegalArgumentException("Text must contain 1 to 2500 characters");
        return decisions.decide(DecisionTask.SERVICE_CATEGORY, Map.of("content", request.text()));
    }
}
