package com.danasea.backend.modules.service.infrastructure.storage;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudinary.Cloudinary;
import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.domain.exceptions.FileStorageException;

public class CloudinaryStorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryStorageAdapter.class);
    private static final int DEFAULT_EXPIRY_SECONDS = 300;

    private final Cloudinary cloudinary;

    public CloudinaryStorageAdapter(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public String uploadFile(byte[] fileData, String originalFilename, String folder) {
        if (fileData == null || fileData.length == 0) {
            throw new FileStorageException("Cannot upload empty file.");
        }

        try {
            Map<String, Object> params = new HashMap<>();
            if (folder != null && !folder.isBlank()) {
                params.put("folder", folder);
                if (folder.contains("document")) {
                    params.put("type", "authenticated");
                }
            }
            params.put("resource_type", "auto");

            if (originalFilename != null && !originalFilename.isBlank()) {
                params.put("use_filename", true);
                params.put("unique_filename", true);
            }

            Map<?, ?> uploadResult = cloudinary.uploader().upload(fileData, params);
            Object secureUrl = uploadResult.get("secure_url");
            if (secureUrl == null) {
                secureUrl = uploadResult.get("url");
            }

            if (secureUrl == null) {
                throw new FileStorageException("Cloudinary upload did not return a valid URL.");
            }

            return secureUrl.toString();
        } catch (FileStorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new FileStorageException("Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        try {
            String publicId = extractPublicId(fileUrl);
            if (publicId == null || publicId.isBlank()) {
                log.warn("Could not extract publicId from fileUrl: {}", fileUrl);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("invalidate", true);
            if (fileUrl.contains("/raw/")) {
                params.put("resource_type", "raw");
            }
            if (fileUrl.contains("/authenticated/")) {
                params.put("type", "authenticated");
            }

            cloudinary.uploader().destroy(publicId, params);
        } catch (Exception e) {
            log.error("Failed to delete file from Cloudinary: {}", e.getMessage(), e);
            throw new FileStorageException("Failed to delete file from Cloudinary: " + e.getMessage(), e);
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
            String format = "png";
            int dotIndex = fileUrl.lastIndexOf('.');
            if (dotIndex != -1 && dotIndex > fileUrl.lastIndexOf('/')) {
                format = fileUrl.substring(dotIndex + 1);
            }

            long expiresAt = Instant.now().getEpochSecond() + expirySeconds;
            Map<String, Object> options = new HashMap<>();
            options.put("type", "authenticated");
            options.put("expires_at", expiresAt);

            return cloudinary.privateDownload(publicId, format, options);
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

        String pathAfterUpload = fileUrl.substring(startIndex + prefixLength);

        // Strip version or transformations followed by version (e.g. "v1234567890/" or "c_fill,w_300/v1234567890/")
        if (pathAfterUpload.matches("^v\\d+/.*")) {
            pathAfterUpload = pathAfterUpload.substring(pathAfterUpload.indexOf('/') + 1);
        } else {
            int vIndex = pathAfterUpload.indexOf("/v");
            if (vIndex != -1) {
                int slashAfterV = pathAfterUpload.indexOf('/', vIndex + 2);
                if (slashAfterV != -1 && pathAfterUpload.substring(vIndex + 2, slashAfterV).matches("\\d+")) {
                    pathAfterUpload = pathAfterUpload.substring(slashAfterV + 1);
                }
            }
        }

        int dotIndex = pathAfterUpload.lastIndexOf('.');
        if (dotIndex != -1) {
            pathAfterUpload = pathAfterUpload.substring(0, dotIndex);
        }

        return pathAfterUpload;
    }
}
