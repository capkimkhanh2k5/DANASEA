import type { Role } from './index';

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface AdminUserSummaryDto {
  id: string;
  email: string;
  phone: string | null;
  fullName: string | null;
  role: Role;
  avatarUrl: string | null;
  isEmailVerified: boolean;
  isLocked: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUserDetailDto {
  id: string;
  email: string;
  phone: string | null;
  fullName: string | null;
  role: Role;
  avatarUrl: string | null;
  isEmailVerified: boolean;
  isLocked: boolean;
  locale: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface LockUserRequest {
  reason?: string;
}

export interface AdminUserQueryParams {
  page?: number;
  size?: number;
  role?: Role;
  isLocked?: boolean;
  search?: string;
}
