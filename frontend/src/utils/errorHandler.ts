import axios from 'axios';

/**
 * Bóc tách thông báo lỗi thân thiện, súc tích từ Backend hoặc Axios Error.
 * Ngăn chặn tuyệt đối việc in các câu lỗi kỹ thuật như "Request failed with status code 400".
 */
export function extractErrorMessage(error: unknown, fallbackMessage = 'Đã có lỗi xảy ra. Vui lòng thử lại!'): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data;

    // 1. Nếu Backend trả về ErrorResponse dạng { code, message }
    if (data && typeof data === 'object') {
      if ('message' in data && typeof data.message === 'string' && data.message.trim()) {
        return data.message.trim();
      }
      if ('error' in data && typeof data.error === 'string' && data.error.trim()) {
        return data.error.trim();
      }
      if ('details' in data && typeof data.details === 'string' && data.details.trim()) {
        return data.details.trim();
      }
    }

    // 2. Map theo HTTP status code nếu không có message từ backend
    switch (error.response?.status) {
      case 400:
        return 'Yêu cầu không hợp lệ hoặc trạng thái dữ liệu không cho phép thao tác này.';
      case 401:
        return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
      case 403:
        return 'Bạn không có quyền thực hiện thao tác này.';
      case 404:
        return 'Dữ liệu không tồn tại trên hệ thống.';
      case 409:
        return 'Dữ liệu bị trùng lặp hoặc xảy ra xung đột trạng thái.';
      case 500:
        return 'Hệ thống máy chủ DANASEA đang bận. Vui lòng thử lại sau.';
      default:
        break;
    }
  }

  // 3. Nếu là JS Error thông thường (nhưng không phải mã lỗi kỹ thuật của Axios)
  if (error instanceof Error && error.message) {
    if (
      !error.message.includes('status code') &&
      !error.message.includes('AxiosError') &&
      !error.message.includes('Network Error')
    ) {
      return error.message;
    }
  }

  return fallbackMessage;
}
