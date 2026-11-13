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
};
