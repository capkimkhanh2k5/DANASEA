package com.danasea.backend.modules.service.infrastructure.storage;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.Url;
import com.danasea.backend.modules.service.domain.exceptions.FileStorageException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CloudinaryStorageAdapterTest {

    private Cloudinary cloudinary;
    private Uploader uploader;
    private Url mockUrl;
    private CloudinaryStorageAdapter adapter;

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
        adapter = new CloudinaryStorageAdapter(cloudinary);
    }

    @Test
    void shouldUploadFileSuccessfully() throws IOException {
        byte[] fileData = "test content".getBytes();
        String secureUrl = "https://res.cloudinary.com/demo/image/upload/v1234567890/services/images/photo.jpg";

        when(uploader.upload(eq(fileData), anyMap())).thenReturn(Map.of("secure_url", secureUrl));

        String result = adapter.uploadFile(fileData, "photo.jpg", "services/images");

        assertEquals(secureUrl, result);
        verify(uploader).upload(eq(fileData), argThat(params ->
                "services/images".equals(params.get("folder")) &&
                "auto".equals(params.get("resource_type")) &&
                !params.containsKey("type")
        ));
    }

    @Test
    void shouldUploadDocumentWithTypeAuthenticatedAndReturnSignedCdnUrl() throws IOException {
        byte[] fileData = "test document".getBytes();
        String unsignedSecureUrl = "https://res.cloudinary.com/demo/image/authenticated/v1234567890/services/123/documents/cert.png";
        String signedCdnUrl = "https://res.cloudinary.com/demo/image/authenticated/s--SIGN1234--/v1234567890/services/123/documents/cert.png";

        when(uploader.upload(eq(fileData), anyMap())).thenReturn(Map.of(
                "secure_url", unsignedSecureUrl,
                "public_id", "services/123/documents/cert",
                "version", 1234567890,
                "format", "png",
                "resource_type", "image"
        ));
        when(mockUrl.generate(eq("services/123/documents/cert"))).thenReturn(signedCdnUrl);

        String result = adapter.uploadFile(fileData, "cert.png", "services/123/documents");

        assertEquals(signedCdnUrl, result);
        verify(uploader).upload(eq(fileData), argThat(params ->
                "services/123/documents".equals(params.get("folder")) &&
                "authenticated".equals(params.get("type"))
        ));
        verify(mockUrl).type("authenticated");
        verify(mockUrl).signed(true);
        verify(mockUrl).secure(true);
    }

    @Test
    void shouldFallbackToUrlWhenSecureUrlMissing() throws IOException {
        byte[] fileData = "test content".getBytes();
        String regularUrl = "http://res.cloudinary.com/demo/image/upload/v1234567890/services/images/photo.jpg";

        when(uploader.upload(eq(fileData), anyMap())).thenReturn(Map.of("url", regularUrl));

        String result = adapter.uploadFile(fileData, "photo.jpg", "services/images");

        assertEquals(regularUrl, result);
    }

    @Test
    void shouldThrowExceptionWhenUploadingEmptyOrNullFile() {
        assertThrows(FileStorageException.class, () -> adapter.uploadFile(null, "file.jpg", "folder"));
        assertThrows(FileStorageException.class, () -> adapter.uploadFile(new byte[0], "file.jpg", "folder"));
    }

    @Test
    void shouldThrowFileStorageExceptionWhenUploadFails() throws IOException {
        byte[] fileData = "test content".getBytes();
        when(uploader.upload(eq(fileData), anyMap())).thenThrow(new RuntimeException("Cloudinary API unavailable"));

        FileStorageException ex = assertThrows(FileStorageException.class,
                () -> adapter.uploadFile(fileData, "photo.jpg", "services/images"));

        assertTrue(ex.getMessage().contains("Cloudinary API unavailable"));
    }

    @Test
    void shouldThrowFileStorageExceptionWhenNoUrlReturned() throws IOException {
        byte[] fileData = "test content".getBytes();
        when(uploader.upload(eq(fileData), anyMap())).thenReturn(Map.of());

        assertThrows(FileStorageException.class,
                () -> adapter.uploadFile(fileData, "photo.jpg", "services/images"));
    }

    @Test
    void shouldDeleteFileSuccessfully() throws IOException {
        String fileUrl = "https://res.cloudinary.com/demo/image/upload/v1234567890/services/images/sample.jpg";

        when(uploader.destroy(eq("services/images/sample"), anyMap())).thenReturn(Map.of("result", "ok"));

        adapter.deleteFile(fileUrl);

        verify(uploader).destroy(eq("services/images/sample"), argThat(params ->
                Boolean.TRUE.equals(params.get("invalidate"))
        ));
    }

    @Test
    void shouldDeleteAuthenticatedFileWithTypeAuthenticated() throws IOException {
        String fileUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/service_safety_documents/02556a44.png";

        when(uploader.destroy(eq("danasea_demo_data_package/service_safety_documents/02556a44"), anyMap()))
                .thenReturn(Map.of("result", "ok"));

        adapter.deleteFile(fileUrl);

        verify(uploader).destroy(eq("danasea_demo_data_package/service_safety_documents/02556a44"), argThat(params ->
                "authenticated".equals(params.get("type")) && Boolean.TRUE.equals(params.get("invalidate"))
        ));
    }

    @Test
    void shouldDeleteSignedAuthenticatedFileExtractingPublicIdCorrectly() throws IOException {
        String fileUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--nos1VThy--/v1793625652/danasea_demo_data_package/service_safety_documents/7ebfcb235a6e16b05d2711c6411d95acd8ddd8a993d8734229bad91f3e31baf6.png";

        when(uploader.destroy(eq("danasea_demo_data_package/service_safety_documents/7ebfcb235a6e16b05d2711c6411d95acd8ddd8a993d8734229bad91f3e31baf6"), anyMap()))
                .thenReturn(Map.of("result", "ok"));

        adapter.deleteFile(fileUrl);

        verify(uploader).destroy(
                eq("danasea_demo_data_package/service_safety_documents/7ebfcb235a6e16b05d2711c6411d95acd8ddd8a993d8734229bad91f3e31baf6"),
                argThat(params -> "authenticated".equals(params.get("type")) && Boolean.TRUE.equals(params.get("invalidate")))
        );
    }

    @Test
    void shouldDeleteRawFileWithRawResourceType() throws IOException {
        String fileUrl = "https://res.cloudinary.com/demo/raw/upload/v1234567890/services/docs/cert.pdf";

        when(uploader.destroy(eq("services/docs/cert"), anyMap())).thenReturn(Map.of("result", "ok"));

        adapter.deleteFile(fileUrl);

        verify(uploader).destroy(eq("services/docs/cert"), argThat(params ->
                "raw".equals(params.get("resource_type")) && Boolean.TRUE.equals(params.get("invalidate"))
        ));
    }

    @Test
    void shouldDoNothingWhenDeleteFileWithNullOrBlankUrl() throws IOException {
        adapter.deleteFile(null);
        adapter.deleteFile("");
        adapter.deleteFile("   ");

        verify(uploader, never()).destroy(any(), any());
    }

    @Test
    void shouldThrowFileStorageExceptionWhenDestroyFails() throws IOException {
        String fileUrl = "https://res.cloudinary.com/demo/image/upload/v1234567890/services/images/sample.jpg";
        when(uploader.destroy(any(), anyMap())).thenThrow(new RuntimeException("Cloudinary delete failed"));

        assertThrows(FileStorageException.class, () -> adapter.deleteFile(fileUrl));
    }

    @Test
    void shouldExtractPublicIdCorrectly() {
        assertEquals("sample", adapter.extractPublicId("sample"));
        assertEquals("services/images/photo", adapter.extractPublicId("https://res.cloudinary.com/demo/image/upload/v1234567890/services/images/photo.jpg"));
        assertEquals("services/docs/cert", adapter.extractPublicId("https://res.cloudinary.com/demo/raw/upload/v999/services/docs/cert.pdf"));
        assertEquals("photo", adapter.extractPublicId("https://res.cloudinary.com/demo/image/upload/photo.png"));
        assertEquals("danasea_demo_data_package/service_safety_documents/02556a44",
                adapter.extractPublicId("https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/service_safety_documents/02556a44.png"));
        assertEquals("danasea_demo_data_package/service_safety_documents/7ebfcb235a6e16b05d2711c6411d95acd8ddd8a993d8734229bad91f3e31baf6",
                adapter.extractPublicId("https://res.cloudinary.com/xmxguqpi/image/authenticated/s--nos1VThy--/v1793625652/danasea_demo_data_package/service_safety_documents/7ebfcb235a6e16b05d2711c6411d95acd8ddd8a993d8734229bad91f3e31baf6.png"));
    }

    @Test
    void shouldKeepExistingSignedCdnUrlWithoutReSigning() {
        String existingSignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--nos1VThy--/v1793625652/danasea_demo_data_package/service_safety_documents/7ebfcb235a6e16b05d2711c6411d95acd8ddd8a993d8734229bad91f3e31baf6.png";

        String result = adapter.generateDeliveryUrl(existingSignedUrl);

        assertEquals(existingSignedUrl, result);
        verify(cloudinary, never()).url();
        verifyNoInteractions(mockUrl);
    }

    @Test
    void shouldGenerateSignedDeliveryUrlWhenAuthenticatedUrlIsUnsigned() {
        String unsignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/service_safety_documents/02556a44.png";
        String generatedSignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--generatedSig--/v1792170628/danasea_demo_data_package/service_safety_documents/02556a44.png";

        when(mockUrl.generate(eq("danasea_demo_data_package/service_safety_documents/02556a44")))
                .thenReturn(generatedSignedUrl);

        String result = adapter.generateDeliveryUrl(unsignedUrl);

        assertEquals(generatedSignedUrl, result);
        verify(mockUrl).resourceType("image");
        verify(mockUrl).type("authenticated");
        verify(mockUrl).signed(true);
        verify(mockUrl).secure(true);
        verify(mockUrl).version("1792170628");
        verify(mockUrl).format("png");
        verify(mockUrl).generate("danasea_demo_data_package/service_safety_documents/02556a44");
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
        String existingSignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--nos1VThy--/v1793625652/danasea_demo_data_package/service_safety_documents/7ebfcb23.png";
        String unsignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1792170628/danasea_demo_data_package/service_safety_documents/02556a44.png";
        when(mockUrl.generate(any())).thenReturn("https://res.cloudinary.com/xmxguqpi/image/authenticated/s--sig--/v1792170628/danasea_demo_data_package/service_safety_documents/02556a44.png");

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
        CloudinaryStorageAdapter realAdapter = new CloudinaryStorageAdapter(realCloudinary);

        String unsignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/v1793625652/danasea_demo_data_package/service_safety_documents/7ebfcb235a6e16b05d2711c6411d95acd8ddd8a993d8734229bad91f3e31baf6.png";
        String expectedSignedUrl = "https://res.cloudinary.com/xmxguqpi/image/authenticated/s--nos1VThy--/v1793625652/danasea_demo_data_package/service_safety_documents/7ebfcb235a6e16b05d2711c6411d95acd8ddd8a993d8734229bad91f3e31baf6.png";

        String result = realAdapter.generateDeliveryUrl(unsignedUrl);

        assertEquals(expectedSignedUrl, result);
        assertTrue(realAdapter.isSignedAuthenticatedUrl(result));
    }
}
