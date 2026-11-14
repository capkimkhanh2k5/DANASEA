import { useState, useEffect, useCallback, useMemo } from 'react';
import { vendorSettlementService } from '../../../services/vendorSettlementService';
import { vendorProfileService, type VendorProfileDto } from '../../../services/vendorProfileService';
import { vendorOrderService } from '../../../services/vendorOrderService';
import { getAuthoritativeCustomer } from '../../../data/demoCustomerMap';
import type { VendorSubOrderDto } from '../../../types/vendorOrder';
import { 
  Printer, 
  Download, 
  Search, 
  RotateCcw, 
  X, 
  Wallet, 
  ArrowUpRight, 
  Clock, 
  CheckCircle2, 
  AlertCircle
} from 'lucide-react';
import { SettlementStatementDocument } from './SettlementStatementDocument';
import { 
  AtlasPageHeader, 
  AtlasMetricCard, 
  AtlasSegmentedTabs,
  AtlasBadge, 
  AtlasButton,
  AtlasPagination
} from '../../../components/vendor/atlas/AtlasDesignSystem';
import { toast } from '../../../store/useToastStore';

export type TimeRangeFilter = 'TODAY' | 'THIS_WEEK' | 'THIS_MONTH' | 'ALL_TIME';

export interface CashflowTransaction {
  id: string;
  code: string;
  dateStr: string;
  timestamp: number;
  serviceName: string;
  customerName: string;
  quantity: number;
  grossAmount: number;
  commissionRate: number;
  commissionAmount: number;
  netAmount: number;
  status: 'AVAILABLE' | 'ESCROW' | 'REFUNDED';
  orderStatus: string;
}

export interface SettlementItem {
  id: string;
  subOrderCode: string;
  serviceName: string;
  slotTime: string;
  customerName: string;
  quantity: number;
  unitPrice: number;
  gross: number;
  rate: string;
  commission: number;
  net: number;
}

export interface SettlementPeriod {
  id: string;
  code: string;
  dateRange: string;
  grossAmount: number;
  commissionRate: number;
  commissionAmount: number;
  netAmount: number;
  payoutDate: string;
  status: 'PAID' | 'PENDING';
  itemsCount: number;
}

export function Revenue() {
  const [timeRange, setTimeRange] = useState<TimeRangeFilter>('THIS_MONTH');
  const [activeTab, setActiveTab] = useState<'ALL' | 'AVAILABLE' | 'ESCROW' | 'REFUNDED' | 'SETTLEMENTS'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(8);
  const [isLoading, setIsLoading] = useState(false);

  // Raw DB data
  const [orders, setOrders] = useState<VendorSubOrderDto[]>([]);
  const [settlementPeriods, setSettlementPeriods] = useState<SettlementPeriod[]>([]);
  const [vendorProfile, setVendorProfile] = useState<VendorProfileDto | null>(null);
  const [showPrintModal, setShowPrintModal] = useState(false);

  useEffect(() => {
    vendorProfileService.getProfile().then((p) => {
      setVendorProfile(p);
    }).catch(() => {});
  }, []);

  // Fetch live orders & settlements from DB
  const loadData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [ordersRes, settleRes] = await Promise.allSettled([
        vendorOrderService.getVendorOrders(0, 100),
        vendorSettlementService.getSettlements({ size: 20 })
      ]);

      if (ordersRes.status === 'fulfilled' && ordersRes.value?.content) {
        setOrders(ordersRes.value.content);
      } else {
        setOrders([]);
      }

      if (settleRes.status === 'fulfilled' && settleRes.value?.content) {
        const mapped: SettlementPeriod[] = settleRes.value.content.map((s) => ({
          id: s.id,
          code: `#SET-${s.periodStart.replace(/-/g, '').substring(0, 6)}-${s.id.substring(0, 4)}`,
          dateRange: `${s.periodStart} – ${s.periodEnd}`,
          grossAmount: Number(s.grossAmount) || 0,
          commissionRate: 0.1,
          commissionAmount: Number(s.commissionAmount) || 0,
          netAmount: Number(s.netPayableAmount) || 0,
          payoutDate: (s.status === 'PAID' || s.status === 'FINALIZED') ? 'Đã thanh toán ngân hàng' : 'Đang xử lý đối soát',
          status: (s.status === 'PAID' || s.status === 'FINALIZED') ? 'PAID' : 'PENDING',
          itemsCount: 0,
        }));
        setSettlementPeriods(mapped);
      } else {
        setSettlementPeriods([]);
      }
    } catch {
      // Keep empty lists
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Map sub_orders to live cashflow transactions
  const transactions: CashflowTransaction[] = useMemo(() => {
    return orders.map((o) => {
      const gross = Number(o.finalAmount ?? o.subtotalAmount ?? (o.unitPrice * (o.quantity || 1))) || 0;
      const commission = Math.round(gross * 0.1);
      const net = gross - commission;
      const createdDate = new Date();

      let cashStatus: CashflowTransaction['status'] = 'ESCROW';
      if (o.status === 'CHECKED_IN' || o.status === 'COMPLETED') {
        cashStatus = 'AVAILABLE';
      } else if (o.status === 'CANCELLED' || o.status === 'REFUNDED') {
        cashStatus = 'REFUNDED';
      }

      return {
        id: o.id,
        code: `#VE-${o.id.substring(0, 8).toUpperCase()}`,
        dateStr: o.checkedInAt ? o.checkedInAt.slice(0, 10) : 'Hôm nay',
        timestamp: createdDate.getTime(),
        serviceName: 'Dịch vụ trải nghiệm biển',
        customerName: (o.customerName && o.customerName !== 'Khách đặt chỗ') ? o.customerName : getAuthoritativeCustomer(o.id).name,
        quantity: o.quantity || 1,
        grossAmount: gross,
        commissionRate: 0.1,
        commissionAmount: commission,
        netAmount: net,
        status: cashStatus,
        orderStatus: o.status,
      };
    });
  }, [orders]);

  // Filter transactions by time range
  const timeFilteredTransactions = useMemo(() => {
    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const dayOfWeek = now.getDay() || 7;
    const startOfWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate() - dayOfWeek + 1).getTime();
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();

    return transactions.filter((t) => {
      if (timeRange === 'TODAY') return t.timestamp >= startOfToday;
      if (timeRange === 'THIS_WEEK') return t.timestamp >= startOfWeek;
      if (timeRange === 'THIS_MONTH') return t.timestamp >= startOfMonth;
      return true;
    });
  }, [transactions, timeRange]);

  // Aggregate financial metrics based on selected time range
  const metrics = useMemo(() => {
    const valid = timeFilteredTransactions.filter((t) => t.status !== 'REFUNDED');
    const gross = valid.reduce((acc, t) => acc + t.grossAmount, 0);
    const net = valid.reduce((acc, t) => acc + t.netAmount, 0);
    const commission = valid.reduce((acc, t) => acc + t.commissionAmount, 0);

    const available = timeFilteredTransactions
      .filter((t) => t.status === 'AVAILABLE')
      .reduce((acc, t) => acc + t.netAmount, 0);

    const inEscrow = timeFilteredTransactions
      .filter((t) => t.status === 'ESCROW')
      .reduce((acc, t) => acc + t.netAmount, 0);

    return {
      gross,
      net,
      commission,
      available,
      inEscrow,
    };
  }, [timeFilteredTransactions]);

  // Filtered table rows based on activeTab and searchQuery
  const filteredRows = useMemo(() => {
    if (activeTab === 'SETTLEMENTS') return [];

    return timeFilteredTransactions.filter((t) => {
      if (activeTab !== 'ALL' && t.status !== activeTab) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        return (
          t.code.toLowerCase().includes(q) ||
          t.serviceName.toLowerCase().includes(q) ||
          t.customerName.toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [timeFilteredTransactions, activeTab, searchQuery]);

  // Tab counts
  const countAvailable = timeFilteredTransactions.filter((t) => t.status === 'AVAILABLE').length;
  const countEscrow = timeFilteredTransactions.filter((t) => t.status === 'ESCROW').length;
  const countRefunded = timeFilteredTransactions.filter((t) => t.status === 'REFUNDED').length;

  const totalPages = Math.max(1, Math.ceil(filteredRows.length / pageSize));
  const paginatedRows = filteredRows.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const handleExportCSV = () => {
    if (filteredRows.length === 0) {
      toast.error('Không có dữ liệu giao dịch để xuất file CSV.');
      return;
    }

    const csvRows = [
      ['Mã Giao Dịch', 'Thời Gian', 'Dịch Vụ', 'Khách Hàng', 'Số Lượng', 'Doanh Thu Gộp', 'Khấu Trừ Sàn', 'Thực Nhận', 'Trạng Thái Dòng Tiền'],
      ...filteredRows.map((item) => [
        `"${item.code}"`,
        `"${item.dateStr}"`,
        `"${item.serviceName}"`,
        `"${item.customerName}"`,
        item.quantity.toString(),
        item.grossAmount.toString(),
        item.commissionAmount.toString(),
        item.netAmount.toString(),
        `"${item.status === 'AVAILABLE' ? 'Khả dụng' : item.status === 'ESCROW' ? 'Đang ký quỹ' : 'Đã hoàn tiền'}"`,
      ]),
    ];

    const csvString = '\uFEFF' + csvRows.map((e) => e.join(',')).join('\r\n');
    const blob = new Blob([csvString], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `DANASEA_DONG_TIEN_${timeRange}_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    toast.success(`Đã xuất ${filteredRows.length} dòng giao dịch thành công!`);
  };

  return (
    <>
      <div className="w-full max-w-[1400px] mx-auto px-6 py-5 space-y-5 print:hidden">
        {/* 1. Header Toolbar with Time Range Switcher */}
        <AtlasPageHeader
          title="Doanh thu & Dòng tiền đối tác"
          actions={
            <div className="flex flex-wrap items-center gap-2.5">
              {/* Time Range Selector */}
              <div className="flex items-center bg-slate-100 p-1 rounded-xl border border-slate-200/70 text-xs font-medium text-slate-600">
                {(
                  [
                    { id: 'TODAY', label: 'Hôm nay' },
                    { id: 'THIS_WEEK', label: 'Tuần này' },
                    { id: 'THIS_MONTH', label: 'Tháng này' },
                    { id: 'ALL_TIME', label: 'Tất cả' },
                  ] as const
                ).map((r) => (
                  <button
                    key={r.id}
                    onClick={() => {
                      setTimeRange(r.id);
                      setCurrentPage(1);
                    }}
                    className={`px-3 py-1.5 rounded-lg font-medium transition-all cursor-pointer ${
                      timeRange === r.id
                        ? 'bg-white text-slate-900 shadow-xs font-semibold'
                        : 'hover:text-slate-900'
                    }`}
                  >
                    {r.label}
                  </button>
                ))}
              </div>

              <AtlasButton
                variant="outline"
                size="md"
                onClick={() => setShowPrintModal(true)}
                icon={<Printer className="w-4 h-4 text-slate-500" />}
              >
                In biên bản
              </AtlasButton>

              <AtlasButton
                variant="primary"
                size="md"
                onClick={handleExportCSV}
                icon={<Download className="w-4 h-4" />}
              >
                Xuất CSV
              </AtlasButton>
            </div>
          }
        />

        {/* 2. Atlas 4-Card Cashflow & Revenue Strip */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <AtlasMetricCard
            label="Doanh thu thực nhận (Net)"
            value={`${metrics.net.toLocaleString('vi-VN')} đ`}
            accent="emerald"
            icon={<Wallet className="w-5 h-5 text-emerald-600" />}
          />

          <AtlasMetricCard
            label="Số dư khả dụng rút"
            value={`${metrics.available.toLocaleString('vi-VN')} đ`}
            accent="sky"
            icon={<CheckCircle2 className="w-5 h-5 text-sky-600" />}
          />

          <AtlasMetricCard
            label="Đang ký quỹ / Giữ chỗ"
            value={`${metrics.inEscrow.toLocaleString('vi-VN')} đ`}
            accent="amber"
            icon={<Clock className="w-5 h-5 text-amber-600" />}
          />

          <AtlasMetricCard
            label="Khấu trừ hoa hồng sàn"
            value={`-${metrics.commission.toLocaleString('vi-VN')} đ`}
            accent="rose"
            icon={<ArrowUpRight className="w-5 h-5 text-rose-600" />}
          />
        </div>

        {/* 3. Unified Card Container: Cashflow Ledger (Atlas Standard Table Layout) */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden flex flex-col">
          {/* Card Head: Segmented Tabs */}
          <div className="p-4 sm:p-5 border-b border-slate-100">
            <AtlasSegmentedTabs<typeof activeTab>
              activeTab={activeTab}
              onChange={(tab) => {
                setActiveTab(tab);
                setCurrentPage(1);
              }}
              tabs={[
                { id: 'ALL', label: 'Tất cả dòng tiền', count: timeFilteredTransactions.length },
                { id: 'AVAILABLE', label: 'Khả dụng (Đã soát vé)', count: countAvailable },
                { id: 'ESCROW', label: 'Đang ký quỹ (Chờ chạy)', count: countEscrow },
                { id: 'REFUNDED', label: 'Đã hoàn tiền', count: countRefunded },
                { id: 'SETTLEMENTS', label: 'Kỳ đối soát định kỳ', count: settlementPeriods.length },
              ]}
            />
          </div>

          {/* Conditional Rendering: Cashflow Ledger vs Period History Table */}
          {activeTab !== 'SETTLEMENTS' ? (
            <>
              {/* Card Toolbar */}
              <div className="p-3.5 border-b border-slate-100 bg-[#fbfcfd] flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3">
                <div className="relative flex-1 min-w-[260px]">
                  <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => {
                      setSearchQuery(e.target.value);
                      setCurrentPage(1);
                    }}
                    placeholder="Tìm theo mã đơn, tên tour, khách hàng..."
                    className="w-full h-10 pl-10 pr-9 py-2 rounded-xl bg-white border border-slate-200 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                  />
                  {searchQuery && (
                    <button
                      onClick={() => setSearchQuery('')}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  )}
                </div>

                <button
                  onClick={() => {
                    setSearchQuery('');
                    setActiveTab('ALL');
                    setCurrentPage(1);
                    loadData();
                    toast.success('Đã đồng bộ lại sổ cái dòng tiền!');
                  }}
                  className="h-10 w-10 flex items-center justify-center rounded-xl bg-white hover:bg-slate-50 border border-slate-200 text-slate-700 shadow-xs transition-colors cursor-pointer shrink-0"
                  title="Làm mới sổ cái"
                >
                  <RotateCcw className="w-4 h-4 text-slate-600" />
                </button>
              </div>

              {/* Table Body */}
              <div className="overflow-x-auto flex-1 scrollbar-thin">
                <table className="w-full text-left text-sm border-collapse table-auto">
                  <thead>
                    <tr className="bg-[#f8f9fa] text-slate-500 text-xs uppercase tracking-wider font-semibold border-b border-slate-200/80">
                      <th className="py-3.5 px-4 w-[130px]">MÃ GIAO DỊCH</th>
                      <th className="py-3.5 px-4 w-[160px]">THỜI GIAN</th>
                      <th className="py-3.5 px-4">DỊCH VỤ & KHÁCH HÀNG</th>
                      <th className="py-3.5 px-4 text-right w-[140px]">DOANH THU GỘP</th>
                      <th className="py-3.5 px-4 text-right w-[130px]">KHẤU TRỪ SÀN</th>
                      <th className="py-3.5 px-4 text-right w-[140px]">THỰC NHẬN</th>
                      <th className="py-3.5 px-4 text-center w-[150px]">TRẠNG THÁI</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 text-slate-700">
                    {isLoading ? (
                      Array.from({ length: 4 }).map((_, idx) => (
                        <tr key={`sk-${idx}`} className="animate-pulse">
                          <td className="py-4 px-4"><div className="h-4 w-20 bg-slate-200 rounded" /></td>
                          <td className="py-4 px-4"><div className="h-4 w-28 bg-slate-200 rounded" /></td>
                          <td className="py-4 px-4"><div className="h-4 w-40 bg-slate-200 rounded" /></td>
                          <td className="py-4 px-4 text-right"><div className="h-4 w-24 bg-slate-200 rounded ml-auto" /></td>
                          <td className="py-4 px-4 text-right"><div className="h-4 w-20 bg-slate-200 rounded ml-auto" /></td>
                          <td className="py-4 px-4 text-right"><div className="h-4 w-24 bg-slate-200 rounded ml-auto" /></td>
                          <td className="py-4 px-4 text-center"><div className="h-6 w-20 bg-slate-200 rounded-full mx-auto" /></td>
                        </tr>
                      ))
                    ) : paginatedRows.length > 0 ? (
                      paginatedRows.map((tx) => (
                        <tr key={tx.id} className="hover:bg-slate-50/70 transition-colors">
                          <td className="py-4 px-4 font-mono font-semibold text-slate-900 text-xs">
                            {tx.code}
                          </td>
                          <td className="py-4 px-4 text-slate-500 font-mono text-xs">
                            {tx.dateStr}
                          </td>
                          <td className="py-4 px-4">
                            <div className="flex flex-col">
                              <span className="font-semibold text-slate-900 text-sm">{tx.serviceName}</span>
                              <span className="text-xs text-slate-400 mt-0.5">
                                {tx.customerName} • {tx.quantity} vé
                              </span>
                            </div>
                          </td>
                          <td className="py-4 px-4 text-right font-mono font-medium text-slate-900 tabular-nums">
                            +{tx.grossAmount.toLocaleString('vi-VN')} đ
                          </td>
                          <td className="py-4 px-4 text-right font-mono text-rose-600 tabular-nums text-xs">
                            -{tx.commissionAmount.toLocaleString('vi-VN')} đ
                          </td>
                          <td className="py-4 px-4 text-right font-mono font-bold text-slate-900 tabular-nums">
                            +{tx.netAmount.toLocaleString('vi-VN')} đ
                          </td>
                          <td className="py-4 px-4 text-center">
                            {tx.status === 'AVAILABLE' && (
                              <AtlasBadge label="Khả dụng rút" variant="success" dot />
                            )}
                            {tx.status === 'ESCROW' && (
                              <AtlasBadge label="Đang ký quỹ" variant="warning" dot />
                            )}
                            {tx.status === 'REFUNDED' && (
                              <AtlasBadge label="Đã hoàn tiền" variant="neutral" dot />
                            )}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={7} className="py-12 text-center text-slate-400 text-sm">
                          Chưa phát sinh giao dịch dòng tiền trong khoảng thời gian này
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              <AtlasPagination
                currentPage={currentPage}
                totalPages={totalPages}
                totalItems={filteredRows.length}
                pageSize={pageSize}
                onPageSizeChange={setPageSize}
                onPageChange={setCurrentPage}
              />
            </>
          ) : (
            /* Period History Table (Kỳ đối soát định kỳ) */
            <div className="overflow-x-auto flex-1 scrollbar-thin">
              <table className="w-full text-left text-sm border-collapse table-auto">
                <thead>
                  <tr className="bg-[#f8f9fa] text-slate-500 text-xs uppercase tracking-wider font-semibold border-b border-slate-200/80">
                    <th className="py-3.5 px-4 w-[160px]">MÃ KỲ ĐỐI SOÁT</th>
                    <th className="py-3.5 px-4">KHOẢNG THỜI GIAN</th>
                    <th className="py-3.5 px-4 text-right w-[150px]">DOANH THU GỘP</th>
                    <th className="py-3.5 px-4 text-right w-[130px]">KHẤU TRỪ</th>
                    <th className="py-3.5 px-4 text-right w-[150px]">THỰC NHẬN</th>
                    <th className="py-3.5 px-4 text-center w-[150px]">TRẠNG THÁI</th>
                    <th className="py-3.5 px-4 text-center w-[120px]">THAO TÁC</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-slate-700">
                  {settlementPeriods.length > 0 ? (
                    settlementPeriods.map((period) => (
                      <tr key={period.id} className="hover:bg-slate-50/70 transition-colors">
                        <td className="py-4 px-4 font-mono font-bold text-slate-900 text-xs">
                          {period.code}
                        </td>
                        <td className="py-4 px-4 text-slate-600 font-mono text-xs">
                          {period.dateRange}
                        </td>
                        <td className="py-4 px-4 text-right font-mono font-medium text-slate-900 tabular-nums">
                          {period.grossAmount.toLocaleString('vi-VN')} đ
                        </td>
                        <td className="py-4 px-4 text-right font-mono text-rose-600 tabular-nums text-xs">
                          -{period.commissionAmount.toLocaleString('vi-VN')} đ
                        </td>
                        <td className="py-4 px-4 text-right font-mono font-bold text-slate-900 tabular-nums">
                          {period.netAmount.toLocaleString('vi-VN')} đ
                        </td>
                        <td className="py-4 px-4 text-center">
                          <AtlasBadge
                            label={period.status === 'PAID' ? 'Đã quyết toán' : 'Chờ đối soát'}
                            variant={period.status === 'PAID' ? 'success' : 'warning'}
                            dot
                          />
                        </td>
                        <td className="py-4 px-4 text-center">
                          <button
                            onClick={() => setShowPrintModal(true)}
                            className="px-2.5 py-1 text-xs font-semibold text-slate-700 hover:text-slate-900 hover:bg-slate-100 rounded-lg border border-slate-200 transition-colors cursor-pointer"
                          >
                            In biên bản
                          </button>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={7} className="py-12 text-center text-slate-400 text-sm">
                        <div className="flex flex-col items-center justify-center gap-2">
                          <AlertCircle className="w-6 h-6 text-slate-300" />
                          <span>Chưa có kỳ đối soát định kỳ nào được chốt trong hệ thống</span>
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {/* Print Statement Modal */}
      {showPrintModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-4xl w-full max-h-[90vh] overflow-y-auto p-6 shadow-2xl border border-slate-200 flex flex-col gap-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-200/80">
              <h3 className="text-base font-bold text-slate-900">
                Biên bản đối soát & Quyết toán tài chính
              </h3>
              <button
                onClick={() => setShowPrintModal(false)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <SettlementStatementDocument
              cycle={{
                code: settlementPeriods[0]?.code || '#SET-CURRENT',
                dateRange: settlementPeriods[0]?.dateRange || '2026-11-01 – 2026-11-30',
                grossAmount: metrics.gross,
                commissionAmount: metrics.commission,
                netAmount: metrics.net,
                status: 'PENDING',
                itemsCount: filteredRows.length,
              }}
              items={filteredRows.map((r) => ({
                id: r.id,
                subOrderCode: r.code,
                serviceName: r.serviceName,
                slotTime: r.dateStr,
                customerName: r.customerName,
                quantity: r.quantity,
                unitPrice: r.grossAmount,
                gross: r.grossAmount,
                rate: '10%',
                commission: r.commissionAmount,
                net: r.netAmount,
              }))}
              bankInfo={{
                bankName: vendorProfile?.bankName || 'Techcombank',
                accountNumber: vendorProfile?.bankAccountNumber || 'Chưa cập nhật',
              }}
              vendorProfile={vendorProfile}
              onClose={() => setShowPrintModal(false)}
            />

            <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
              <AtlasButton variant="outline" size="md" onClick={() => setShowPrintModal(false)}>
                Đóng
              </AtlasButton>
              <AtlasButton variant="primary" size="md" onClick={() => window.print()} icon={<Printer className="w-4 h-4" />}>
                In tài liệu ngay
              </AtlasButton>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
