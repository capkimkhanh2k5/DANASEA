package com.danasea.backend.shared.i18n;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Service
public class LocalizedMessageService {
    private final MessageSource messageSource;
    private final MeterRegistry meterRegistry;

    @Autowired
    public LocalizedMessageService(MessageSource messageSource, ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.messageSource = messageSource;
        this.meterRegistry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
    }

    public LocalizedMessageService(MessageSource messageSource) {
        this(messageSource, null);
    }

    public static LocalizedMessageService standalone() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames(
                "classpath:i18n/common", "classpath:i18n/validation", "classpath:i18n/auth",
                "classpath:i18n/booking", "classpath:i18n/weather", "classpath:i18n/notification",
                "classpath:i18n/ai", "classpath:i18n/policy", "classpath:i18n/transactional_email");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        return new LocalizedMessageService(source);
    }

    public String get(String key, Object... args) {
        return get(key, args, LocaleContextHolder.getLocale());
    }

    public String get(LocalizedMessageRef ref) {
        return get(ref.key(), ref.args());
    }

    public String get(LocalizedMessageRef ref, SupportedLanguage language) {
        return get(ref.key(), ref.args(), language.locale());
    }

    public String get(String key, SupportedLanguage language, Object... args) {
        return get(key, args, language.locale());
    }

    public String getOrDefault(String key, String fallback, Object... args) {
        try {
            return get(key, args);
        } catch (NoSuchMessageException ignored) {
            return fallback;
        }
    }

    public String get(String key, Object[] args, Locale locale) {
        Locale requested = SupportedLanguage.fromTag(locale == null ? null : locale.toLanguageTag())
                .orElse(SupportedLanguage.DEFAULT)
                .locale();
        try {
            return messageSource.getMessage(key, resolveArguments(args, requested), requested);
        } catch (NoSuchMessageException missingRequested) {
            recordFallback();
            log.warn("Missing i18n message key '{}' for locale '{}'; falling back to Vietnamese", key, requested);
            if (!SupportedLanguage.VI.locale().equals(requested)) {
                return messageSource.getMessage(key, resolveArguments(args, SupportedLanguage.VI.locale()), SupportedLanguage.VI.locale());
            }
            throw missingRequested;
        }
    }

    private Object[] resolveArguments(Object[] args, Locale locale) {
        if (args == null || args.length == 0) {
            return args;
        }
        SupportedLanguage language = SupportedLanguage.fromTag(locale.toLanguageTag()).orElse(SupportedLanguage.VI);
        Object[] resolved = args.clone();
        for (int i = 0; i < resolved.length; i++) {
            if (resolved[i] instanceof LocalizedArgument value) {
                resolved[i] = value.resolve(language);
            } else if (resolved[i] instanceof LocalizedMessageRef ref) {
                resolved[i] = get(ref, language);
            }
        }
        return resolved;
    }

    private void recordFallback() {
        if (meterRegistry != null) {
            meterRegistry.counter("i18n_missing_translation_total").increment();
        }
    }
}
