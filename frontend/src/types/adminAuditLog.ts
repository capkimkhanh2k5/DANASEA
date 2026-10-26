export interface AdminAuditLogDto {
  id: string;
  actorUserId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  metadata: string | null;
  timestamp: string; // ISO 8601 OffsetDateTime from backend createdAt
}

export interface AdminAuditLogQueryParams {
  page?: number;
  size?: number;
}
