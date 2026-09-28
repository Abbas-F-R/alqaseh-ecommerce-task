package com.alqaseh.ecommerce.shared.localization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalizationService {

    private final MessageSource messageSource;

    public String getMessage(String key, Object... args) {
        Locale currentLocale = LocaleContextHolder.getLocale();
        return getMessage(key, currentLocale, args);
    }

    public String getMessage(String key, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(key, args, locale != null ? locale : Locale.ENGLISH);
        } catch (NoSuchMessageException e) {
            log.warn("Missing localization key: '{}' for locale: '{}'", key, locale);
            return key;
        }
    }
}
