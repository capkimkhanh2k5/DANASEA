package com.danasea.backend.modules.service.domain.exceptions;

public class CategoryHasDependentRulesException extends RuntimeException {
    public CategoryHasDependentRulesException(String message) {
        super(message);
    }
}
