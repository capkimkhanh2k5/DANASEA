package com.danasea.backend.modules.vendor.application.ports;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentStoragePort {

    String uploadDocument(MultipartFile file, String folder);

    default String generateDeliveryUrl(String fileUrl) {
        return generateDeliveryUrl(fileUrl, 300);
    }

    default String generateDeliveryUrl(String fileUrl, int expirySeconds) {
        return fileUrl;
    }
}
