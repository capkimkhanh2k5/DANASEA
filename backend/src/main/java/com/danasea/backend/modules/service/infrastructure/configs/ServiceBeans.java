package com.danasea.backend.modules.service.infrastructure.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.danasea.backend.modules.service.application.usecases.ApproveSafetyDocumentUseCase;
import com.danasea.backend.modules.service.application.usecases.ApproveServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.CreateServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.DeleteServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.GetAdminServicesUseCase;
import com.danasea.backend.modules.service.application.usecases.GetServiceDetailUseCase;
import com.danasea.backend.modules.service.application.usecases.GetVendorServicesUseCase;
import com.danasea.backend.modules.service.application.usecases.PauseServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.RejectServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.ResumeServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.SubmitServiceForReviewUseCase;
import com.danasea.backend.modules.service.application.usecases.UpdateServiceUseCase;
import com.danasea.backend.modules.service.domain.ports.AuditLogPort;
import com.danasea.backend.modules.service.domain.ports.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSafetyDocumentRepository;

@Configuration
public class ServiceBeans {

    @Bean
    public CreateServiceUseCase createServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            CategoryRepositoryPort categoryRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            VendorPort vendorPort) {
        return new CreateServiceUseCase(serviceRepository, categoryRepository, serviceImageRepository, vendorPort);
    }

    @Bean
    public GetVendorServicesUseCase getVendorServicesUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            VendorPort vendorPort) {
        return new GetVendorServicesUseCase(serviceRepository, serviceImageRepository, vendorPort);
    }

    @Bean
    public GetServiceDetailUseCase getServiceDetailUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            VendorPort vendorPort) {
        return new GetServiceDetailUseCase(serviceRepository, serviceImageRepository, vendorPort);
    }

    @Bean
    public UpdateServiceUseCase updateServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            CategoryRepositoryPort categoryRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            VendorPort vendorPort) {
        return new UpdateServiceUseCase(serviceRepository, categoryRepository, serviceImageRepository, vendorPort);
    }

    @Bean
    public SubmitServiceForReviewUseCase submitServiceForReviewUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            VendorPort vendorPort) {
        return new SubmitServiceForReviewUseCase(serviceRepository, serviceImageRepository, vendorPort);
    }

    @Bean
    public PauseServiceUseCase pauseServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            VendorPort vendorPort) {
        return new PauseServiceUseCase(serviceRepository, serviceImageRepository, vendorPort);
    }

    @Bean
    public ResumeServiceUseCase resumeServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            VendorPort vendorPort,
            ApproveSafetyDocumentUseCase approveSafetyDocumentUseCase) {
        return new ResumeServiceUseCase(serviceRepository, serviceImageRepository, vendorPort, approveSafetyDocumentUseCase);
    }

    @Bean
    public DeleteServiceUseCase deleteServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            VendorPort vendorPort,
            ServiceSlotRepositoryPort serviceSlotRepository,
            JpaServiceSafetyDocumentRepository safetyDocumentRepository) {
        return new DeleteServiceUseCase(serviceRepository, serviceImageRepository, vendorPort, serviceSlotRepository, safetyDocumentRepository);
    }

    @Bean
    public GetAdminServicesUseCase getAdminServicesUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository) {
        return new GetAdminServicesUseCase(serviceRepository, serviceImageRepository);
    }

    @Bean
    public ApproveServiceUseCase approveServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            AuditLogPort auditLogPort,
            ApproveSafetyDocumentUseCase approveSafetyDocumentUseCase) {
        return new ApproveServiceUseCase(serviceRepository, serviceImageRepository, auditLogPort,
                approveSafetyDocumentUseCase);
    }

    @Bean
    public RejectServiceUseCase rejectServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceImageRepositoryPort serviceImageRepository,
            AuditLogPort auditLogPort) {
        return new RejectServiceUseCase(serviceRepository, serviceImageRepository, auditLogPort);
    }
}
