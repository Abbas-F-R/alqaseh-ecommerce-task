package com.alqaseh.ecommerce.shared.localization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizationServiceTest {

    private final LocalizationService localizationService;

    public LocalizationServiceTest() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        this.localizationService = new LocalizationService(messageSource);
    }

    @Test
    @DisplayName("Resolves message in English")
    void shouldResolveEnglishMessage() {
        String message = localizationService.getMessage("product.not_found", Locale.ENGLISH);
        assertThat(message).isEqualTo("Product not found");
    }

    @Test
    @DisplayName("Resolves message in Arabic")
    void shouldResolveArabicMessage() {
        String message = localizationService.getMessage("product.not_found", Locale.forLanguageTag("ar"));
        assertThat(message).isEqualTo("المنتج غير موجود");
    }

    @Test
    @DisplayName("Interpolates arguments into localized messages")
    void shouldInterpolateArguments() {
        String enMessage = localizationService.getMessage("order.insufficient_stock", Locale.ENGLISH, "Laptop");
        assertThat(enMessage).isEqualTo("Insufficient stock for product: Laptop");

        String arMessage = localizationService.getMessage("order.insufficient_stock", Locale.forLanguageTag("ar"), "لابتوب");
        assertThat(arMessage).contains("الكمية المطلوبة غير متوفرة في المخزون للمنتج: لابتوب");
    }
}
