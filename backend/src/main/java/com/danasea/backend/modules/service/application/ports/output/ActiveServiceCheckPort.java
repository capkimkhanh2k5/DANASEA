package com.danasea.backend.modules.service.application.ports.output;

import java.util.UUID;

public interface ActiveServiceCheckPort {
    boolean hasActiveServices(UUID categoryId);
    boolean hasPublishedServicesWithoutApprovedSafetyDocuments(UUID categoryId);
}
