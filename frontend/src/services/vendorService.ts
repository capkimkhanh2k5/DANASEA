import { apiClient } from './apiClient';
import type {
  VendorServiceDto,
  CreateServiceRequest,
  UpdateServiceRequest,
  VendorServiceOptionDto,
  CreateServiceOptionRequest,
  UpdateServiceOptionRequest,
  ServiceImageDto,
  SafetyDocumentDto,
  VendorServiceSlotDto,
  CreateServiceSlotRequest,
  UpdateServiceSlotRequest,
  BatchCreateServiceSlotsRequest,
  BatchCreateServiceSlotsResponse,
  OperatingScheduleDto,
  PutOperatingScheduleRequest,
  OperatingScheduleUpdateResponse,
} from '../types/vendorService';

export const vendorService = {
  // 1. SERVICES MANAGEMENT (V05, V06, V07)
  async getMyServices(): Promise<VendorServiceDto[]> {
    const res = await apiClient.get<VendorServiceDto[]>('/vendor/services');
    return res.data || [];
  },

  async getServiceDetail(id: string): Promise<VendorServiceDto> {
    const res = await apiClient.get<VendorServiceDto>(`/vendor/services/${id}`);
    return res.data;
  },

  async createService(data: CreateServiceRequest): Promise<VendorServiceDto> {
    const res = await apiClient.post<VendorServiceDto>('/vendor/services', data);
    return res.data;
  },

  async updateService(id: string, data: UpdateServiceRequest): Promise<VendorServiceDto> {
    const res = await apiClient.patch<VendorServiceDto>(`/vendor/services/${id}`, data);
    return res.data;
  },

  async submitForReview(id: string): Promise<VendorServiceDto> {
    const res = await apiClient.post<VendorServiceDto>(`/vendor/services/${id}/submit`);
    return res.data;
  },

  async pauseService(id: string): Promise<VendorServiceDto> {
    const res = await apiClient.patch<VendorServiceDto>(`/vendor/services/${id}/pause`);
    return res.data;
  },

  async resumeService(id: string): Promise<VendorServiceDto> {
    const res = await apiClient.patch<VendorServiceDto>(`/vendor/services/${id}/resume`);
    return res.data;
  },

  async deleteService(id: string): Promise<void> {
    await apiClient.delete(`/vendor/services/${id}`);
  },

  // 2. SERVICE OPTIONS MANAGEMENT (V06 Tab 3)
  async getOptions(serviceId: string): Promise<VendorServiceOptionDto[]> {
    const res = await apiClient.get<VendorServiceOptionDto[]>(`/vendor/services/${serviceId}/options`);
    return res.data || [];
  },

  async createOption(serviceId: string, data: CreateServiceOptionRequest): Promise<VendorServiceOptionDto> {
    const res = await apiClient.post<VendorServiceOptionDto>(`/vendor/services/${serviceId}/options`, data);
    return res.data;
  },

  async updateOption(serviceId: string, optionId: string, data: UpdateServiceOptionRequest): Promise<VendorServiceOptionDto> {
    const res = await apiClient.patch<VendorServiceOptionDto>(`/vendor/services/${serviceId}/options/${optionId}`, data);
    return res.data;
  },

  // 3. SERVICE IMAGES MANAGEMENT (V06 Tab 2)
  async uploadImage(serviceId: string, file: File): Promise<ServiceImageDto> {
    const formData = new FormData();
    formData.append('file', file);
    const res = await apiClient.post<ServiceImageDto>(`/vendor/services/${serviceId}/images`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return res.data;
  },

  async deleteImage(serviceId: string, imageId: string): Promise<void> {
    await apiClient.delete(`/vendor/services/${serviceId}/images/${imageId}`);
  },

  async reorderImages(serviceId: string, imageIds: string[]): Promise<ServiceImageDto[]> {
    const res = await apiClient.patch<ServiceImageDto[]>(`/vendor/services/${serviceId}/images/reorder`, {
      imageIds,
    });
    return res.data || [];
  },

  // 4. SAFETY DOCUMENTS (V06 Tab 4)
  async getSafetyDocuments(serviceId: string): Promise<SafetyDocumentDto[]> {
    const res = await apiClient.get<SafetyDocumentDto[]>(`/vendor/services/${serviceId}/safety-documents`);
    return res.data || [];
  },

  async uploadSafetyDocument(serviceId: string, file: File): Promise<SafetyDocumentDto> {
    const formData = new FormData();
    formData.append('file', file);
    const res = await apiClient.post<SafetyDocumentDto>(`/vendor/services/${serviceId}/safety-documents`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return res.data;
  },

  async withdrawSafetyDocument(serviceId: string, documentId: string): Promise<SafetyDocumentDto> {
    const res = await apiClient.post<SafetyDocumentDto>(`/vendor/services/${serviceId}/safety-documents/${documentId}/withdraw`);
    return res.data;
  },

  async replaceSafetyDocument(serviceId: string, documentId: string, file: File): Promise<SafetyDocumentDto> {
    const formData = new FormData();
    formData.append('file', file);
    const res = await apiClient.post<SafetyDocumentDto>(`/vendor/services/${serviceId}/safety-documents/${documentId}/replace`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return res.data;
  },

  async deleteSafetyDocument(serviceId: string, documentId: string): Promise<void> {
    await apiClient.delete(`/vendor/services/${serviceId}/safety-documents/${documentId}`);
  },

  // 5. SERVICE SLOTS / SCHEDULE (V08, V09)
  async getSlots(serviceId: string): Promise<VendorServiceSlotDto[]> {
    const res = await apiClient.get<VendorServiceSlotDto[]>(`/vendor/services/${serviceId}/slots`);
    return res.data || [];
  },

  async createSlot(serviceId: string, data: CreateServiceSlotRequest): Promise<VendorServiceSlotDto> {
    const res = await apiClient.post<VendorServiceSlotDto>(`/vendor/services/${serviceId}/slots`, data);
    return res.data;
  },

  async updateSlot(serviceId: string, slotId: string, data: UpdateServiceSlotRequest): Promise<VendorServiceSlotDto> {
    const res = await apiClient.patch<VendorServiceSlotDto>(`/vendor/services/${serviceId}/slots/${slotId}`, data);
    return res.data;
  },

  async batchCreateSlots(serviceId: string, data: BatchCreateServiceSlotsRequest): Promise<BatchCreateServiceSlotsResponse> {
    const res = await apiClient.post<BatchCreateServiceSlotsResponse>(`/vendor/services/${serviceId}/slots/batch`, data);
    return res.data;
  },

  // 6. OPERATING SCHEDULE (90-day recurring)
  async getOperatingSchedule(serviceId: string): Promise<OperatingScheduleDto | null> {
    try {
      const res = await apiClient.get<OperatingScheduleDto>(`/vendor/services/${serviceId}/operating-schedule`);
      return res.data;
    } catch {
      // 404 means no schedule set yet
      return null;
    }
  },

  async putOperatingSchedule(serviceId: string, data: PutOperatingScheduleRequest): Promise<OperatingScheduleUpdateResponse> {
    const res = await apiClient.put<OperatingScheduleUpdateResponse>(`/vendor/services/${serviceId}/operating-schedule`, data);
    return res.data;
  },

  // 7. OPERATING SCHEDULE EXCEPTIONS (Ngày nghỉ đột xuất, bảo dưỡng, đóng bán)
  async getOperatingScheduleExceptions(serviceId: string): Promise<OperatingScheduleExceptionDto[]> {
    const res = await apiClient.get<OperatingScheduleExceptionDto[]>(`/vendor/services/${serviceId}/operating-schedule/exceptions`);
    return res.data || [];
  },

  async addOperatingScheduleException(
    serviceId: string,
    data: { dateFrom: string; dateTo: string; startTime?: string; reason: string }
  ): Promise<OperatingScheduleExceptionDto> {
    const res = await apiClient.post<OperatingScheduleExceptionDto>(
      `/vendor/services/${serviceId}/operating-schedule/exceptions`,
      data
    );
    return res.data;
  },

  async removeOperatingScheduleException(serviceId: string, exceptionId: string): Promise<void> {
    await apiClient.delete(`/vendor/services/${serviceId}/operating-schedule/exceptions/${exceptionId}`);
  },

  // 8. DEPARTURE CANCELLATION (Hủy ca do bão biển, sự cố kỹ thuật)
  async cancelSlotDeparture(
    serviceId: string,
    slotId: string,
    reason: string
  ): Promise<SlotDepartureCancellationDto> {
    const res = await apiClient.post<SlotDepartureCancellationDto>(
      `/vendor/services/${serviceId}/slots/${slotId}/cancel`,
      { reason }
    );
    return res.data;
  },

  async getSlotDepartureCancellation(
    serviceId: string,
    slotId: string
  ): Promise<SlotDepartureCancellationStatusDto> {
    const res = await apiClient.get<SlotDepartureCancellationStatusDto>(
      `/vendor/services/${serviceId}/slots/${slotId}/cancel`
    );
    return res.data;
  },
};

export interface OperatingScheduleExceptionDto {
  id: string;
  dateFrom: string;
  dateTo: string;
  startTime?: string | null;
  reason: string;
}

export interface SlotDepartureCancellationDto {
  slotId: string;
  reason: string;
  cancelledAt: string;
}

export interface SlotDepartureCancellationStatusDto {
  cancellation: SlotDepartureCancellationDto;
  existingRefundsToReview: Array<{
    subOrderId: string;
    refundAmount: number;
    status: string;
  }>;
}
