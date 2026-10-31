package com.danasea.backend.shared.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugUtilsTest {

    @Test
    @DisplayName("Chuyển đổi chuỗi tiếng Việt có dấu sang slug chuẩn SEO")
    void testToSlugVietnamese() {
        assertEquals(
                "tour-lan-bien-ngam-san-ho-cu-lao-cham",
                SlugUtils.toSlug("Tour Lặn Biển Ngắm San Hô - Cù Lao Chàm")
        );
        assertEquals(
                "da-nang-hoi-an-cheo-sup-100",
                SlugUtils.toSlug("Đà Nẵng & Hội An - Chèo SUP 100%!")
        );
    }

    @Test
    @DisplayName("Xử lý chuỗi rỗng hoặc null trả về giá trị fallback an toàn")
    void testToSlugNullOrEmpty() {
        assertEquals("service", SlugUtils.toSlug(null));
        assertEquals("service", SlugUtils.toSlug("   "));
        assertEquals("service", SlugUtils.toSlug("---"));
    }

    @Test
    @DisplayName("Tự động thêm hậu tố khi slug bị trùng lặp")
    void testResolveUniqueSlug() {
        Set<String> existingSlugs = Set.of(
                "thue-sup-bai-but",
                "thue-sup-bai-but-2"
        );

        String unique = SlugUtils.resolveUniqueSlug("Thuê Sup Bãi Bụt", existingSlugs::contains);
        assertEquals("thue-sup-bai-but-3", unique);
    }
}
