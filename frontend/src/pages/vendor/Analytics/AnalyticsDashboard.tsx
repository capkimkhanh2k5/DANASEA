import { useState, useEffect, useMemo, useCallback } from "react";
import { vendorReportService, type VendorDashboardResponse } from "../../../services/vendorReportService";
import { vendorService } from "../../../services/vendorService";
import type { VendorServiceDto } from "../../../types/vendorService";
import { vendorSettlementService, type SettlementResponse } from "../../../services/vendorSettlementService";
import { vendorOrderService } from "../../../services/vendorOrderService";
import type { VendorBookingItemDto } from "../../../types/vendorOrder";
import { Download, BarChart3 } from "lucide-react";
import { 
  AtlasPageHeader, 
  AtlasMetricCard, 
  AtlasBadge, 
  AtlasButton 
} from "../../../components/vendor/atlas/AtlasDesignSystem";

export function AnalyticsDashboard() {
  const [timeRange, setTimeRange] = useState<"7d" | "30d" | "90d" | "12m">("30d");
  const [metrics, setMetrics] = useState<VendorDashboardResponse | null>(null);
  const [services, setServices] = useState<VendorServiceDto[]>([]);
  const [settlements, setSettlements] = useState<SettlementResponse[]>([]);
  const [bookings, setBookings] = useState<VendorBookingItemDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const getRangeDates = useCallback((range: "7d" | "30d" | "90d" | "12m") => {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const day = String(now.getDate()).padStart(2, "0");
    const todayStr = `${year}-${month}-${day}`;

    const daysMap: Record<string, number> = {
      "7d": 6,
      "30d": 29,
      "90d": 89,
      "12m": 364,
    };

    const past = new Date(now);
    past.setDate(past.getDate() - (daysMap[range] ?? 29));
    const pYear = past.getFullYear();
    const pMonth = String(past.getMonth() + 1).padStart(2, "0");
    const pDay = String(past.getDate()).padStart(2, "0");

    return { from: `${pYear}-${pMonth}-${pDay}`, to: todayStr, todayStr };
  }, []);

  useEffect(() => {
    let isMounted = true;
    setIsLoading(true);

    const { from, to } = getRangeDates(timeRange);

    Promise.allSettled([
      vendorReportService.getVendorDashboard(from, to),
      vendorService.getMyServices(),
      vendorSettlementService.getSettlements(),
      vendorOrderService.getVendorBookings({ page: 0, size: 100 }),
    ]).then(([dashRes, servRes, settleRes, bookRes]) => {
      if (!isMounted) return;

      if (dashRes.status === "fulfilled" && dashRes.value) {
        setMetrics(dashRes.value);
      } else {
        setMetrics({
          totalRevenue: 0,
          netPayout: 0,
          newOrders: 0,
          completedOrders: 0,
          completionRate: 100,
          cancellationRate: 0,
          alerts: []
        });
      }

      if (servRes.status === "fulfilled" && servRes.value) {
        setServices(servRes.value);
      }

      if (settleRes.status === "fulfilled" && settleRes.value?.content) {
        setSettlements(settleRes.value.content);
      }

      if (bookRes.status === "fulfilled" && bookRes.value?.content) {
        setBookings(bookRes.value.content);
      }

      setIsLoading(false);
    });

    return () => {
      isMounted = false;
    };
  }, [timeRange, getRangeDates]);

  // Filter bookings belonging to the active time range
  const filteredBookings = useMemo(() => {
    const { from, to } = getRangeDates(timeRange);
    return bookings.filter((b) => b.bookingDate >= from && b.bookingDate <= to);
  }, [bookings, timeRange, getRangeDates]);

  // Calculated Analytics derived from real data
  const totalRevenueCalc = useMemo(() => {
    if (metrics?.totalRevenue && metrics.totalRevenue > 0) return metrics.totalRevenue;
    return filteredBookings.reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0), 0);
  }, [metrics, filteredBookings]);

  const completedOrdersCalc = useMemo(() => {
    if (metrics?.completedOrders && metrics.completedOrders > 0) return metrics.completedOrders;
    return filteredBookings.length;
  }, [metrics, filteredBookings]);

  const completionRateCalc = metrics?.completionRate ?? 100;

  // Real trend data grounded in actual bookings for the selected time range
  const trendBars = useMemo(() => {
    const now = new Date();

    if (timeRange === "7d") {
      // 7 ngày gần nhất
      const days = [];
      for (let i = 6; i >= 0; i--) {
        const d = new Date(now);
        d.setDate(d.getDate() - i);
        const label = d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
        const iso = d.toISOString().slice(0, 10);
        const bOnDay = filteredBookings.filter((b) => b.bookingDate === iso);
        const rev = bOnDay.reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0), 0);
        days.push({
          label,
          revenue: Math.round(rev),
          bookings: bOnDay.reduce((sum, b) => sum + (b.quantity || 1), 0),
        });
      }
      return days;
    } else if (timeRange === "30d") {
      // 4 tuần trong 30 ngày
      const weeks = [];
      for (let w = 3; w >= 0; w--) {
        const startD = new Date(now);
        startD.setDate(startD.getDate() - (w * 7 + 6));
        const endD = new Date(now);
        endD.setDate(endD.getDate() - (w * 7));
        const startIso = startD.toISOString().slice(0, 10);
        const endIso = endD.toISOString().slice(0, 10);
        const bInWeek = filteredBookings.filter((b) => b.bookingDate >= startIso && b.bookingDate <= endIso);
        const rev = bInWeek.reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0), 0);
        weeks.push({
          label: `Tuần ${4 - w}`,
          revenue: Math.round(rev),
          bookings: bInWeek.reduce((sum, b) => sum + (b.quantity || 1), 0),
        });
      }
      return weeks;
    } else if (timeRange === "90d") {
      // 3 tháng gần nhất
      const months = [];
      for (let m = 2; m >= 0; m--) {
        const d = new Date(now.getFullYear(), now.getMonth() - m, 1);
        const mNum = d.getMonth() + 1;
        const mPrefix = `${d.getFullYear()}-${String(mNum).padStart(2, "0")}`;
        const bInMonth = filteredBookings.filter((b) => b.bookingDate.startsWith(mPrefix));
        const rev = bInMonth.reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0), 0);
        months.push({
          label: `Thg ${mNum}`,
          revenue: Math.round(rev),
          bookings: bInMonth.reduce((sum, b) => sum + (b.quantity || 1), 0),
        });
      }
      return months;
    } else {
      // 12 tháng gần nhất (rút gọn hiển thị 6 mốc 2 tháng hoặc 12 tháng)
      const months = [];
      for (let m = 11; m >= 0; m -= 2) {
        const d = new Date(now.getFullYear(), now.getMonth() - m, 1);
        const mNum = d.getMonth() + 1;
        const mPrefix = `${d.getFullYear()}-${String(mNum).padStart(2, "0")}`;
        const bInMonth = filteredBookings.filter((b) => b.bookingDate.startsWith(mPrefix));
        const rev = bInMonth.reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0), 0);
        months.push({
          label: `T${mNum}`,
          revenue: Math.round(rev),
          bookings: bInMonth.reduce((sum, b) => sum + (b.quantity || 1), 0),
        });
      }
      return months;
    }
  }, [timeRange, filteredBookings]);

  const maxWeeklyRev = useMemo(() => {
    return Math.max(...trendBars.map((w) => w.revenue), 1000000);
  }, [trendBars]);

  // Breakdown revenue per service from real bookings
  const serviceRevenueBreakdown = useMemo(() => {
    if (services.length === 0) return [];
    const totalRev = filteredBookings.reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0), 0) || totalRevenueCalc || 1;

    return services.map((s) => {
      const sBookings = filteredBookings.filter((b) => b.serviceId === s.id);
      const rev = sBookings.reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0), 0);
      const guestCount = sBookings.reduce((sum, b) => sum + (b.quantity || 1), 0);
      const pct = Math.min(100, Math.round((rev / totalRev) * 100));
      return {
        id: s.id,
        name: s.name,
        revenue: rev,
        guestCount,
        percentage: pct,
        capacityPerSlot: s.capacityPerSlot,
        status: s.status,
        location: s.locationName || s.address || "Đà Nẵng",
      };
    }).sort((a, b) => b.revenue - a.revenue);
  }, [services, filteredBookings, totalRevenueCalc]);

  return (
    <div className="flex-1 w-full max-w-[1400px] mx-auto px-6 py-5 space-y-5">
      {/* 1. Header Toolbar (Atlas Page Header) */}
      <AtlasPageHeader
        title="Báo cáo phân tích kinh doanh"
        actions={
          <div className="flex items-center gap-2.5">
            <div className="flex items-center bg-slate-100 p-1 rounded-xl border border-slate-200/70 text-sm font-medium text-slate-600">
              {(["7d", "30d", "90d", "12m"] as const).map((r) => {
                const labels = { "7d": "7 ngày", "30d": "30 ngày", "90d": "Quý này", "12m": "12 tháng" };
                return (
                  <button
                    key={r}
                    onClick={() => setTimeRange(r)}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer ${
                      timeRange === r ? "bg-white text-slate-900 shadow-xs font-semibold" : "hover:text-slate-900"
                    }`}
                  >
                    {labels[r]}
                  </button>
                );
              })}
            </div>

            <AtlasButton
              variant="outline"
              size="md"
              onClick={() => window.print()}
              icon={<Download className="w-4 h-4 text-slate-500" />}
            >
              Xuất dữ liệu
            </AtlasButton>
          </div>
        }
      />

      {/* 2. Structured Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <AtlasMetricCard
          label="Tổng doanh thu"
          value={isLoading ? "---" : `${totalRevenueCalc.toLocaleString("vi-VN")} đ`}
        />

        <AtlasMetricCard
          label="Lượt đặt vé hoàn thành"
          value={isLoading ? "---" : `${completedOrdersCalc} đơn`}
        />

        <AtlasMetricCard
          label="Tỷ lệ kín chỗ"
          value={metrics ? `${metrics.completionRate}%` : "0%"}
          accent="sky"
        />

        <AtlasMetricCard
          label="Tỷ lệ hoàn thành"
          value={`${completionRateCalc}%`}
          accent="emerald"
        />
      </div>

      {/* 3. Main Charts & Breakdown Section (Atlas 2-Column Grid) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-stretch">
        {/* Left Column (8 cols): Revenue Trend Chart */}
        <div className="lg:col-span-8 bg-white rounded-xl border border-slate-200/80 p-5 shadow-xs flex flex-col justify-between">
          <div>
            <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-3 border-b border-slate-100 mb-4 gap-2">
              <div>
                <h3 className="text-sm font-semibold text-slate-900">
                  Xu hướng doanh thu ({timeRange === "7d" ? "7 ngày qua" : timeRange === "30d" ? "30 ngày qua" : timeRange === "90d" ? "Quý này" : "12 tháng"})
                </h3>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                {services.length > 0 && (
                  services.slice(0, 4).map((s, idx) => {
                    const colors = ["#0284C7", "#0ea5e9", "#38bdf8", "#7dd3fc"];
                    return (
                      <span key={s.id} className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-600">
                        <span className="w-2 h-2 rounded-xs" style={{ backgroundColor: colors[idx % colors.length] }} />
                        <span className="truncate max-w-[140px]" title={s.name}>{s.name}</span>
                      </span>
                    );
                  })
                )}
              </div>
            </div>

            {/* Custom Bar Visualization */}
            {totalRevenueCalc === 0 ? (
              <div className="h-60 flex flex-col items-center justify-center text-slate-400 text-xs gap-2">
                <BarChart3 className="w-8 h-8 text-slate-300 stroke-[1.5]" />
                <span>Chưa có dữ liệu doanh thu phát sinh trong kỳ</span>
              </div>
            ) : (
              <div className="h-60 flex items-end gap-3 pt-6 pb-2 px-2">
                {trendBars.map((item, idx) => {
                  const heightPct = Math.max(8, Math.round((item.revenue / maxWeeklyRev) * 100));
                  return (
                    <div key={idx} className="flex-1 flex flex-col items-center gap-2 h-full justify-end group">
                      <div className="text-xs font-mono text-slate-500 opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap">
                        {(item.revenue / 1000000).toFixed(1)}M
                      </div>
                      <div
                        style={{ height: `${heightPct}%` }}
                        className="w-full bg-[#0284C7] rounded-t-sm transition-all cursor-pointer hover:bg-sky-600 shadow-2xs"
                        title={`${item.label}: ${item.revenue.toLocaleString("vi-VN")} đ (${item.bookings} vé)`}
                      />
                      <span className="text-xs font-mono font-medium text-slate-500">
                        {item.label}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <div className="pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500 mt-3">
            <span>Tổng cộng: <strong className="text-slate-900 font-semibold font-mono">{totalRevenueCalc.toLocaleString("vi-VN")} đ</strong> {settlements.length > 0 ? `(${settlements.length} kỳ đối soát)` : ""}</span>
          </div>
        </div>

        {/* Right Column (4 cols): Category Breakdown */}
        <div className="lg:col-span-4 bg-white rounded-xl border border-slate-200/80 p-5 shadow-xs flex flex-col justify-between">
          <div>
            <div className="pb-3 border-b border-slate-100 mb-4">
              <h3 className="text-sm font-semibold text-slate-900">Cơ cấu doanh thu theo dịch vụ</h3>
            </div>

            {/* Category Breakdown (Grounded in real vendor bookings & services) */}
            <div className="space-y-4">
              {serviceRevenueBreakdown.length > 0 ? (
                serviceRevenueBreakdown.map((s, index) => {
                  const colors = ["#0284C7", "#0ea5e9", "#38bdf8", "#7dd3fc"];
                  const color = colors[index % colors.length];
                  return (
                    <div key={s.id}>
                      <div className="flex justify-between text-xs font-medium text-slate-700 mb-1.5">
                        <span className="truncate max-w-[180px]" title={s.name}>{s.name}</span>
                        <span className="font-mono font-semibold text-slate-900">
                          {s.revenue > 0 ? `${s.revenue.toLocaleString("vi-VN")} đ` : "0 đ"}{" "}
                          <span className="text-slate-400 font-normal">({s.percentage}%)</span>
                        </span>
                      </div>
                      <div className="w-full bg-slate-100 rounded-full h-2 overflow-hidden">
                        <div
                          className="h-2 rounded-full transition-all"
                          style={{
                            width: `${s.percentage}%`,
                            backgroundColor: color,
                          }}
                        />
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="py-8 text-center text-slate-400 text-xs">
                  Chưa có dữ liệu danh mục dịch vụ
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 4. Top Performing Services Table (Atlas Table Layout) */}
      <div className="bg-white rounded-xl border border-slate-200/80 shadow-xs overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-slate-900">Bảng xếp hạng hiệu suất dịch vụ</h3>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-[#f8f9fa] border-b border-slate-200/80 text-xs uppercase tracking-wider text-slate-500 font-semibold">
              <tr>
                <th className="py-3.5 px-4">TÊN TOUR / DỊCH VỤ</th>
                <th className="py-3.5 px-4">ĐỊA ĐIỂM</th>
                <th className="py-3.5 px-4 text-center">LƯỢT KHÁCH</th>
                <th className="py-3.5 px-4 text-right">DOANH THU</th>
                <th className="py-3.5 px-4 text-center">CÔNG SUẤT</th>
                <th className="py-3.5 px-4 text-center">TRẠNG THÁI</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {serviceRevenueBreakdown.length > 0 ? (
                serviceRevenueBreakdown.map((s) => (
                  <tr key={s.id} className="hover:bg-slate-50/70 transition-colors">
                    <td className="py-3.5 px-4 font-medium text-slate-900 max-w-xs truncate">
                      {s.name}
                    </td>
                    <td className="py-3.5 px-4 text-slate-500">{s.location}</td>
                    <td className="py-3.5 px-4 text-center font-mono tabular-nums">
                      {s.guestCount > 0 ? `${s.guestCount} khách` : "0 khách"}
                    </td>
                    <td className="py-3.5 px-4 text-right font-mono font-semibold text-slate-900 tabular-nums">
                      {s.revenue > 0 ? `${s.revenue.toLocaleString("vi-VN")} đ` : "0 đ"}
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      <span className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-mono font-semibold bg-slate-100 text-slate-700">
                        {s.capacityPerSlot ? `${s.capacityPerSlot} khách/ca` : "---"}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      <AtlasBadge 
                        label={s.status === "PUBLISHED" ? "Đang hoạt động" : s.status} 
                        variant={s.status === "PUBLISHED" ? "success" : "neutral"} 
                      />
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-400 text-sm">
                    Chưa có dữ liệu dịch vụ trong hệ thống
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
