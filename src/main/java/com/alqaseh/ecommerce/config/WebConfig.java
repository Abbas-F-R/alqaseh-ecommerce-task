package com.alqaseh.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
        localeResolver.setDefaultLocale(Locale.ENGLISH);
        localeResolver.setSupportedLocales(List.of(
                Locale.ENGLISH,
                Locale.forLanguageTag("ar")
        ));
        return localeResolver;
    }

    /**
     * Query parameters such as {@code ?category=furniture} or {@code ?paymentMethod=credit_card} are accepted in any
     * letter case. (JSON bodies get the same behaviour from {@code spring.jackson.mapper.accept-case-insensitive-enums}.)
     * An unknown value still fails conversion and is answered with 400.
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new ConverterFactory<String, Enum>() {
            @Override
            public <T extends Enum> Converter<String, T> getConverter(Class<T> enumType) {
                return source -> {
                    if (source.isBlank()) {
                        return null;
                    }
                    try {
                        // Enums with a public static from(String) (category, payment method) parse their own API spelling.
                        return (T) enumType.getMethod("from", String.class).invoke(null, source);
                    } catch (NoSuchMethodException e) {
                        return (T) Enum.valueOf(enumType, source.trim().toUpperCase(Locale.ROOT));
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw new IllegalArgumentException(e.getCause().getMessage(), e.getCause());
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                };
            }
        });
    }
}
