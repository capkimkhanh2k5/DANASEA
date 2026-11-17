import type { AssistantCard } from '../../services/aiService';

type Row = { label: string; value: string; href?: string };

type CardSummary = {
  title: string;
  rows: Row[];
  note?: string;
};

const object = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};

const list = (value: unknown): unknown[] => Array.isArray(value) ? value : [];
const string = (value: unknown) => typeof value === 'string' ? value : undefined;
const numeric = (value: unknown) => typeof value === 'number' && Number.isFinite(value) ? value : undefined;

const date = (value: unknown, language: 'vi' | 'en') => {
  const parts = typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
    ? value.split('-').map(Number)
    : list(value);
  if (parts.length !== 3 || !parts.every((part) => typeof part === 'number' && Number.isInteger(part))) return undefined;
  const [year, month, day] = parts.map((part) => String(part).padStart(2, '0'));
  return language === 'vi' ? day + '/' + month + '/' + year : year + '-' + month + '-' + day;
};

const time = (value: unknown) => {
  const parts = list(value).slice(0, 2);
  if (parts.length !== 2 || !parts.every((part) => typeof part === 'number' && Number.isInteger(part))) {
    return string(value)?.slice(0, 5);
  }
  return parts.map((part) => String(part).padStart(2, '0')).join(':');
};

export function summarizeAssistantCard(card: AssistantCard, language: 'vi' | 'en'): CardSummary | null {
  if (card.status === 'UNAVAILABLE') return null;

  const facts = object(card.facts);
  const copy = (vi: string, en: string) => language === 'vi' ? vi : en;
  const vnd = (amount: number) => new Intl.NumberFormat(language === 'vi' ? 'vi-VN' : 'en', {
    style: 'currency',
    currency: 'VND',
  }).format(amount);
  const rows: Row[] = [];
  const add = (label: string, value: string | undefined) => {
    if (value) rows.push({ label, value });
  };

  switch (card.type) {
    case 'POLICY': {
      const policy = object(facts.policy);
      for (const value of list(policy.customerCancellation)) {
        const tier = object(value);
        const percentage = numeric(tier.percentage);
        if (percentage === undefined) continue;
        if (tier.condition === 'MORE_THAN_24_HOURS') {
          add(copy('Khách hủy khi còn hơn 24 giờ', 'Guest cancels more than 24 hours ahead'), percentage + '%');
        } else if (tier.condition === 'AT_MOST_24_HOURS') {
          add(copy('Khách hủy khi còn 24 giờ hoặc ít hơn', 'Guest cancels 24 hours or less ahead'), percentage + '%');
        }
      }
      const verifiedCancellation = numeric(policy.weatherOrVendorFaultPercentage);
      if (verifiedCancellation !== undefined) {
        add(copy('Thời tiết hoặc lỗi nhà cung cấp đã xác minh', 'Verified weather or provider cancellation'), verifiedCancellation + '%');
      }
      const days = numeric(facts.disputeWindowDays);
      if (days !== undefined) add(copy('Thời hạn khiếu nại', 'Dispute deadline'), days + copy(' ngày từ ngày trải nghiệm', ' days from the experience date'));
      if (!rows.length) add(copy('Thông tin', 'Details'), string(facts.text) ?? string(facts.policy));
      return rows.length ? {
        title: copy('Chính sách chung', 'Platform policy'),
        rows,
        note: list(policy.customerCancellation).length ? copy('Mức thực tế áp dụng theo từng đơn.', 'Check your booking for actual eligibility.') : undefined,
      } : null;
    }
    case 'CUSTOMER_SUPPORT': {
      const amount = numeric(object(facts.cancellationPreview).refundAmount);
      if (amount === undefined) return null;
      add(copy('Dự kiến hoàn', 'Estimated refund'), vnd(amount));
      return { title: copy('Xem trước hủy', 'Cancellation preview'), rows,
        note: copy('Chưa thực hiện giao dịch hoàn tiền.', 'No refund transaction has been performed.') };
    }
    case 'WEATHER': {
      const assessment = object(facts.assessment);
      const status = string(assessment.status) ?? string(facts.status) ?? card.status;
      const location = string(facts.location);
      add(copy('Khu vực', 'Area'), location);
      add(copy('Ngày', 'Date'), date(facts.date, language));
      if (numeric(facts.peakWindSpeed) !== undefined) add(copy('Gió mạnh nhất', 'Peak wind speed'), facts.peakWindSpeed + ' km/h');
      if (numeric(facts.peakWaveHeight) !== undefined) add(copy('Sóng cao nhất', 'Peak wave height'), facts.peakWaveHeight + ' m');
      const statusLabels: Record<string, string> = {
        UNKNOWN: copy('Chưa xác minh', 'Unverified'),
        STALE: copy('Dự báo hết hạn', 'Forecast expired'),
        PROVISIONAL: copy('Cần kiểm tra lại', 'Recheck required'),
        UNSAFE: copy('Điều kiện không phù hợp', 'Unsuitable conditions'),
        ACCEPTABLE: copy('Trong ngưỡng tại lúc kiểm tra', 'Within limits when checked'),
      };
      add(copy('Đánh giá ca', 'Departure assessment'), statusLabels[status]);
      return {
        title: copy('Thời tiết biển', 'Marine weather'),
        rows,
        note: Object.keys(assessment).length
          ? copy('Điều kiện có thể thay đổi trước khi khởi hành.', 'Conditions may change before departure.')
          : copy('Dự báo khu vực chỉ để tham khảo. Cần xác minh lại theo ca khởi hành.', 'Area forecasts are advisory. Verify the specific departure.'),
      };
    }
    case 'SERVICE_DETAIL': {
      const name = string(facts.name);
      if (!name) return null;
      const id = string(facts.serviceId);
      rows.push({
        label: name,
        value: '',
        href: id && /^[a-f0-9-]{36}$/i.test(id) ? '/tour/' + id : undefined,
      });
      const prices = list(facts.options).map(object)
        .filter((option) => list(option.slots).length > 0)
        .map((option) => numeric(option.partyTotal))
        .filter((value): value is number => value !== undefined);
      if (prices.length && facts.priceBasis === 'ACTIVE_OPTION_PARTY_TOTAL') {
        add(copy('Giá thấp nhất cả nhóm', 'Lowest group price'), vnd(Math.min(...prices)));
      }
      return { title: copy('Chi tiết dịch vụ', 'Service details'), rows };
    }
    case 'SERVICE_LIST':
    case 'COMPARISON': {
      const entries = list(facts.candidates ?? facts.results ?? facts.matrix ?? facts.services).slice(0, 5);
      for (const value of entries) {
        const entry = object(value);
        const service = object(entry.service ?? value);
        const name = string(service.name) ?? string(service.title) ?? string(entry.serviceName);
        if (!name) continue;
        const price = numeric(entry.minimumPartyTotal ?? entry.partyTotal ?? entry.price);
        const minutes = numeric(service.durationMinutes);
        const rating = numeric(service.averageRating ?? entry.rating ?? entry.avgRating);
        const id = string(service.id ?? entry.serviceId);
        rows.push({
          label: name,
          value: [
            price === undefined ? undefined : vnd(price) + copy(' cả nhóm', ' for the group'),
            minutes === undefined ? undefined : minutes + copy(' phút', ' min'),
            rating ? rating + '/5' : undefined,
          ].filter(Boolean).join(' · '),
          href: id && /^[a-f0-9-]{36}$/i.test(id) ? '/tour/' + id : undefined,
        });
      }
      return rows.length ? { title: copy('Dịch vụ', 'Services'), rows } : null;
    }
    case 'MARINE_BRIEFING_CARD': {
      for (const value of list(facts.activities)) {
        const activity = object(value);
        add(string(activity.serviceTitle) ?? copy('Hoạt động', 'Activity'),
          [date(activity.departureDate, language), time(activity.departureTime), string(activity.marineConditionsSummary)]
            .filter(Boolean).join(' · '));
        for (const checklist of list(activity.packingChecklist).slice(0, 5)) {
          const item = object(checklist);
          add(string(item.itemName) ?? copy('Chuẩn bị', 'Preparation'), string(item.tip));
        }
      }
      return rows.length ? { title: copy('Chuẩn bị đi biển', 'Marine briefing'), rows } : null;
    }
    case 'ITINERARY': {
      for (const value of list(facts.alternatives).slice(0, 3)) {
        const plan = object(object(value).plan);
        const cost = numeric(plan.totalPrice);
        const items = list(plan.items);
        if (!items.length) continue;
        for (const item of items) {
          const details = object(item);
          add(string(details.serviceTitle) ?? string(details.serviceName) ?? copy('Hoạt động', 'Activity'),
            [time(details.startTime), time(details.endTime)].filter(Boolean).join(' – '));
        }
        if (cost !== undefined) add(copy('Tổng dự kiến', 'Estimated total'), vnd(cost));
      }
      return {
        title: copy('Lịch trình', 'Itinerary'),
        rows,
        note: rows.length ? copy('Phương án này chưa giữ chỗ.', 'This itinerary does not reserve inventory.')
          : copy('Chưa có lịch phù hợp với các tiêu chí hiện tại.', 'No itinerary matched the current criteria.'),
      };
    }
    case 'BOOKING_CONFIRMATION': {
      if (card.status !== 'CONFIRMATION_PENDING') return null;
      const amount = numeric(facts.partyTotal);
      add(copy('Khởi hành', 'Departure'), string(facts.date)?.replace('T', ' '));
      if (amount !== undefined) add(copy('Tổng cả nhóm', 'Group total'), vnd(amount));
      const serviceId = string(facts.serviceId);
      if (serviceId && /^[a-f0-9-]{36}$/i.test(serviceId)) {
        rows.push({ label: copy('Mở dịch vụ', 'Open service'), value: '', href: '/tour/' + serviceId });
      }
      const grouping = list(facts.participantsPerPackage).filter((value) => numeric(value) !== undefined);
      if (grouping.length) add(copy('Số khách từng gói', 'Guests per package'), grouping.join(' + '));
      return { title: copy('Thông tin đặt chỗ', 'Booking details'), rows,
        note: copy('Mở trang dịch vụ để kiểm tra giá và ca còn chỗ. Bạn chưa thanh toán.', 'Open the service page to check the price and departure. You have not paid.') };
    }
    default:
      return null;
  }
}
