export function localizeNotificationTitle(title?: string | null, language: string = 'vi'): string {
  if (!title) return '';
  if (language !== 'en') return title;

  const trimmed = title.trim();

  // Exact mappings
  const exactMap: Record<string, string> = {
    'Nhắc lịch chuyến đi sắp tới': 'Upcoming trip reminder',
    'Cẩm nang ra khơi 24h đã sẵn sàng': '24h Marine Briefing Ready',
    'Thanh toán thành công': 'Payment successful',
    'Đơn đặt dịch vụ mới': 'New service booking',
    'Hoàn tiền thành công': 'Refund completed',
    'Đề xuất đổi ca từ nhà cung cấp': 'Reschedule proposal from operator',
    'Đã đổi ca đặt chỗ': 'Booking rescheduled',
    'Chọn đổi ca hoặc hoàn toàn bộ tiền': 'Choose a new departure or full refund',
    'Đã yêu cầu hoàn toàn bộ tiền': 'Full refund requested',
    'Đã hủy dịch vụ': 'Service cancelled',
    'Bạn có voucher bồi thường': 'Your compensation voucher is available',
    'Hệ thống tự động can thiệp vì an toàn': 'Automatic safety intervention',
    'Lịch trình của bạn cần được xem lại': 'Your itinerary needs review',
    'Đã tiếp nhận yêu cầu hỗ trợ': 'Support request received',
    'Có yêu cầu hỗ trợ khách hàng đang chờ': 'Customer support request waiting',
    'Yêu cầu hỗ trợ đã được cập nhật': 'Support request updated',
  };

  if (exactMap[trimmed]) {
    return exactMap[trimmed];
  }

  // Parameterized title mappings
  if (trimmed.startsWith('Chuyến đi tự động hủy vì an toàn hàng hải:')) {
    return trimmed.replace('Chuyến đi tự động hủy vì an toàn hàng hải:', 'Trip cancelled for maritime safety:');
  }
  if (trimmed.startsWith('Đã hủy chuyến; đã yêu cầu hoàn 100%:')) {
    return trimmed.replace('Đã hủy chuyến; đã yêu cầu hoàn 100%:', 'Trip cancelled; 100% refund requested:');
  }
  if (trimmed.startsWith('Cảnh báo thời tiết cho chuyến đi sắp tới:')) {
    return trimmed.replace('Cảnh báo thời tiết cho chuyến đi sắp tới:', 'Weather alert for upcoming trip:');
  }
  if (trimmed.startsWith('Cập nhật thời tiết cho chuyến đi sắp tới:')) {
    return trimmed.replace('Cập nhật thời tiết cho chuyến đi sắp tới:', 'Weather update for upcoming trip:');
  }
  if (trimmed.startsWith('Cảnh báo thời tiết nguy hiểm cho dịch vụ:')) {
    return trimmed.replace('Cảnh báo thời tiết nguy hiểm cho dịch vụ:', 'Dangerous weather alert for service:');
  }

  return trimmed;
}

export function localizeNotificationBody(body?: string | null, language: string = 'vi'): string {
  if (!body) return '';
  if (language !== 'en') return body;

  const trimmed = body.trim();

  // Pattern 1: Chuyến đi của bạn khởi hành lúc 07:00 ngày 2026-11-08...
  const tripReminderMatch = trimmed.match(/^Chuyến đi của bạn khởi hành lúc (.+?) ngày (.+?)\. (?:Vui lòng chuẩn bị và đến (.+)|Vui lòng chuẩn bị và đến đúng giờ\.)/);
  if (tripReminderMatch) {
    const [, time, date] = tripReminderMatch;
    return `Your trip departs at ${time} on ${date}. Please prepare and arrive 15 minutes before departure.`;
  }

  // Pattern 2: Xem chi tiết thời tiết biển, checklist đồ cần mang...
  const briefingMatch = trimmed.match(/^Xem chi tiết thời tiết biển, checklist đồ cần mang và hướng dẫn an toàn cho chuyến đi lúc (.+?) ngày (.+?)\./);
  if (briefingMatch) {
    const [, time, date] = briefingMatch;
    return `View marine conditions, packing checklist, and safety instructions for your trip at ${time} on ${date}.`;
  }

  // Pattern 3: Đơn hàng của bạn đã được thanh toán thành công.
  if (trimmed.includes('Đơn hàng của bạn đã được thanh toán thành công')) {
    return 'Your order has been paid successfully.';
  }

  // Pattern 4: Hoàn tiền
  const refundMatch = trimmed.match(/^Yêu cầu hoàn tiền (.+?) VNĐ đã được xử lý thành công\./);
  if (refundMatch) {
    return `Your refund request of ${refundMatch[1]} VND has been processed successfully.`;
  }

  // Pattern 5: Đề xuất đổi ca
  const rescheduleProposalMatch = trimmed.match(/^Nhà cung cấp đề xuất ca ngày (.+?) lúc (.+?)\. Lý do: (.+?)\. Xác nhận trước (.+?)\./);
  if (rescheduleProposalMatch) {
    const [, date, time, reason, deadline] = rescheduleProposalMatch;
    return `Operator proposes ${date} at ${time}. Reason: ${reason}. Please confirm before ${deadline}.`;
  }

  // Pattern 6: Đã đổi ca đặt chỗ
  const rescheduleDoneMatch = trimmed.match(/^Đơn (.+?) được chuyển sang ngày (.+?) lúc (.+?)\. Mở chi tiết đơn để xem vé mới\./);
  if (rescheduleDoneMatch) {
    const [, orderId, date, time] = rescheduleDoneMatch;
    return `Order ${orderId} has been moved to ${date} at ${time}. Open order details to view your new ticket.`;
  }

  // Pattern 7: Chọn đổi ca hoặc hoàn tiền
  if (trimmed.includes('không thể thực hiện theo lịch đã đặt. Chọn đổi ca hoặc hoàn toàn bộ tiền trước')) {
    return trimmed
      .replace('Dịch vụ của đơn con', 'Service for sub-order')
      .replace('không thể thực hiện theo lịch đã đặt. Chọn đổi ca hoặc hoàn toàn bộ tiền trước', 'cannot be operated as scheduled. Choose to reschedule or full refund before')
      .replace('; hết hạn chưa lựa chọn, hệ thống tự yêu cầu hoàn tiền.', '; otherwise a full refund will be requested automatically.');
  }

  // Pattern 8: Hủy do sự cố
  if (trimmed.includes('đã hủy do sự cố được xác minh. Yêu cầu hoàn toàn bộ tiền đang chờ cổng thanh toán xác nhận.')) {
    return trimmed
      .replace('Đơn con', 'Sub-order')
      .replace('đã hủy do sự cố được xác minh. Yêu cầu hoàn toàn bộ tiền đang chờ cổng thanh toán xác nhận.', 'was cancelled due to verified operational disruption. Full refund is awaiting payment gateway confirmation.')
      .replace('Mã bù riêng, nếu áp dụng, nằm trong tài khoản của bạn.', 'Personal replacement vouchers, if applicable, are in your account.');
  }

  // Pattern 9: Hủy do thời tiết nguy hiểm
  if (trimmed.includes('đã bị hủy do thời tiết nguy hiểm. Yêu cầu hoàn tiền toàn bộ đang chờ cổng thanh toán xác nhận.')) {
    return trimmed
      .replace('Chuyến đi của bạn lúc', 'Your trip at')
      .replace('ngày', 'on')
      .replace('đã bị hủy do thời tiết nguy hiểm. Yêu cầu hoàn tiền toàn bộ đang chờ cổng thanh toán xác nhận.', 'was cancelled due to severe weather. A full refund has been requested and is awaiting payment gateway confirmation.');
  }

  return trimmed;
}
