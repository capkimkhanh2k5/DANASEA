package com.danasea.backend.modules.vendor.infrastructure.storage;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CloudinaryDocumentStorageAdapterTest {

    private Cloudinary cloudinary;
    private Uploader uploader;
    private CloudinaryDocumentStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        adapter = new CloudinaryDocumentStorageAdapter(cloudinary);
    }

    @Test
    void shouldUploadDocumentWithAuthenticatedType() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "business_license.png", "image/png", "fake license content".getBytes()
        );
        String secureUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png";

        when(uploader.upload(eq(file.getBytes()), anyMap())).thenReturn(Map.of("secure_url", secureUrl));

        String result = adapter.uploadDocument(file, "danasea_demo_data_package/vendor_documents");

        assertEquals(secureUrl, result);
        verify(uploader).upload(eq(file.getBytes()), argThat(params ->
                "danasea_demo_data_package/vendor_documents".equals(params.get("folder")) &&
                "auto".equals(params.get("resource_type")) &&
                "authenticated".equals(params.get("type"))
        ));
    }

    @Test
    void shouldThrowExceptionWhenFileIsNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> adapter.uploadDocument(null, "folder"));
        MockMultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> adapter.uploadDocument(emptyFile, "folder"));
    }

    @Test
    void shouldGenerateExpiringPrivateDownloadUrlForAuthenticatedDocument() throws Exception {
        String authenticatedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png";
        String expiringUrl = "https://api.cloudinary.com/v1_1/xmxguqpi/image/download?api_key=key&expires_at=1234567890&format=png&public_id=danasea_demo_data_package/vendor_documents/4d57d2cc&signature=sig&timestamp=1234567590&type=authenticated";

        when(cloudinary.privateDownload(
                eq("danasea_demo_data_package/vendor_documents/4d57d2cc"),
                eq("png"),
                argThat(map -> "authenticated".equals(map.get("type")) && map.get("expires_at") instanceof Long)
        )).thenReturn(expiringUrl);

        String result = adapter.generateDeliveryUrl(authenticatedUrl);

        assertEquals(expiringUrl, result);
        verify(cloudinary).privateDownload(
                eq("danasea_demo_data_package/vendor_documents/4d57d2cc"),
                eq("png"),
                argThat(map -> {
                    long expiresAt = (Long) map.get("expires_at");
                    long now = Instant.now().getEpochSecond();
                    return expiresAt >= now + 290 && expiresAt <= now + 310;
                })
        );
    }

    @Test
    void shouldReturnOriginalUrlWhenNotAuthenticated() {
        String publicUrl = "https://res.cloudinary.com/xmxguqpi/image/upload/v1792170628/services/photo.jpg";
        assertEquals(publicUrl, adapter.generateDeliveryUrl(publicUrl));
        assertNull(adapter.generateDeliveryUrl(null));
    }

    @Test
    void shouldExtractPublicIdCorrectly() {
        assertEquals("danasea_demo_data_package/vendor_documents/4d57d2cc",
                adapter.extractPublicId("https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png"));
        assertEquals("png",
                adapter.extractFormat("https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png"));
    }
}
