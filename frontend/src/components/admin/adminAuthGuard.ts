export type AdminAuthAccessResult =
  | { allowed: true; reason: 'demo' | 'admin' }
  | { allowed: false; reason: 'unauthenticated' }
  | { allowed: false; reason: 'forbidden'; role: string };

export interface AdminAuthContext {
  isDev: boolean;
  demoParam: boolean;
  user: { role?: string; fullName?: string; email?: string } | null;
  isAuthenticated: boolean;
  token?: string | null;
}

/**
 * Đánh giá quyền truy cập phân hệ Quản trị viên (/admin/*).
 *
 * Nguyên tắc bảo mật G2:
 * 1. Demo Mode CHỈ ĐƯỢC PHÉP hoạt động khi đang chạy ở môi trường Development (localhost).
 * 2. Trên Production (isDev = false), query param `?demo=1` TUYỆT ĐỐI BỊ VÔ HIỆU HÓA,
 *    không thể dùng để bypass cơ chế xác thực.
 * 3. Ở Real Mode:
 *    - Chưa đăng nhập (không có user / isAuthenticated = false / không có token) -> 'unauthenticated'
 *    - Đã đăng nhập nhưng không mang role ADMIN -> 'forbidden'
 *    - Đã đăng nhập và mang role ADMIN -> 'admin' (Hợp lệ)
 */
export function evaluateAdminAccess({
  isDev,
  demoParam,
  user,
  isAuthenticated,
  token,
}: AdminAuthContext): AdminAuthAccessResult {
  // Demo Mode: Chỉ cho phép trên localhost/dev environment
  if (isDev && demoParam) {
    return { allowed: true, reason: 'demo' };
  }

  // Real Mode: Kiểm tra phiên đăng nhập đầy đủ
  if (!user || !isAuthenticated || !token) {
    return { allowed: false, reason: 'unauthenticated' };
  }

  // Real Mode: Bắt buộc role ADMIN
  if (user.role === 'ADMIN') {
    return { allowed: true, reason: 'admin' };
  }

  return { allowed: false, reason: 'forbidden', role: user.role || 'UNKNOWN' };
}
