import { apiClient } from './apiClient';

export interface BackendEquipment {
  id: string;
  vendorId?: string;
  name: string;
  compatibilityGroup: string;
  capacity: number;
  createdAt?: string;
}

export interface CreateBackendEquipmentRequest {
  name: string;
  compatibilityGroup: string;
  capacity: number;
}

export interface EquipmentMaintenanceDto {
  id: string;
  equipmentId: string;
  startsAt: string;
  endsAt: string;
  urgent: boolean;
  reason: string;
  affectedSlots: number;
  movedAllocations: number;
}

export const vendorEquipmentService = {
  /**
   * Fetch real equipment from Backend API GET /api/vendor/equipment
   */
  async getEquipment(): Promise<BackendEquipment[]> {
    try {
      const res = await apiClient.get<BackendEquipment[]>('/vendor/equipment');
      return res.data || [];
    } catch (err) {
      console.warn('[vendorEquipmentService] Failed to fetch equipment from API, falling back:', err);
      return [];
    }
  },

  /**
   * Create equipment via Backend API POST /api/vendor/equipment
   */
  async createEquipment(data: CreateBackendEquipmentRequest): Promise<BackendEquipment> {
    const res = await apiClient.post<BackendEquipment>('/vendor/equipment', data);
    return res.data;
  },

  /**
   * Gán thiết bị vào ca vận hành cụ thể
   * PUT /api/vendor/equipment/{equipmentId}/slots/{slotId}/units/{unitNumber}
   */
  async attachEquipmentToSlot(equipmentId: string, slotId: string, unitNumber: number): Promise<void> {
    await apiClient.put(`/vendor/equipment/${equipmentId}/slots/${slotId}/units/${unitNumber}`);
  },

  /**
   * Gỡ liên kết thiết bị khỏi một đơn vị chưa có khách của ca vận hành
   * DELETE /api/vendor/equipment/{equipmentId}/slots/{slotId}/units/{unitNumber}
   */
  async detachEquipmentFromSlot(equipmentId: string, slotId: string, unitNumber: number): Promise<void> {
    await apiClient.delete(`/vendor/equipment/${equipmentId}/slots/${slotId}/units/${unitNumber}`);
  },

  /**
   * Lên lịch hoặc cập nhật bảo trì phương tiện / thiết bị
   * PUT /api/vendor/equipment/{equipmentId}/maintenance/{maintenanceId}
   */
  async scheduleMaintenance(
    equipmentId: string,
    maintenanceIdOrData: string | { start: string; end: string; urgent: boolean; reason: string },
    optionalData?: { start: string; end: string; urgent: boolean; reason: string }
  ): Promise<EquipmentMaintenanceDto> {
    const maintenanceId = typeof maintenanceIdOrData === 'string' ? maintenanceIdOrData : crypto.randomUUID();
    const data = typeof maintenanceIdOrData === 'string' ? optionalData! : maintenanceIdOrData;
    const res = await apiClient.put<EquipmentMaintenanceDto>(
      `/vendor/equipment/${equipmentId}/maintenance/${maintenanceId}`,
      data
    );
    return res.data;
  },

  /**
   * Đọc chi tiết kết quả và lịch bảo trì
   * GET /api/vendor/equipment/{equipmentId}/maintenance/{maintenanceId}
   */
  async getMaintenance(equipmentId: string, maintenanceId: string): Promise<EquipmentMaintenanceDto> {
    const res = await apiClient.get<EquipmentMaintenanceDto>(
      `/vendor/equipment/${equipmentId}/maintenance/${maintenanceId}`
    );
    return res.data;
  },
};
