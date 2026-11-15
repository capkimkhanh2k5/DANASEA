export interface ChecklistItem {
  category: string;
  itemName: string;
  tip: string;
  mandatory: boolean;
}

export interface SubOrderBriefing {
  subOrderId: string;
  serviceId: string;
  serviceTitle: string;
  meetingLocation: string;
  departureDate: string;
  departureTime: string;
  weatherSafetyStatus: 'ACCEPTABLE' | 'CAUTION' | 'UNSAFE' | 'UNKNOWN' | 'NOT_REQUIRED';
  marineConditionsSummary: string;
  packingChecklist: ChecklistItem[];
  arrivalAndSafetyGuidelines: string[];
}

export interface MarineBriefingOverview {
  masterOrderId: string;
  orderCode: string;
  customerId: string;
  totalActivities: number;
  activities: SubOrderBriefing[];
  generatedAt: string;
}
