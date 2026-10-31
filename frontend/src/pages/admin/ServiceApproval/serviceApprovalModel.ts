import type { MOCK_ADMIN_SERVICES } from '../../../data/adminMockData';
import type { ServiceStatus } from '../../../types';

export type ServiceRecord = typeof MOCK_ADMIN_SERVICES[number];

export type ServiceDecisionKind = 'approve' | 'reject' | 'pause';

export type ServiceDecision = {
  serviceId: string;
  kind: ServiceDecisionKind;
  reason: string;
  note: string;
  at: string;
};

export type ServiceReviewEvent = ServiceDecision & {
  previousStatus: ServiceStatus;
};

export type ServiceReviewState = {
  services: ServiceRecord[];
  timeline: ServiceReviewEvent[];
};

export function filterServices(
  services: ServiceRecord[],
  query: string,
  status: string,
  categoryId: string
): ServiceRecord[] {
  const search = query.trim().toLocaleLowerCase('vi');
  return services.filter(service => {
    const matchesSearch =
      !search ||
      service.name.toLocaleLowerCase('vi').includes(search) ||
      (service.nameEn && service.nameEn.toLocaleLowerCase('vi').includes(search)) ||
      service.id.toLocaleLowerCase('vi').includes(search) ||
      (service.vendorName && service.vendorName.toLocaleLowerCase('vi').includes(search)) ||
      (service.locationName && service.locationName.toLocaleLowerCase('vi').includes(search));

    const matchesStatus = status === 'all' || service.status === status;
    const matchesCategory = categoryId === 'all' || service.categoryId === categoryId;

    return matchesSearch && matchesStatus && matchesCategory;
  });
}

export function applyServiceDecision(
  state: ServiceReviewState,
  decision: ServiceDecision,
  demo: boolean
): ServiceReviewState {
  const service = state.services.find(item => item.id === decision.serviceId);
  if (!demo || !service) return state;

  let newStatus: ServiceStatus = service.status;
  let newRejectionReason = service.rejectionReason;

  if (decision.kind === 'approve') {
    newStatus = 'ACTIVE';
    newRejectionReason = '';
  } else if (decision.kind === 'reject') {
    newStatus = 'REJECTED';
    newRejectionReason = decision.reason.trim();
  } else if (decision.kind === 'pause') {
    newStatus = 'PAUSED';
  }

  return {
    services: state.services.map(item =>
      item.id !== service.id
        ? item
        : {
            ...item,
            status: newStatus,
            rejectionReason: newRejectionReason,
            updatedAt: decision.at,
          }
    ),
    timeline: [
      ...state.timeline,
      {
        ...decision,
        reason: decision.reason.trim(),
        note: decision.note.trim(),
        previousStatus: service.status,
      },
    ],
  };
}
