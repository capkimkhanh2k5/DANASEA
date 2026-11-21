package com.danasea.backend.modules.vendor.infrastructure.storage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Url;
import com.danasea.backend.modules.vendor.application.ports.DocumentStoragePort;

@Component
public class CloudinaryDocumentStorageAdapter implements DocumentStoragePort {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryDocumentStorageAdapter.class);
    private static final Pattern SIGNED_PATTERN = Pattern.compile("/s--[^/]+--/");
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?:^|/)v(\\d+)/");

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

            String uploadedUrl = secureUrl.toString();
            return buildSignedCdnUrl(uploadResult, uploadedUrl);
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
        return generateDeliveryUrl(fileUrl, 0);
    }

    @Override
    public String generateDeliveryUrl(String fileUrl, int expirySeconds) {
        if (fileUrl == null || fileUrl.isBlank() || !fileUrl.contains("/authenticated/")) {
            return fileUrl;
        }

        // 1. If URL already has a valid CDN signature, return it directly
        if (isSignedAuthenticatedUrl(fileUrl)) {
            return fileUrl;
        }

        // 2. If authenticated but unsigned, generate Signed CDN Delivery URL using Cloudinary SDK
        try {
            String publicId = extractPublicId(fileUrl);
            if (publicId == null || publicId.isBlank()) {
                return fileUrl;
            }

            String version = extractVersion(fileUrl);
            String format = extractFormat(fileUrl);
            String resourceType = extractResourceType(fileUrl);

            return generateSignedCdnUrl(publicId, version, format, resourceType);
        } catch (Exception e) {
            log.warn("Failed to generate signed CDN delivery URL for {}: {}", fileUrl, e.getMessage());
            return fileUrl;
        }
    }

    public boolean isSignedAuthenticatedUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank() || !fileUrl.contains("/authenticated/")) {
            return false;
        }
        return SIGNED_PATTERN.matcher(fileUrl).find();
    }

    public String generateSignedCdnUrl(String publicId, String version, String format, String resourceType) {
        Url urlBuilder = cloudinary.url();
        if (resourceType != null && !resourceType.isBlank()) {
            urlBuilder.resourceType(resourceType);
        } else {
            urlBuilder.resourceType("image");
        }
        urlBuilder.type("authenticated");
        urlBuilder.secure(true);
        urlBuilder.signed(true);
        if (version != null && !version.isBlank()) {
            urlBuilder.version(version);
        }
        String cleanPublicId = publicId;
        if (format != null && !format.isBlank()) {
            if (cleanPublicId.endsWith("." + format)) {
                cleanPublicId = cleanPublicId.substring(0, cleanPublicId.length() - format.length() - 1);
            }
            urlBuilder.format(format);
        }
        return urlBuilder.generate(cleanPublicId);
    }

    private String buildSignedCdnUrl(Map<?, ?> uploadResult, String fallbackUrl) {
        try {
            String publicId = (String) uploadResult.get("public_id");
            if (publicId == null || publicId.isBlank()) {
                return generateDeliveryUrl(fallbackUrl);
            }

            Object versionObj = uploadResult.get("version");
            String version = versionObj != null ? String.valueOf(versionObj) : null;
            String format = (String) uploadResult.get("format");
            String resourceType = (String) uploadResult.get("resource_type");
            if (resourceType == null || resourceType.isBlank()) {
                resourceType = "image";
            }

            return generateSignedCdnUrl(publicId, version, format, resourceType);
        } catch (Exception e) {
            log.warn("Failed to build signed CDN URL from upload result: {}", e.getMessage());
            return generateDeliveryUrl(fallbackUrl);
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

        // Strip signature if present (e.g. "s--signature--/")
        if (pathAfterPrefix.matches("^s--[^/]+--/.*")) {
            pathAfterPrefix = pathAfterPrefix.substring(pathAfterPrefix.indexOf('/') + 1);
        }

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

        int queryIndex = pathAfterPrefix.indexOf('?');
        if (queryIndex != -1) {
            pathAfterPrefix = pathAfterPrefix.substring(0, queryIndex);
        }

        int dotIndex = pathAfterPrefix.lastIndexOf('.');
        if (dotIndex != -1) {
            pathAfterPrefix = pathAfterPrefix.substring(0, dotIndex);
        }

        return pathAfterPrefix;
    }

    public String extractVersion(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(fileUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public String extractFormat(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        String cleanUrl = fileUrl;
        int queryIndex = cleanUrl.indexOf('?');
        if (queryIndex != -1) {
            cleanUrl = cleanUrl.substring(0, queryIndex);
        }
        int dotIndex = cleanUrl.lastIndexOf('.');
        if (dotIndex != -1 && dotIndex > cleanUrl.lastIndexOf('/')) {
            return cleanUrl.substring(dotIndex + 1);
        }
        return null;
    }

    public String extractResourceType(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return "image";
        }
        if (fileUrl.contains("/raw/")) {
            return "raw";
        }
        if (fileUrl.contains("/video/")) {
            return "video";
        }
        return "image";
    }
}
