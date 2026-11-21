package com.danasea.backend.modules.vendor.infrastructure.storage;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.Url;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CloudinaryDocumentStorageAdapterTest {

    private Cloudinary cloudinary;
    private Uploader uploader;
    private Url mockUrl;
    private CloudinaryDocumentStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        mockUrl = mock(Url.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(cloudinary.url()).thenReturn(mockUrl);
        when(mockUrl.resourceType(any())).thenReturn(mockUrl);
        when(mockUrl.type(any())).thenReturn(mockUrl);
        when(mockUrl.secure(anyBoolean())).thenReturn(mockUrl);
        when(mockUrl.signed(anyBoolean())).thenReturn(mockUrl);
        when(mockUrl.version(any())).thenReturn(mockUrl);
        when(mockUrl.format(any())).thenReturn(mockUrl);
        adapter = new CloudinaryDocumentStorageAdapter(cloudinary);
    }

    @Test
    void shouldUploadDocumentWithAuthenticatedTypeAndReturnSignedCdnUrl() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "business_license.png", "image/png", "fake license content".getBytes()
        );
        String unsignedSecureUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png";
        String signedCdnUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--SIGN1234--/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png";

        when(uploader.upload(eq(file.getBytes()), anyMap())).thenReturn(Map.of(
                "secure_url", unsignedSecureUrl,
                "public_id", "danasea_demo_data_package/vendor_documents/4d57d2cc",
                "version", 1792170628,
                "format", "png",
                "resource_type", "image"
        ));
        when(mockUrl.generate(eq("danasea_demo_data_package/vendor_documents/4d57d2cc"))).thenReturn(signedCdnUrl);

        String result = adapter.uploadDocument(file, "danasea_demo_data_package/vendor_documents");

        assertEquals(signedCdnUrl, result);
        verify(uploader).upload(eq(file.getBytes()), argThat(params ->
                "danasea_demo_data_package/vendor_documents".equals(params.get("folder")) &&
                "auto".equals(params.get("resource_type")) &&
                "authenticated".equals(params.get("type"))
        ));
        verify(mockUrl).type("authenticated");
        verify(mockUrl).signed(true);
        verify(mockUrl).secure(true);
    }

    @Test
    void shouldThrowExceptionWhenFileIsNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> adapter.uploadDocument(null, "folder"));
        MockMultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> adapter.uploadDocument(emptyFile, "folder"));
    }

    @Test
    void shouldKeepExistingSignedCdnUrlWithoutReSigning() {
        String existingSignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--87pVT0lZ--/v1793625552/danasea_demo_data_package/vendor_documents/e2681ab1f16042a553303d0da9192b66c0ab21cf2492a35d72b5cfc512cd2bba.png";

        String result = adapter.generateDeliveryUrl(existingSignedUrl);

        assertEquals(existingSignedUrl, result);
        verify(cloudinary, never()).url();
        verifyNoInteractions(mockUrl);
    }

    @Test
    void shouldGenerateSignedDeliveryUrlWhenAuthenticatedUrlIsUnsigned() {
        String unsignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png";
        String generatedSignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--generatedSig--/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png";

        when(mockUrl.generate(eq("danasea_demo_data_package/vendor_documents/4d57d2cc")))
                .thenReturn(generatedSignedUrl);

        String result = adapter.generateDeliveryUrl(unsignedUrl);

        assertEquals(generatedSignedUrl, result);
        verify(mockUrl).resourceType("image");
        verify(mockUrl).type("authenticated");
        verify(mockUrl).signed(true);
        verify(mockUrl).secure(true);
        verify(mockUrl).version("1792170628");
        verify(mockUrl).format("png");
        verify(mockUrl).generate("danasea_demo_data_package/vendor_documents/4d57d2cc");
    }

    @Test
    void shouldReturnOriginalUrlWhenNotAuthenticated() {
        String publicUrl = "https://res.cloudinary.com/xmxguqpi/image/upload/v1792170628/services/photo.jpg";
        assertEquals(publicUrl, adapter.generateDeliveryUrl(publicUrl));
        verify(cloudinary, never()).url();
    }

    @Test
    void shouldHandleNullEmptyOrMalformedUrlSafely() {
        assertNull(adapter.generateDeliveryUrl(null));
        assertEquals("", adapter.generateDeliveryUrl(""));
        assertEquals("   ", adapter.generateDeliveryUrl("   "));
        assertEquals("https://other-domain.com/photo.jpg", adapter.generateDeliveryUrl("https://other-domain.com/photo.jpg"));
        verify(cloudinary, never()).url();
    }

    @Test
    void shouldNeverProduceImageDownloadInNewPreview() {
        String existingSignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--87pVT0lZ--/v1793625552/danasea_demo_data_package/vendor_documents/e268.png";
        String unsignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png";
        when(mockUrl.generate(any())).thenReturn("https://res.cloudinary.com/xmxguqpi/image/authenticated/s--sig--/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png");

        String result1 = adapter.generateDeliveryUrl(existingSignedUrl);
        String result2 = adapter.generateDeliveryUrl(unsignedUrl);

        assertFalse(result1.contains("image/download"));
        assertFalse(result2.contains("image/download"));
    }

    @Test
    void shouldGenerateExactCloudinarySignedCdnUrlWithRealSdk() {
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "xmxguqpi",
                "api_key", "734928193669124",
                "api_secret", "zXCLYLKCtXr95OQ9GnkkQTPLd_Q",
                "secure", "true"
        ));
        CloudinaryDocumentStorageAdapter realAdapter = new CloudinaryDocumentStorageAdapter(realCloudinary);

        String unsignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1793625552/danasea_demo_data_package/vendor_documents/e2681ab1f16042a553303d0da9192b66c0ab21cf2492a35d72b5cfc512cd2bba.png";
        String expectedSignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--87pVT0lZ--/v1793625552/danasea_demo_data_package/vendor_documents/e2681ab1f16042a553303d0da9192b66c0ab21cf2492a35d72b5cfc512cd2bba.png";

        String result = realAdapter.generateDeliveryUrl(unsignedUrl);

        assertEquals(expectedSignedUrl, result);
        assertTrue(realAdapter.isSignedAuthenticatedUrl(result));
    }

    @Test
    void shouldExtractPublicIdCorrectly() {
        assertEquals("danasea_demo_data_package/vendor_documents/4d57d2cc",
                adapter.extractPublicId("https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png"));
        assertEquals("danasea_demo_data_package/vendor_documents/e2681ab1f16042a553303d0da9192b66c0ab21cf2492a35d72b5cfc512cd2bba",
                adapter.extractPublicId("https://res.cloudinary.com/xmxguqpi/image/authenticated/s--87pVT0lZ--/v1793625552/danasea_demo_data_package/vendor_documents/e2681ab1f16042a553303d0da9192b66c0ab21cf2492a35d72b5cfc512cd2bba.png"));
        assertEquals("png",
                adapter.extractFormat("https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/vendor_documents/4d57d2cc.png"));
    }
}
