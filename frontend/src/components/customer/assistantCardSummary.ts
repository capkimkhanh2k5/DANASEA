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
      const proposal = object(facts.proposal);
      const pendingProposals = list(facts.pendingProposals);
      const activeProposal = Object.keys(proposal).length ? proposal
        : (pendingProposals.length ? object(pendingProposals[0]) : {});
      if (Object.keys(activeProposal).length) {
        const plan = object(activeProposal.plan);
        const items = list(plan.items);
        const cost = numeric(plan.totalPrice);
        const removed = list(facts.removedSlotIds).length || list(activeProposal.removedSlotIds).length;
        const added = list(facts.addedSlotIds).length || list(activeProposal.addedSlotIds).length;
        if (string(activeProposal.state)) add(copy('Trạng thái đề xuất', 'Proposal state'), string(activeProposal.state));
        if (numeric(activeProposal.baseVersion) !== undefined) {
          add(copy('Phiên bản gốc', 'Base version'), 'v' + String(activeProposal.baseVersion));
        }
        if (removed || added) {
          add(copy('Thay đổi ca', 'Slot diff'), `-${removed} / +${added}`);
        }
        for (const item of items.slice(0, 5)) {
          const details = object(item);
          add(string(details.serviceTitle) ?? string(details.serviceName) ?? copy('Hoạt động', 'Activity'),
            [time(details.startTime), time(details.endTime)].filter(Boolean).join(' – '));
        }
        if (cost !== undefined) add(copy('Tổng dự kiến mới', 'Updated estimate'), vnd(cost));
        return {
          title: copy('Đề xuất điều chỉnh lịch trình', 'Itinerary Adjustment Proposal'),
          rows,
          note: copy('Đề xuất chưa thay đổi lịch gốc hay đơn đã thanh toán trước khi bạn chấp nhận.', 'Proposal does not alter your saved itinerary or paid bookings until accepted.'),
        };
      }
      if (list(facts.items).length && !list(facts.alternatives).length) {
        if (numeric(facts.version) !== undefined) add(copy('Phiên bản', 'Version'), 'v' + String(facts.version));
        if (string(facts.lifecycle)) add(copy('Trạng thái', 'Lifecycle'), string(facts.lifecycle));
        for (const value of list(facts.items).slice(0, 6)) {
          const item = object(value);
          add(string(item.serviceName) ?? string(item.serviceTitle) ?? copy('Hoạt động', 'Activity'),
            [string(item.start)?.slice(11, 16), string(item.end)?.slice(11, 16)].filter(Boolean).join(' – ') || string(item.weatherStatus));
        }
        const planTotal = numeric(object(facts.plan).totalPrice);
        if (planTotal !== undefined) add(copy('Tổng dự kiến', 'Estimated total'), vnd(planTotal));
        return {
          title: copy('Chi tiết lịch trình đã lưu', 'Saved Itinerary Detail'),
          rows,
          note: copy('Lịch trình đã lưu không tự giữ chỗ tồn kho.', 'Saved itineraries do not reserve inventory.'),
        };
      }
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
    case 'ITINERARY_LIST': {
      const itineraries = list(facts.itineraries);
      itineraries.slice(0, 5).forEach((entry, idx) => {
        const item = object(entry);
        const index = numeric(item.index) ?? idx + 1;
        const ver = numeric(item.version);
        const total = numeric(item.totalPrice);
        const range = [date(item.from, language), date(item.to, language)].filter(Boolean).join(' – ');
        add(
          copy(`Lịch trình #${index}`, `Itinerary #${index}`),
          [string(item.lifecycle), ver !== undefined ? `v${ver}` : undefined, range, total !== undefined ? vnd(total) : undefined]
            .filter(Boolean)
            .join(' · ')
        );
      });
      return {
        title: copy('Danh sách lịch trình đã lưu', 'Saved Itineraries'),
        rows,
        note: rows.length
          ? copy('Chọn theo số thứ tự (ví dụ: lịch trình số 1) để xem chi tiết hoặc điều chỉnh.', 'Refer by number (e.g. itinerary #1) to inspect or adjust.')
          : copy('Bạn chưa có lịch trình đã lưu nào.', 'You have no saved itineraries yet.'),
      };
    }
    case 'ITINERARY_HISTORY': {
      const revisions = list(facts.revisions);
      revisions.slice(-4).forEach((revObj) => {
        const rev = object(revObj);
        const ver = numeric(rev.version);
        add(
          ver !== undefined ? `v${ver}` : copy('Phiên bản', 'Revision'),
          [string(rev.lifecycle), string(rev.reason)].filter(Boolean).join(' · ')
        );
      });
      const restored = object(facts.restoredProposal);
      if (Object.keys(restored).length) {
        add(copy('Đề xuất khôi phục', 'Restore proposal'), `${string(restored.state) ?? 'PENDING'} (v${String(restored.baseVersion ?? 0)})`);
      }
      return rows.length ? {
        title: copy('Lịch sử phiên bản lịch trình', 'Itinerary Version History'),
        rows,
        note: copy('Khôi phục phiên bản tạo một đề xuất mới cần kiểm tra lại giá, ca và thời tiết trước khi chấp nhận.', 'Restoring a revision creates a proposal revalidated against live price, slot, and weather before acceptance.'),
      } : null;
    }
    case 'PAYMENT_PROVIDERS': {
      const enabled = list(facts.enabledProviders).filter((v): v is string => typeof v === 'string');
      add(copy('Đang hỗ trợ', 'Supported & active'), enabled.length ? enabled.join(', ') : copy('Chưa có', 'None'));
      return {
        title: copy('Phương thức thanh toán', 'Payment Providers'),
        rows,
      };
    }
    case 'BOOKING_CONFIRMATION': {
      if (card.status === 'out_of_stock') {
        return {
          title: copy('Thông tin đặt chỗ', 'Booking details'),
          rows: [{ label: copy('Trạng thái ca', 'Slot status'), value: copy('Hết chỗ', 'Out of stock') }],
          note: copy('Ca đã chọn vừa hết chỗ. Hệ thống không giữ chỗ hay overbook.', 'The selected slot just sold out. No hold or overbooking occurred.'),
        };
      }
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
        note: copy('Kiểm tra giá, phân nhóm và bấm Xác nhận bên dưới khi sẵn sàng (chưa thu tiền tự động).', 'Review pricing and grouping and click Confirm below when ready (no automatic charge).') };
    }
    default:
      return null;
  }
}
