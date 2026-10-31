package com.danasea.backend.modules.service.application.ports;

public interface FileStoragePort {

    /**
     * Uploads file data to remote storage.
     *
     * @param fileData the raw byte array of the file
     * @param originalFilename the original filename
     * @param folder the target storage folder/path
     * @return secure URL of the uploaded file
     */
    String uploadFile(byte[] fileData, String originalFilename, String folder);

    /**
     * Deletes a file from remote storage by its URL or identifier.
     *
     * @param fileUrl the URL or public ID of the file to delete
     */
    void deleteFile(String fileUrl);

    /**
     * Generates a signed or deliverable URL for accessing stored files.
     * For authenticated assets, returns an expiring delivery URL.
     *
     * @param fileUrl the stored canonical file URL
     * @return expiring delivery URL or original URL
     */
    default String generateDeliveryUrl(String fileUrl) {
        return generateDeliveryUrl(fileUrl, 300);
    }

    /**
     * Generates an expiring delivery URL with explicit TTL.
     *
     * @param fileUrl the stored canonical file URL
     * @param expirySeconds the TTL in seconds
     * @return expiring delivery URL or original URL
     */
    default String generateDeliveryUrl(String fileUrl, int expirySeconds) {
        return fileUrl;
    }
}
