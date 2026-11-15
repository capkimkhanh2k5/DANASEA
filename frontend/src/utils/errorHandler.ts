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
      const rawMsg = (
        ('message' in data && typeof data.message === 'string' && data.message) ||
        ('error' in data && typeof data.error === 'string' && data.error) ||
        ('details' in data && typeof data.details === 'string' && data.details) ||
        ''
      ).trim();

      if (rawMsg) {
        if (rawMsg.includes('departure already exists at this date and start time')) {
          return 'Ca khởi hành tại ngày và giờ này đã tồn tại trên hệ thống.';
        }
        if (rawMsg.includes('Cannot create a slot in the past')) {
          return 'Không thể tạo ca khởi hành trong quá khứ hoặc đã qua giờ bắt đầu.';
        }
        if (rawMsg.includes('Start date must be before or equal to end date')) {
          return 'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.';
        }
        if (rawMsg.includes('Batch date range cannot exceed 31 days')) {
          return 'Khoảng ngày tạo ca không được vượt quá 31 ngày.';
        }
        if (rawMsg.includes('No matching days of week found')) {
          return 'Không có ngày nào phù hợp với các thứ đã chọn trong khoảng ngày.';
        }
        if (rawMsg.includes('Duplicate start time in slot templates')) {
          return 'Trùng giờ bắt đầu trong danh sách các ca mẫu.';
        }
        if (rawMsg.includes('operating schedule exception blocks this slot')) {
          return 'Một ngoại lệ lịch hoạt động đang chặn ca khởi hành này.';
        }
        if (rawMsg === 'An unexpected error occurred.' || rawMsg.includes('unexpected error')) {
          return 'Hệ thống máy chủ đang bận hoặc gặp sự cố không mong muốn. Vui lòng thử lại sau.';
        }
        return rawMsg;
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
