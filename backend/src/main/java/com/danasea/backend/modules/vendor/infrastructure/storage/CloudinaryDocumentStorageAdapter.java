package com.danasea.backend.modules.vendor.infrastructure.storage;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.danasea.backend.modules.vendor.application.ports.DocumentStoragePort;

@Component
public class CloudinaryDocumentStorageAdapter implements DocumentStoragePort {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryDocumentStorageAdapter.class);
    private static final int DEFAULT_EXPIRY_SECONDS = 300;

    private final Cloudinary cloudinary;

    public CloudinaryDocumentStorageAdapter(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public String uploadDocument(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        try {
            Map<String, Object> params = new HashMap<>();
            String targetFolder = (folder != null && !folder.isBlank()) ? folder : "vendor_documents";
            params.put("folder", targetFolder);
            params.put("resource_type", "auto");
            params.put("type", "authenticated");

            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && !originalFilename.isBlank()) {
                params.put("use_filename", true);
                params.put("unique_filename", true);
            }

            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), params);
            Object secureUrl = uploadResult.get("secure_url");
            if (secureUrl == null) {
                secureUrl = uploadResult.get("url");
            }

            if (secureUrl == null) {
                throw new RuntimeException("Cloudinary upload did not return a valid URL.");
            }

            return secureUrl.toString();
        } catch (IOException e) {
            log.error("Failed to read file for Cloudinary upload: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read file for Cloudinary upload: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateDeliveryUrl(String fileUrl) {
        return generateDeliveryUrl(fileUrl, DEFAULT_EXPIRY_SECONDS);
    }

    @Override
    public String generateDeliveryUrl(String fileUrl, int expirySeconds) {
        if (fileUrl == null || fileUrl.isBlank() || !fileUrl.contains("/authenticated/")) {
            return fileUrl;
        }

        try {
            String publicId = extractPublicId(fileUrl);
            String format = extractFormat(fileUrl);
            long expiresAt = Instant.now().getEpochSecond() + expirySeconds;

            Map<String, Object> options = new HashMap<>();
            options.put("type", "authenticated");
            options.put("expires_at", expiresAt);

            return cloudinary.privateDownload(publicId, format != null ? format : "png", options);
        } catch (Exception e) {
            log.warn("Failed to generate private download URL for {}: {}", fileUrl, e.getMessage());
            return fileUrl;
        }
    }

    public String extractPublicId(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }

        if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
            return fileUrl;
        }

        int uploadIndex = fileUrl.indexOf("/upload/");
        int authIndex = fileUrl.indexOf("/authenticated/");
        int prefixLength;
        int startIndex;
        if (uploadIndex != -1) {
            startIndex = uploadIndex;
            prefixLength = "/upload/".length();
        } else if (authIndex != -1) {
            startIndex = authIndex;
            prefixLength = "/authenticated/".length();
        } else {
            return fileUrl;
        }

        String pathAfterPrefix = fileUrl.substring(startIndex + prefixLength);

        if (pathAfterPrefix.matches("^v\\d+/.*")) {
            pathAfterPrefix = pathAfterPrefix.substring(pathAfterPrefix.indexOf('/') + 1);
        } else {
            int vIndex = pathAfterPrefix.indexOf("/v");
            if (vIndex != -1) {
                int slashAfterV = pathAfterPrefix.indexOf('/', vIndex + 2);
                if (slashAfterV != -1 && pathAfterPrefix.substring(vIndex + 2, slashAfterV).matches("\\d+")) {
                    pathAfterPrefix = pathAfterPrefix.substring(slashAfterV + 1);
                }
            }
        }

        int dotIndex = pathAfterPrefix.lastIndexOf('.');
        if (dotIndex != -1) {
            pathAfterPrefix = pathAfterPrefix.substring(0, dotIndex);
        }

        return pathAfterPrefix;
    }

    public String extractFormat(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        int dotIndex = fileUrl.lastIndexOf('.');
        if (dotIndex != -1 && dotIndex > fileUrl.lastIndexOf('/')) {
            return fileUrl.substring(dotIndex + 1);
        }
        return null;
    }
}
