/**
 * DANASEA Golden Flow Diagnostic Logger
 * Cung cấp log màu có cấu trúc trực tiếp trên DevTools F12 Console
 * Giúp người kiểm thử và vận hành bắt ngay nguyên nhân lỗi trên môi trường deploy.
 */

export interface FlowDiagnosticContext {
  step: '01_CATALOG' | '02_DETAIL' | '03_HOLD' | '04_ORDER' | '05_PAYMENT' | '06_CHECKIN';
  stepName: string;
  operation: string;
  payload?: unknown;
  hint?: string;
}

interface BackendErrorData {
  errorCode?: string;
  message?: string;
  status?: number;
  [key: string]: unknown;
}

const STEP_COLORS: Record<string, { bg: string; text: string }> = {
  '01_CATALOG': { bg: '#0284c7', text: '#ffffff' }, // Sky
  '02_DETAIL':  { bg: '#0369a1', text: '#ffffff' }, // Ocean
  '03_HOLD':    { bg: '#d97706', text: '#ffffff' }, // Amber
  '04_ORDER':   { bg: '#7c3aed', text: '#ffffff' }, // Purple
  '05_PAYMENT': { bg: '#059669', text: '#ffffff' }, // Emerald
  '06_CHECKIN': { bg: '#475569', text: '#ffffff' }, // Slate
};

function getActionableHint(status?: number, errorCode?: string): string {
  if (status === 401 || errorCode === 'INVALID_CREDENTIALS') {
    return 'Chưa đăng nhập hoặc phiên làm việc đã hết hạn. Hãy đăng nhập tài khoản khách hàng.';
  }
  if (status === 403 || errorCode === 'ACCESS_DENIED' || errorCode === 'UNAUTHORIZED_BOOKING_ACCESS') {
    return 'Tài khoản không có quyền thao tác trên đơn hàng hoặc dịch vụ này.';
  }
  if (status === 409 || errorCode === 'INSUFFICIENT_INVENTORY') {
    return 'Khung giờ này không đủ số lượng chỗ trống. Hãy giảm số lượng hoặc chọn khung giờ khác.';
  }
  if (status === 410 || errorCode === 'BOOKING_HOLD_EXPIRED') {
    return 'Đã quá thời gian giữ chỗ 15 phút. Quay lại giỏ hàng / trang chi tiết để giữ chỗ mới.';
  }
  if (errorCode === 'SLOT_NOT_AVAILABLE') {
    return 'Khung giờ không khả dụng hoặc đã bị nhà cung cấp tạm đóng.';
  }
  if (errorCode === 'BOOKING_NOT_FOUND') {
    return 'Mã giữ chỗ (bookingId) không tồn tại hoặc đã bị hủy trước đó.';
  }
  if (status === 500 || errorCode === 'INTERNAL_ERROR') {
    return 'Lỗi máy chủ Backend 500. Kiểm tra log của container backend / Spring Boot.';
  }
  if (!status) {
    return 'Mất kết nối mạng hoặc Backend không phản hồi (Network Error / CORS / DNS).';
  }
  return 'Kiểm tra kỹ payload gửi lên so với đặc tả OpenAPI của Backend.';
}

export const flowLogger = {
  /**
   * Log khi bắt đầu một thao tác trong luồng
   */
  start(ctx: FlowDiagnosticContext) {
    const { bg, text } = STEP_COLORS[ctx.step] || { bg: '#334155', text: '#ffffff' };
    console.groupCollapsed(
      `%c[DANASEA ${ctx.step}] ▶ ${ctx.stepName} — ${ctx.operation}`,
      `background: ${bg}; color: ${text}; font-weight: bold; padding: 2px 6px; border-radius: 4px;`
    );
    console.log('%cThời gian:', 'font-weight: bold;', new Date().toLocaleTimeString('vi-VN'));
    if (ctx.payload !== undefined) {
      console.log('%cPayload gửi đi:', 'color: #0284c7; font-weight: bold;', ctx.payload);
    }
    console.groupEnd();
  },

  /**
   * Log khi thao tác thành công
   */
  success(ctx: FlowDiagnosticContext, result?: unknown) {
    const { bg, text } = STEP_COLORS[ctx.step] || { bg: '#334155', text: '#ffffff' };
    console.groupCollapsed(
      `%c[DANASEA ${ctx.step}] ✅ ${ctx.stepName} THÀNH CÔNG`,
      `background: ${bg}; color: ${text}; font-weight: bold; padding: 2px 6px; border-radius: 4px;`
    );
    console.log('%cThao tác:', 'font-weight: bold;', ctx.operation);
    if (result !== undefined) {
      console.log('%cKết quả phản hồi:', 'color: #16a34a; font-weight: bold;', result);
    }
    console.groupEnd();
  },

  /**
   * Log khi có lỗi phát sinh - In khối thông tin cảnh báo trực diện
   */
  error(ctx: FlowDiagnosticContext, error: unknown) {
    const err = error as { response?: { status?: number; data?: BackendErrorData }; message?: string };
    const status = err?.response?.status;
    const responseData = err?.response?.data;
    const errorCode = responseData?.errorCode;
    const errorMsg = responseData?.message || err?.message || 'Lỗi không xác định';
    const hint = ctx.hint || getActionableHint(status, errorCode);

    console.group(
      `%c[DANASEA ${ctx.step} LỖI] ❌ ${ctx.stepName} — ${ctx.operation}`,
      'background: #dc2626; color: #ffffff; font-weight: bold; padding: 3px 8px; border-radius: 4px;'
    );
    console.error(`🚨 Mã lỗi HTTP: ${status || 'MẠNG KHÔNG PHẢN HỒI'}`);
    if (errorCode) {
      console.error(`🏷️ Mã nghiệp vụ Backend: ${errorCode}`);
    }
    console.error(`💬 Thông điệp lỗi: ${errorMsg}`);
    
    if (ctx.payload !== undefined) {
      console.log('%c📦 Payload đã gửi:', 'color: #0284c7; font-weight: bold;', ctx.payload);
    }
    if (responseData) {
      console.log('%c📄 Chi tiết phản hồi từ BE:', 'color: #e11d48; font-weight: bold;', responseData);
    }

    console.warn(
      `%c💡 HƯỚNG DẪN XỬ LÝ NHANH:%c ${hint}`,
      'background: #fef3c7; color: #92400e; font-weight: bold; padding: 2px 6px; border-radius: 3px;',
      'color: #92400e; font-weight: normal; margin-left: 4px;'
    );
    console.groupEnd();
  },
};
