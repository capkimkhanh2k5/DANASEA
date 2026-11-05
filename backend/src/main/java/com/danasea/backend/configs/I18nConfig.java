package com.danasea.backend.configs;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;

@Configuration
public class I18nConfig {
    private static final String[] BASENAMES = {
            "classpath:i18n/common",
            "classpath:i18n/validation",
            "classpath:i18n/auth",
            "classpath:i18n/booking",
            "classpath:i18n/weather",
            "classpath:i18n/notification",
            "classpath:i18n/ai",
            "classpath:i18n/policy",
            "classpath:i18n/transactional_email"
    };

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames(BASENAMES);
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
