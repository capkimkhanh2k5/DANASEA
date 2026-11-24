import React, { useState, useEffect, useMemo, useRef } from 'react';
import {
  X,
  Search,
  Check,
  ShoppingBag,
  Compass,
  Star,
  Scale,
  RotateCcw,
  Calendar,
  Anchor,
  CreditCard,
  Copy,
  CheckCheck,
  ChevronDown,
  ChevronUp,
  ChevronLeft,
  ChevronRight,
  Paperclip,
} from 'lucide-react';
import type { VendorContextType, AttachedContext } from '../../../store/useVendorCopilotStore';
import { vendorService } from '../../../services/vendorService';
import { vendorOrderService } from '../../../services/vendorOrderService';
import { reviewService } from '../../../services/reviewService';
import { disputeService } from '../../../services/disputeService';
import { vendorEquipmentService } from '../../../services/vendorEquipmentService';
import { vendorPayoutService } from '../../../services/vendorPayoutService';
import { DEMO_EQUIPMENT_DATA } from '../../../data/demoEquipmentMap';
import { formatDisplayCode } from '../../../utils/displayCode';

export interface ContextItem {
  id: string;
  type: VendorContextType;
  title: string;
  subtitle: string;
  badge?: string;
  badgeVariant?: 'neutral' | 'success' | 'warning' | 'info';
  detailSpecs?: Array<{ label: string; value: string }>;
}

interface CategoryOption {
  type: VendorContextType;
  label: string;
  icon: React.ReactNode;
}

const CATEGORY_OPTIONS: CategoryOption[] = [
  { type: 'ORDER', label: 'Đơn hàng', icon: <ShoppingBag className="w-4 h-4" /> },
  { type: 'SERVICE', label: 'Dịch vụ / Tour', icon: <Compass className="w-4 h-4" /> },
  { type: 'REVIEW', label: 'Đánh giá', icon: <Star className="w-4 h-4" /> },
  { type: 'DISPUTE', label: 'Khiếu nại', icon: <Scale className="w-4 h-4" /> },
  { type: 'SLOT', label: 'Ca khởi hành', icon: <Calendar className="w-4 h-4" /> },
  { type: 'EQUIPMENT', label: 'Thiết bị / Cano', icon: <Anchor className="w-4 h-4" /> },
  { type: 'REFUND', label: 'Hoàn tiền', icon: <RotateCcw className="w-4 h-4" /> },
  { type: 'PAYOUT', label: 'Quyết toán ví', icon: <CreditCard className="w-4 h-4" /> },
];

// Fallback authoritative demo items directly grounded in DB_Demo/
const FALLBACK_DEMO_ITEMS: Record<VendorContextType, ContextItem[]> = {
  ORDER: [
    {
      id: '00020000-0000-0000-0000-000000000001',
      type: 'ORDER',
      title: 'Đơn #00020000 – Nguyễn Hoàng Long',
      subtitle: 'Mô tô nước – Danabeach • 1,170,000 đ',
      badge: 'Hoàn thành',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Dịch vụ', value: 'Mô tô nước – Danabeach' },
        { label: 'Khách hàng', value: 'Nguyễn Hoàng Long (0912345678)' },
        { label: 'Số lượng', value: '2 vé' },
        { label: 'Thanh toán', value: '1,170,000 đ (VNPAY)' },
      ],
    },
    {
      id: '00020000-0000-0000-0000-000000000002',
      type: 'ORDER',
      title: 'Đơn #00020002 – Trần Thu Hà',
      subtitle: 'Dù bay trên biển – Danabeach • 800,000 đ',
      badge: 'Hoàn thành',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Dịch vụ', value: 'Dù bay trên biển – Danabeach' },
        { label: 'Khách hàng', value: 'Trần Thu Hà (0905111222)' },
        { label: 'Số lượng', value: '1 vé' },
        { label: 'Thanh toán', value: '800,000 đ (MoMo)' },
      ],
    },
  ],
  SERVICE: [
    {
      id: '088d874c-a61e-5b58-a7a9-497f32a56901',
      type: 'SERVICE',
      title: 'Mô tô nước – Danabeach',
      subtitle: 'Thể thao biển • 500,000 đ • 30 phút',
      badge: 'Đang mở bán',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Phân loại', value: 'Thể thao cảm giác mạnh' },
        { label: 'Thời lượng', value: '30 phút / ca' },
        { label: 'Sức chứa', value: '2 người / lượt' },
        { label: 'Đánh giá', value: '5.0 sao (14 đánh giá)' },
      ],
    },
    {
      id: '3758c297-54ff-5887-89bd-62220227570b',
      type: 'SERVICE',
      title: 'Dù bay trên biển – Danabeach',
      subtitle: 'Hoạt động bay biển • 800,000 đ • 45 phút',
      badge: 'Đang mở bán',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Phân loại', value: 'Thể thao trên không' },
        { label: 'Thời lượng', value: '45 phút / ca' },
        { label: 'Sức chứa', value: '1 - 2 người / lượt' },
        { label: 'Đánh giá', value: '4.9 sao (8 đánh giá)' },
      ],
    },
  ],
  REVIEW: [
    {
      id: '66666666-6666-6666-6666-000000000001',
      type: 'REVIEW',
      title: 'Đánh giá 5 sao – Nguyễn Hoàng Long',
      subtitle: 'Nước biển Đà Nẵng mùa này trong vắt, lặn ngắm cá bơi rất thích...',
      badge: 'Đã phản hồi',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Điểm số', value: '5/5 sao' },
        { label: 'Dịch vụ', value: 'Mô tô nước – Danabeach' },
        { label: 'Thời gian', value: '2026-10-25' },
      ],
    },
    {
      id: '66666666-6666-6666-6666-000000000002',
      type: 'REVIEW',
      title: 'Đánh giá 5 sao – Trần Thu Hà',
      subtitle: 'Dịch vụ cực kỳ chuyên nghiệp, hướng dẫn viên 1 kèm 1 an toàn...',
      badge: 'Chưa phản hồi',
      badgeVariant: 'warning',
      detailSpecs: [
        { label: 'Điểm số', value: '5/5 sao' },
        { label: 'Dịch vụ', value: 'Dù bay trên biển – Danabeach' },
        { label: 'Thời gian', value: '2026-10-26' },
      ],
    },
  ],
  DISPUTE: [
    {
      id: '77777777-7777-7777-7777-777777777701',
      type: 'DISPUTE',
      title: 'Khiếu nại #77777777 – Không đúng mô tả',
      subtitle: 'Đơn #00020015 • Yêu cầu hoàn 100% tiền vé',
      badge: 'Đã hoàn tiền',
      badgeVariant: 'neutral',
      detailSpecs: [
        { label: 'Lý do', value: 'SERVICE_NOT_AS_DESCRIBED' },
        { label: 'Trạng thái', value: 'RESOLVED_REFUND' },
        { label: 'Số tiền tranh chấp', value: '500,000 đ' },
      ],
    },
    {
      id: '77777777-7777-7777-7777-777777777702',
      type: 'DISPUTE',
      title: 'Khiếu nại #77777778 – Hoãn giờ do thời tiết',
      subtitle: 'Đơn #00020022 • Yêu cầu dời lịch',
      badge: 'Đã hòa giải',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Lý do', value: 'WEATHER_CANCELLATION' },
        { label: 'Trạng thái', value: 'RESOLVED_PARTIAL' },
        { label: 'Phương án', value: 'Dời sang ca sáng hôm sau' },
      ],
    },
  ],
  SLOT: [
    {
      id: '5e068a90-a8d3-5194-b2f6-d37dca987121',
      type: 'SLOT',
      title: 'Ca 08:30 – 09:30 • Mô tô nước',
      subtitle: 'Đã đặt 2/4 chỗ • Trạng thái: Mở',
      badge: 'OPEN',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Giờ vận hành', value: '08:30 - 09:30' },
        { label: 'Dịch vụ', value: 'Mô tô nước – Danabeach' },
        { label: 'Sức chứa', value: '4 chỗ (Đã đặt: 2)' },
      ],
    },
    {
      id: '3ab9e274-fba3-5c18-b9ab-43a52d48c7a0',
      type: 'SLOT',
      title: 'Ca 09:00 – 10:00 • Dù bay biển',
      subtitle: 'Đã đặt 1/2 chỗ • Trạng thái: Mở',
      badge: 'OPEN',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Giờ vận hành', value: '09:00 - 10:00' },
        { label: 'Dịch vụ', value: 'Dù bay trên biển – Danabeach' },
        { label: 'Sức chứa', value: '2 chỗ (Đã đặt: 1)' },
      ],
    },
  ],
  EQUIPMENT: [
    {
      id: 'e1010000-0000-0000-0000-000000000004',
      type: 'EQUIPMENT',
      title: 'Mô tô nước Yamaha WaveRunner #01',
      subtitle: 'Nhóm: JET_SKI • Sức chứa 2 người',
      badge: 'Hoạt động tốt',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Mã thiết bị', value: 'EQ-JET-01' },
        { label: 'Tương thích', value: 'Mô tô nước – Danabeach' },
        { label: 'Lần bảo dưỡng gần nhất', value: '2026-11-05' },
      ],
    },
    {
      id: 'e1010000-0000-0000-0000-000000000005',
      type: 'EQUIPMENT',
      title: 'Cano kéo phao chuối 150HP #01',
      subtitle: 'Nhóm: SPEEDBOAT • Sức chứa 8 người',
      badge: 'Hoạt động tốt',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Mã thiết bị', value: 'EQ-BOAT-150' },
        { label: 'Tương thích', value: 'Dù bay & Phao chuối' },
        { label: 'Hạn kiểm định', value: '2027-02-15' },
      ],
    },
  ],
  REFUND: [
    {
      id: '00050000-0000-0000-0000-000000000001',
      type: 'REFUND',
      title: 'Hoàn tiền đơn #00020041 – 140,000 đ',
      subtitle: 'Lý do: Khách hủy đúng hạn 24h • MoMo',
      badge: 'Đã hoàn tất',
      badgeVariant: 'neutral',
      detailSpecs: [
        { label: 'Mã hoàn tiền', value: 'REF-00050000' },
        { label: 'Tỷ lệ hoàn', value: '100% (140,000 đ)' },
        { label: 'Kênh chi trả', value: 'MoMo E-Wallet' },
      ],
    },
  ],
  PAYOUT: [
    {
      id: '00090000-0000-0000-0000-000000000001',
      type: 'PAYOUT',
      title: 'Rút tiền 1,170,000 đ – VNPAY Bến bãi',
      subtitle: 'Kỳ đối soát tuần 42 • Trạng thái: Đã chuyển',
      badge: 'Đã thanh toán',
      badgeVariant: 'success',
      detailSpecs: [
        { label: 'Mã yêu cầu', value: 'PAY-00090000' },
        { label: 'Số tiền thực nhận', value: '1,170,000 đ' },
        { label: 'Tài khoản nhận', value: 'Vietcombank ****9882' },
      ],
    },
    {
      id: '00090000-0000-0000-0000-000000000010',
      type: 'PAYOUT',
      title: 'Rút tiền 585,000 đ – Doanh thu ngày',
      subtitle: 'Kỳ đối soát ngày 20/11 • Trạng thái: Chờ xử lý',
      badge: 'Chờ duyệt',
      badgeVariant: 'warning',
      detailSpecs: [
        { label: 'Mã yêu cầu', value: 'PAY-00090010' },
        { label: 'Số tiền', value: '585,000 đ' },
        { label: 'Ngày yêu cầu', value: '2026-11-20' },
      ],
    },
  ],
};

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSelectContext: (context: AttachedContext) => void;
  initialType?: VendorContextType;
}

export const VendorContextPinModal: React.FC<Props> = ({
  isOpen,
  onClose,
  onSelectContext,
  initialType = 'SERVICE',
}) => {
  const [selectedType, setSelectedType] = useState<VendorContextType>(initialType);
  const [searchTerm, setSearchTerm] = useState('');
  const [items, setItems] = useState<ContextItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [selectedItem, setSelectedItem] = useState<ContextItem | null>(null);
  const [showManualInput, setShowManualInput] = useState(false);
  const [manualIdInput, setManualIdInput] = useState('');
  const [copiedId, setCopiedId] = useState(false);
  const categoryScrollRef = useRef<HTMLDivElement>(null);

  const scrollCategory = (direction: 'left' | 'right') => {
    if (categoryScrollRef.current) {
      const scrollAmount = direction === 'left' ? -180 : 180;
      categoryScrollRef.current.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    }
  };

  // Sync initial type when opened
  useEffect(() => {
    if (isOpen) {
      setSelectedType(initialType);
      setSearchTerm('');
      setManualIdInput('');
      setShowManualInput(false);
    }
  }, [isOpen, initialType]);

  // Load live data with defensive fallback to authoritative DB_Demo
  useEffect(() => {
    if (!isOpen) return;

    let isMounted = true;
    setIsLoading(true);

    const loadData = async () => {
      try {
        let loaded: ContextItem[] = [];

        switch (selectedType) {
          case 'SERVICE': {
            const res = await vendorService.getMyServices().catch(() => []);
            if (res && res.length > 0) {
              loaded = res.map((s) => ({
                id: s.id,
                type: 'SERVICE',
                title: s.name,
                subtitle: `${(s.price || 0).toLocaleString()} đ • ${s.durationMinutes || 30} phút`,
                badge: s.status === 'PUBLISHED' ? 'Đang mở bán' : s.status,
                badgeVariant: s.status === 'PUBLISHED' ? 'success' : 'neutral',
                detailSpecs: [
                  { label: 'Mã dịch vụ', value: `#${formatDisplayCode(s.id, 'SRV')}` },
                  { label: 'Giá vé', value: `${(s.price || 0).toLocaleString()} đ` },
                  { label: 'Thời lượng', value: `${s.durationMinutes || 30} phút` },
                  { label: 'Địa điểm', value: s.locationName || s.address || 'Đà Nẵng' },
                ],
              }));
            }
            break;
          }
          case 'ORDER': {
            const res = await vendorOrderService.getVendorOrders(0, 50).catch(() => null);
            if (res && res.content && res.content.length > 0) {
              loaded = res.content.map((o) => ({
                id: o.id,
                type: 'ORDER',
                title: `Đơn #${formatDisplayCode(o.id, 'SUB')}${o.customerName ? ` – ${o.customerName}` : ''}`,
                subtitle: `${o.serviceTitle || 'Dịch vụ biển'} • ${(o.finalAmount || o.subtotalAmount || 0).toLocaleString()} đ`,
                badge: o.status,
                badgeVariant: o.status === 'COMPLETED' ? 'success' : 'info',
                detailSpecs: [
                  { label: 'Mã đơn', value: `#${formatDisplayCode(o.id, 'SUB')}` },
                  { label: 'Khách hàng', value: `${o.customerName || 'Khách đặt'} (${o.customerPhone || 'N/A'})` },
                  { label: 'Số lượng', value: `${o.quantity} vé` },
                  { label: 'Doanh thu', value: `${(o.finalAmount || o.subtotalAmount || 0).toLocaleString()} đ` },
                ],
              }));
            }
            break;
          }
          case 'REVIEW': {
            const res = await reviewService.getVendorReviews(0, 50).catch(() => null);
            if (res && res.content && res.content.length > 0) {
              loaded = res.content.map((r) => ({
                id: r.id,
                type: 'REVIEW',
                title: `Đánh giá ${r.rating} sao${r.customerName ? ` – ${r.customerName}` : ''}`,
                subtitle: r.comment || 'Không có bình luận',
                badge: r.vendorReply ? 'Đã phản hồi' : 'Chưa phản hồi',
                badgeVariant: r.vendorReply ? 'success' : 'warning',
                detailSpecs: [
                  { label: 'Mã đánh giá', value: `#${formatDisplayCode(r.id, 'REV')}` },
                  { label: 'Điểm số', value: `${r.rating}/5 sao` },
                  { label: 'Dịch vụ', value: r.serviceName || 'Dịch vụ biển' },
                ],
              }));
            }
            break;
          }
          case 'DISPUTE': {
            const res = await disputeService.getVendorDisputes('ALL', 0, 50).catch(() => null);
            if (res && res.content && res.content.length > 0) {
              loaded = res.content.map((d) => ({
                id: d.id,
                type: 'DISPUTE',
                title: `Khiếu nại #${formatDisplayCode(d.id, 'DISP')}`,
                subtitle: `Lý do: ${d.reason}`,
                badge: d.status,
                badgeVariant: d.status.includes('RESOLVED') ? 'success' : 'warning',
                detailSpecs: [
                  { label: 'Mã hồ sơ', value: `#${formatDisplayCode(d.id, 'DISP')}` },
                  { label: 'Lý do', value: d.reason },
                  { label: 'Trạng thái', value: d.status },
                ],
              }));
            }
            break;
          }
          case 'EQUIPMENT': {
            const res = await vendorEquipmentService.getEquipment().catch(() => []);
            const valid = res && res.length > 0 ? res : DEMO_EQUIPMENT_DATA;
            loaded = valid.map((e) => ({
              id: e.id,
              type: 'EQUIPMENT',
              title: e.name,
              subtitle: `Nhóm: ${e.compatibilityGroup} • Sức chứa ${e.capacity} người`,
              badge: 'Hoạt động tốt',
              badgeVariant: 'success',
              detailSpecs: [
                { label: 'Mã thiết bị', value: `#${formatDisplayCode(e.id, 'EQP')}` },
                { label: 'Nhóm tàu bè', value: e.compatibilityGroup },
                { label: 'Sức tải', value: `${e.capacity} người` },
              ],
            }));
            break;
          }
          case 'PAYOUT': {
            const res = await vendorPayoutService.getPayoutRequests('ALL', 0, 50).catch(() => null);
            if (res && res.content && res.content.length > 0) {
              loaded = res.content.map((p) => ({
                id: p.id,
                type: 'PAYOUT',
                title: `Rút tiền ${(p.amount || 0).toLocaleString()} đ`,
                subtitle: `Ngày: ${new Date(p.createdAt).toLocaleDateString('vi-VN')} • Mã: #${formatDisplayCode(p.id, 'PAY')}`,
                badge: p.status,
                badgeVariant: p.status === 'PAID' ? 'success' : 'warning',
                detailSpecs: [
                  { label: 'Mã rút tiền', value: `#${formatDisplayCode(p.id, 'PAY')}` },
                  { label: 'Số tiền', value: `${(p.amount || 0).toLocaleString()} đ` },
                  { label: 'Trạng thái', value: p.status },
                ],
              }));
            }
            break;
          }
          default:
            break;
        }

        // If API returned nothing or offline, ground strictly in DB_Demo authoritative fallbacks
        if (loaded.length === 0) {
          loaded = FALLBACK_DEMO_ITEMS[selectedType] || [];
        }

        if (isMounted) {
          setItems(loaded);
          setSelectedItem(loaded[0] || null);
          setIsLoading(false);
        }
      } catch (err) {
        console.error('[VendorContextPinModal] Fetch failed:', err);
        if (isMounted) {
          const fallback = FALLBACK_DEMO_ITEMS[selectedType] || [];
          setItems(fallback);
          setSelectedItem(fallback[0] || null);
          setIsLoading(false);
        }
      }
    };

    loadData();

    return () => {
      isMounted = false;
    };
  }, [isOpen, selectedType]);

  // Filtered items based on search input
  const filteredItems = useMemo(() => {
    if (!searchTerm.trim()) return items;
    const q = searchTerm.toLowerCase().trim();
    return items.filter(
      (item) =>
        item.title.toLowerCase().includes(q) ||
        item.subtitle.toLowerCase().includes(q) ||
        item.id.toLowerCase().includes(q)
    );
  }, [items, searchTerm]);

  if (!isOpen) return null;

  const currentCategory = CATEGORY_OPTIONS.find((c) => c.type === selectedType) || CATEGORY_OPTIONS[0];

  const handleConfirmPin = (itemToPin?: ContextItem) => {
    const target = itemToPin || selectedItem;
    if (!target) return;

    onSelectContext({
      type: target.type,
      id: target.id,
      label: target.title,
      meta: {
        categoryLabel: currentCategory.label,
        title: target.title,
        subtitle: target.subtitle,
        badge: target.badge,
        detailSpecs: target.detailSpecs,
      },
    });
    onClose();
  };

  const handleManualPin = (e: React.FormEvent) => {
    e.preventDefault();
    if (!manualIdInput.trim()) return;
    const cleanId = manualIdInput.trim();
    onSelectContext({
      type: selectedType,
      id: cleanId,
      label: `#${formatDisplayCode(cleanId, selectedType === 'ORDER' ? 'ORD' : selectedType === 'DISPUTE' ? 'DISP' : 'CTX')}`,
      meta: {
        categoryLabel: currentCategory.label,
        title: `#${formatDisplayCode(cleanId, selectedType === 'ORDER' ? 'ORD' : selectedType === 'DISPUTE' ? 'DISP' : 'CTX')}`,
      },
    });
    onClose();
  };

  const handleCopyId = (id: string) => {
    navigator.clipboard.writeText(id);
    setCopiedId(true);
    setTimeout(() => setCopiedId(false), 1500);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-slate-900/40 backdrop-blur-xs">
      <div className="bg-white rounded-2xl shadow-2xl border border-slate-200/90 w-full max-w-2xl overflow-hidden flex flex-col max-h-[88vh] animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-100">
          <div className="flex items-center gap-2">
            <Paperclip className="w-4 h-4 text-sky-600 shrink-0" />
            <h3 className="font-bold text-slate-900 text-sm">Ghim ngữ cảnh đối tượng</h3>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-700 p-1.5 rounded-lg hover:bg-slate-100 transition-colors cursor-pointer"
            aria-label="Đóng"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Category Selector Tabs (Single Row with Scroll & Navigation Arrows) */}
        <div className="px-3 py-2 bg-slate-50/70 border-b border-slate-100 flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => scrollCategory('left')}
            className="w-7 h-7 rounded-lg border border-slate-200/80 bg-white hover:bg-slate-100 text-slate-500 hover:text-slate-800 flex items-center justify-center shrink-0 transition-colors cursor-pointer shadow-2xs"
            title="Cuộn sang trái"
            aria-label="Cuộn sang trái"
          >
            <ChevronLeft className="w-3.5 h-3.5" />
          </button>

          <div
            ref={categoryScrollRef}
            className="flex items-center gap-1.5 overflow-x-auto scroll-smooth flex-1 py-0.5"
            style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
          >
            {CATEGORY_OPTIONS.map((cat) => {
              const isSelected = selectedType === cat.type;
              return (
                <button
                  key={cat.type}
                  type="button"
                  onClick={() => {
                    setSelectedType(cat.type);
                    setSearchTerm('');
                  }}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium transition-all whitespace-nowrap shrink-0 border cursor-pointer ${
                    isSelected
                      ? 'bg-white border-sky-500 text-sky-800 font-semibold shadow-xs'
                      : 'border-transparent text-slate-600 hover:bg-white/80 hover:border-slate-200/80 hover:text-slate-900'
                  }`}
                >
                  <span className={isSelected ? 'text-sky-600' : 'text-slate-400'}>{cat.icon}</span>
                  <span>{cat.label}</span>
                </button>
              );
            })}
          </div>

          <button
            type="button"
            onClick={() => scrollCategory('right')}
            className="w-7 h-7 rounded-lg border border-slate-200/80 bg-white hover:bg-slate-100 text-slate-500 hover:text-slate-800 flex items-center justify-center shrink-0 transition-colors cursor-pointer shadow-2xs"
            title="Cuộn sang phải"
            aria-label="Cuộn sang phải"
          >
            <ChevronRight className="w-3.5 h-3.5" />
          </button>
        </div>

        {/* Search Input Bar */}
        <div className="px-5 pt-3 pb-2">
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder={`Tìm ${currentCategory.label.toLowerCase()} theo tên, mã hoặc từ khóa...`}
              className="w-full pl-9 pr-3 py-2 rounded-xl border border-slate-200 bg-slate-50/50 text-xs text-slate-900 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-1 focus:ring-sky-500/20 focus:border-sky-500 transition-all"
            />
          </div>
        </div>

        {/* Main Body: List + Quick Preview */}
        <div className="flex-1 overflow-y-auto px-5 py-2 space-y-2 min-h-[220px]">
          {isLoading ? (
            <div className="space-y-2 py-2">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-16 rounded-xl bg-slate-100 animate-pulse border border-slate-200/50" />
              ))}
            </div>
          ) : filteredItems.length === 0 ? (
            <div className="py-12 text-center text-slate-400 text-xs">
              Không tìm thấy {currentCategory.label.toLowerCase()} nào phù hợp.
            </div>
          ) : (
            <div className="space-y-2">
              {filteredItems.map((item) => {
                const isSelected = selectedItem?.id === item.id;
                return (
                  <div
                    key={item.id}
                    onClick={() => setSelectedItem(item)}
                    onDoubleClick={() => handleConfirmPin(item)}
                    className={`p-3 rounded-xl border transition-all cursor-pointer flex items-center justify-between gap-3 ${
                      isSelected
                        ? 'border-sky-500 bg-sky-50/60 ring-1 ring-sky-500/20 shadow-xs'
                        : 'border-slate-200/80 bg-white hover:border-slate-300 hover:bg-slate-50/50'
                    }`}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 mb-0.5">
                        <h4 className="font-semibold text-slate-900 text-xs truncate">{item.title}</h4>
                        {item.badge && (
                          <span
                            className={`px-2 py-0.5 rounded-full text-[10px] font-medium shrink-0 ${
                              item.badgeVariant === 'success'
                                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/80'
                                : item.badgeVariant === 'warning'
                                ? 'bg-amber-50 text-amber-700 border border-amber-200/80'
                                : 'bg-slate-100 text-slate-600 border border-slate-200'
                            }`}
                          >
                            {item.badge}
                          </span>
                        )}
                      </div>
                      <p className="text-[11px] text-slate-500 truncate">{item.subtitle}</p>
                    </div>

                    <div className="shrink-0">
                      {isSelected ? (
                        <div className="w-6 h-6 rounded-lg bg-sky-600 text-white flex items-center justify-center shadow-xs">
                          <Check className="w-3.5 h-3.5" />
                        </div>
                      ) : (
                        <div className="w-6 h-6 rounded-lg border border-slate-200 hover:border-slate-300 flex items-center justify-center text-slate-300">
                          <Check className="w-3.5 h-3.5 opacity-0" />
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Selected Item Detail Card & Action */}
        {selectedItem && (
          <div className="px-5 py-3 bg-slate-50/80 border-t border-slate-100 flex flex-col gap-2.5">
            <div className="flex items-center justify-between">
              <div className="min-w-0">
                <span className="text-[10px] uppercase font-bold tracking-wider text-sky-700">
                  Đã chọn: {currentCategory.label}
                </span>
                <h5 className="font-semibold text-slate-900 text-xs truncate">{selectedItem.title}</h5>
              </div>
              <div className="flex items-center gap-1.5 shrink-0">
                <button
                  type="button"
                  onClick={() => handleCopyId(selectedItem.id)}
                  className="px-2 py-1 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 text-[11px] flex items-center gap-1 cursor-pointer transition-colors"
                  title="Sao chép mã UUID"
                >
                  {copiedId ? <CheckCheck className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3" />}
                  <span className="font-mono text-[10px]">#{formatDisplayCode(selectedItem.id, selectedType === 'ORDER' ? 'ORD' : 'CTX')}</span>
                </button>
              </div>
            </div>

            {selectedItem.detailSpecs && selectedItem.detailSpecs.length > 0 && (
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 py-1 border-t border-slate-200/50">
                {selectedItem.detailSpecs.map((spec, i) => (
                  <div key={i} className="min-w-0">
                    <span className="text-[10px] text-slate-400 block">{spec.label}</span>
                    <span className="text-[11px] font-medium text-slate-700 truncate block">{spec.value}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Manual ID Input Accordion (Fallback for Devs/Edge Cases) */}
        <div className="px-5 py-1.5 bg-slate-50/40 border-t border-slate-100">
          <button
            type="button"
            onClick={() => setShowManualInput(!showManualInput)}
            className="text-[11px] text-slate-400 hover:text-slate-600 flex items-center gap-1 transition-colors cursor-pointer"
          >
            {showManualInput ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
            <span>Hoặc nhập mã UUID thủ công</span>
          </button>

          {showManualInput && (
            <form onSubmit={handleManualPin} className="mt-2 mb-1 flex items-center gap-2">
              <input
                type="text"
                value={manualIdInput}
                onChange={(e) => setManualIdInput(e.target.value)}
                placeholder="Dán mã UUID (ví dụ: 088d874c-a61e-...)"
                className="flex-1 p-2 rounded-xl border border-slate-200 bg-white text-xs font-mono text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-1 focus:ring-sky-500/20 focus:border-sky-500"
              />
              <button
                type="submit"
                disabled={!manualIdInput.trim()}
                className="px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-900 text-white text-xs font-medium disabled:opacity-40 transition-colors cursor-pointer"
              >
                Ghim mã
              </button>
            </form>
          )}
        </div>

        {/* Modal Actions Footer */}
        <div className="flex items-center justify-end gap-2 px-5 py-3 border-t border-slate-100 bg-white">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-50 text-xs font-medium transition-colors cursor-pointer"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={() => handleConfirmPin()}
            disabled={!selectedItem}
            className="px-4 py-2 rounded-xl bg-sky-600 hover:bg-sky-700 text-white text-xs font-semibold flex items-center gap-1.5 shadow-xs transition-colors cursor-pointer disabled:opacity-40"
          >
            <Paperclip className="w-3.5 h-3.5" />
            <span>Ghim ngữ cảnh</span>
          </button>
        </div>
      </div>
    </div>
  );
};
