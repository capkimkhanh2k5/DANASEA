package com.danasea.backend.shared.presentation;

import com.danasea.backend.shared.i18n.LocalizedMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Locale;

@RestControllerAdvice
public class ErrorResponseLocalizationAdvice implements ResponseBodyAdvice<Object> {
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

    @Autowired(required = false)
    void setLocalizedMessageService(LocalizedMessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object responseBody,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(responseBody instanceof ErrorResponse body) || body.code() == null) {
            return responseBody;
        }
        String key = "error." + body.code().toLowerCase(Locale.ROOT);
        String fallback = (body.message() != null && !body.message().isBlank())
                ? body.message()
                : messages.get("error.internal");
        return new ErrorResponse(body.code(), messages.getOrDefault(key, fallback));
    }
}
