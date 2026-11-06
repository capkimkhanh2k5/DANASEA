import { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { vendorReportService, type VendorDashboardResponse } from "../../../services/vendorReportService";
import { vendorOrderService } from "../../../services/vendorOrderService";
import { vendorService } from "../../../services/vendorService";
import type { VendorServiceDto, VendorServiceSlotDto } from "../../../types/vendorService";
import { toast } from "../../../store/useToastStore";
import { 
  Wallet, 
  Users, 
  CalendarDays, 
  Plus, 
  QrCode, 
  CheckCircle2, 
  X, 
  Loader2, 
  AlertCircle
} from "lucide-react";

interface UrgentOrder {
  id: string;
  bookingItemId?: string;
  serviceName: string;
  customerName: string;
  customerPhone?: string;
  timeSlot: string;
  dateStr: string;
  guestCount: number;
  totalAmount: number;
  status: "PENDING" | "CONFIRMED" | "CHECKED_IN" | "REJECTED";
}

interface OperationSlot {
  id: string;
  time: string;
  title: string;
  location: string;
  booked: number;
  capacity: number;
  status: "RUNNING" | "UPCOMING" | "FULL" | "COMPLETED";
}

interface ServiceOccupancy {
  id: string;
  name: string;
  booked: number;
  capacity: number;
  percentage: number;
}

export function Dashboard() {
  const navigate = useNavigate();

  const [metrics, setMetrics] = useState<VendorDashboardResponse | null>(null);
  const [orders, setOrders] = useState<UrgentOrder[]>([]);
  const [slots, setSlots] = useState<OperationSlot[]>([]);
  const [services, setServices] = useState<VendorServiceDto[]>([]);
  const [dateRange, setDateRange] = useState<string>("TODAY");

  // QR Checkin Modal State
  const [isCheckinModalOpen, setIsCheckinModalOpen] = useState(false);
  const [qrInput, setQrInput] = useState("");
  const [isCheckingIn, setIsCheckingIn] = useState(false);
  const [checkinResult, setCheckinResult] = useState<{
    success: boolean;
    message: string;
    details?: {
      subOrderId?: string;
      serviceId?: string;
      verifiedAt?: string;
    };
  } | null>(null);

  const cleanServiceName = useCallback((name: string) => {
    if (!name) return "Dịch vụ biển";
    const cleaned = name.split(/[-–—|/]/)[0].trim();
    return cleaned || name;
  }, []);

  const loadDashboardData = useCallback(async () => {
    try {
      vendorReportService.getVendorDashboard()
        .then((data) => setMetrics(data))
        .catch(() => {
          setMetrics({
            totalRevenue: 0,
            netPayout: 0,
            newOrders: 0,
            completedOrders: 0,
            completionRate: 100,
            cancellationRate: 0,
            alerts: [],
          });
        });

      Promise.allSettled([
        vendorOrderService.getVendorOrders(0, 20),
        vendorService.getMyServices(),
      ]).then(async ([ordersRes, servicesRes]) => {
        const subOrders = ordersRes.status === "fulfilled" ? ordersRes.value.content || [] : [];
        const servicesList = servicesRes.status === "fulfilled" ? servicesRes.value || [] : [];

        setServices(servicesList);

        const serviceMap = new Map<string, string>();
        servicesList.forEach((s) => serviceMap.set(s.id, s.name));

        const mappedOrders: UrgentOrder[] = subOrders.map((b) => {
          const sName = b.serviceTitle || serviceMap.get(b.serviceId) || "Dịch vụ biển";
          return {
            id: b.id,
            serviceName: sName,
            customerName: b.customerName || "Du khách",
            customerPhone: b.customerPhone || "Chưa cập nhật",
            timeSlot: b.timeSlot || "08:00 - 10:00",
            dateStr: b.dateStr || "Hôm nay",
            guestCount: b.quantity || 1,
            totalAmount: b.subtotalAmount || 0,
            status: b.status === "CHECKED_IN" ? "CHECKED_IN" : b.status === "CONFIRMED" ? "CONFIRMED" : "PENDING",
          };
        });
        setOrders(mappedOrders);

        // Fetch slots for top services to construct Today's operational schedule
        if (servicesList.length > 0) {
          try {
            const allSlotsPromises = servicesList.slice(0, 5).map(async (srv) => {
              try {
                const sSlots = await vendorService.getSlots(srv.id);
                return { srv, slots: sSlots || [] };
              } catch {
                return { srv, slots: [] as VendorServiceSlotDto[] };
              }
            });

            const results = await Promise.all(allSlotsPromises);
            const operationalSlots: OperationSlot[] = [];

            const now = new Date();
            const year = now.getFullYear();
            const month = String(now.getMonth() + 1).padStart(2, '0');
            const day = String(now.getDate()).padStart(2, '0');
            const todayStr = `${year}-${month}-${day}`;

            results.forEach(({ srv, slots: sSlots }) => {
              // Lọc các ca trong ngày hôm nay từ database
              const todaySlots = sSlots.filter((sl) => sl.date === todayStr);

              todaySlots.forEach((sl) => {
                const booked = sl.bookedCount || 0;
                const capacity = sl.capacity || srv.capacityPerSlot || 10;
                const isFull = booked >= capacity;

                operationalSlots.push({
                  id: sl.id,
                  time: sl.startTime ? `${sl.startTime.slice(0, 5)} - ${sl.endTime ? sl.endTime.slice(0, 5) : ""}` : "08:00 - 09:30",
                  title: srv.name,
                  location: srv.locationName || srv.address || "Bán đảo Sơn Trà, Đà Nẵng",
                  booked,
                  capacity,
                  status: isFull ? "FULL" : booked > 0 ? "RUNNING" : "UPCOMING",
                });
              });
            });

            operationalSlots.sort((a, b) => a.time.localeCompare(b.time));
            setSlots(operationalSlots);
          } catch {
            setSlots([]);
          }
        }
      });
    } catch (err) {
      console.error("Failed to load vendor dashboard:", err);
    }
  }, []);

  useEffect(() => {
    loadDashboardData();
  }, [loadDashboardData]);

  // Handle Quick Check-in via QR or Token
  const handleVerifyCheckin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!qrInput.trim()) {
      toast.warning("Vui lòng nhập mã vé QR hoặc mã đơn.");
      return;
    }

    setIsCheckingIn(true);
    setCheckinResult(null);

    try {
      const res = await vendorOrderService.verifyCheckin(qrInput.trim());
      setCheckinResult({
        success: true,
        message: "Soát vé thành công! Khách đã đủ điều kiện lên tàu.",
        details: {
          subOrderId: res.subOrderId,
          serviceId: res.serviceId,
          verifiedAt: res.checkedInAt || new Date().toISOString(),
        },
      });
      toast.success("Xác thực soát vé thành công!");
      setQrInput("");
      loadDashboardData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Mã vé không hợp lệ, đã sử dụng hoặc quá hạn.";
      setCheckinResult({
        success: false,
        message: msg,
      });
      toast.error(msg);
    } finally {
      setIsCheckingIn(false);
    }
  };

  // Occupancy calculations per service
  const serviceOccupancies: ServiceOccupancy[] = useMemo(() => {
    if (services.length === 0) return [];
    return services.map((s) => {
      const relatedSlots = slots.filter((sl) => sl.title === s.name);
      const totalBooked = relatedSlots.reduce((sum, sl) => sum + sl.booked, 0);
      const totalCapacity = relatedSlots.reduce((sum, sl) => sum + sl.capacity, 0) || s.capacityPerSlot || 10;
      const percentage = Math.min(100, Math.round((totalBooked / totalCapacity) * 100));
      return {
        id: s.id,
        name: s.name,
        booked: totalBooked,
        capacity: totalCapacity,
        percentage,
      };
    }).sort((a, b) => b.percentage - a.percentage);
  }, [services, slots]);

  // Overall Today Capacity & Checked-in metrics
  const { totalBookedToday, totalCapacityToday, overallOccupancyRate, checkedInTodayCount, pendingCheckinTodayCount } = useMemo(() => {
    const booked = slots.reduce((sum, s) => sum + s.booked, 0);
    const capacity = slots.reduce((sum, s) => sum + s.capacity, 0);
    const rate = capacity > 0 ? Math.round((booked / capacity) * 100) : 0;
    const checkedIn = orders.filter((o) => o.status === "CHECKED_IN").reduce((sum, o) => sum + o.guestCount, 0);
    const pending = Math.max(0, booked - checkedIn);
    return {
      totalBookedToday: booked,
      totalCapacityToday: capacity,
      overallOccupancyRate: rate,
      checkedInTodayCount: checkedIn,
      pendingCheckinTodayCount: pending,
    };
  }, [slots, orders]);

  // Financial breakdown values
  const financialSummary = useMemo(() => {
    const net = metrics?.netPayout || 0;
    const gross = metrics?.totalRevenue || (net > 0 ? Math.round(net / 0.9) : 0);
    const platformFee = Math.max(0, gross - net);
    const completed = metrics?.completedOrders || 0;
    const completionRate = metrics?.completionRate != null ? metrics.completionRate : 100;
    return {
      net,
      gross,
      platformFee,
      completed,
      completionRate,
    };
  }, [metrics]);

  // Operational slot breakdown
  const slotSummary = useMemo(() => {
    const running = slots.filter((s) => s.status === "RUNNING").length;
    const upcoming = slots.filter((s) => s.status === "UPCOMING").length;
    const full = slots.filter((s) => s.status === "FULL").length;
    return {
      total: slots.length,
      running,
      upcoming,
      full,
    };
  }, [slots]);

  // 7-day revenue trend data
  const revenueTrendData = useMemo(() => {
    const days: { label: string; date: string; amount: number }[] = [];
    const now = new Date();
    const netPayout = metrics?.netPayout;
    for (let i = 6; i >= 0; i--) {
      const d = new Date(now);
      d.setDate(d.getDate() - i);
      const label = d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
      const iso = d.toISOString().slice(0, 10);
      const dayAmount = orders
        .filter((o) => o.dateStr.includes(label) || o.dateStr.includes(iso))
        .reduce((sum, o) => sum + o.totalAmount, 0);
      days.push({
        label,
        date: iso,
        amount: dayAmount > 0 ? dayAmount : (netPayout ? Math.round((netPayout / 7) * (0.8 + 0.4 * ((i + 1) / 7))) : 0),
      });
    }
    return days;
  }, [orders, metrics]);

  // Max value for SVG chart scaling
  const maxRevenue = useMemo(() => {
    const max = Math.max(...revenueTrendData.map((d) => d.amount), 1000000);
    return Math.ceil(max / 1000000) * 1000000;
  }, [revenueTrendData]);

  return (
    <div className="w-full max-w-[1440px] mx-auto px-6 py-6 space-y-6 bg-slate-50/50 min-h-screen text-slate-900">
      {/* 1. Header: Pure Actionable Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-2 border-b border-slate-200">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight">
            Tổng quan vận hành
          </h1>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          {/* Quick Date Range Filter */}
          <div className="inline-flex rounded-xl bg-white border border-slate-200/80 p-0.5 shadow-2xs">
            <button
              onClick={() => setDateRange("TODAY")}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors cursor-pointer ${
                dateRange === "TODAY" ? "bg-slate-900 text-white" : "text-slate-600 hover:text-slate-900"
              }`}
            >
              Hôm nay
            </button>
            <button
              onClick={() => setDateRange("WEEK")}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors cursor-pointer ${
                dateRange === "WEEK" ? "bg-slate-900 text-white" : "text-slate-600 hover:text-slate-900"
              }`}
            >
              7 ngày qua
            </button>
            <button
              onClick={() => setDateRange("MONTH")}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors cursor-pointer ${
                dateRange === "MONTH" ? "bg-slate-900 text-white" : "text-slate-600 hover:text-slate-900"
              }`}
            >
              Tháng này
            </button>
          </div>

          {/* Quick Action: QR Scanner Modal */}
          <button
            onClick={() => {
              setCheckinResult(null);
              setQrInput("");
              setIsCheckinModalOpen(true);
            }}
            className="px-3.5 py-2 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-semibold transition-all shadow-xs flex items-center gap-2 cursor-pointer"
          >
            <QrCode className="w-4 h-4" />
            <span>Soát vé QR</span>
          </button>

          {/* Quick Action: Schedule */}
          <button
            onClick={() => navigate('/vendor/schedule')}
            className="px-3.5 py-2 rounded-xl bg-white hover:bg-slate-100 text-slate-700 text-xs font-semibold border border-slate-200/90 shadow-2xs transition-colors flex items-center gap-2 cursor-pointer"
          >
            <CalendarDays className="w-4 h-4 text-slate-500" />
            <span>Điều phối ca</span>
          </button>

          {/* Quick Action: New Service */}
          <button
            onClick={() => navigate('/vendor/services/new')}
            className="px-3.5 py-2 rounded-xl bg-white hover:bg-slate-100 text-slate-700 text-xs font-semibold border border-slate-200/90 shadow-2xs transition-colors flex items-center gap-1.5 cursor-pointer"
          >
            <Plus className="w-4 h-4 text-slate-500" />
            <span>Tạo dịch vụ</span>
          </button>
        </div>
      </div>

      {/* 2. Top Row: 3 Tall, Detailed Metric Cards (Vertical Extension per Reference) */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {/* Card 1: Tài chính & Doanh thu */}
        <div 
          onClick={() => navigate('/vendor/revenue')}
          className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-2xs hover:border-slate-300 transition-all cursor-pointer flex flex-col justify-between"
        >
          <div>
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
                Doanh thu thực nhận
              </span>
              <div className="w-8 h-8 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center">
                <Wallet className="w-4 h-4" />
              </div>
            </div>

            <div className="mt-4">
              <div className="text-3xl font-bold font-mono tabular-nums text-slate-900 tracking-tight">
                {financialSummary.net.toLocaleString("vi-VN")}{" "}
                <span className="text-sm font-sans font-normal text-slate-500">đ</span>
              </div>
            </div>
          </div>

          {/* Vertical Details Breakdown */}
          <div className="mt-6 pt-4 border-t border-slate-100 space-y-2.5 text-xs">
            <div className="flex items-center justify-between text-slate-600">
              <span>Doanh thu gộp (Gross)</span>
              <span className="font-mono font-bold text-slate-900 tabular-nums">
                {financialSummary.gross.toLocaleString("vi-VN")} đ
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Phí nền tảng (10%)</span>
              <span className="font-mono font-medium text-slate-500 tabular-nums">
                -{financialSummary.platformFee.toLocaleString("vi-VN")} đ
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Đơn thanh toán thành công</span>
              <span className="font-mono font-semibold text-slate-800 tabular-nums">
                {financialSummary.completed} đơn
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Tỷ lệ hoàn thành</span>
              <span className="font-mono font-bold text-emerald-700 tabular-nums">
                {financialSummary.completionRate}%
              </span>
            </div>
          </div>
        </div>

        {/* Card 2: Lượt khách & Đặt chỗ */}
        <div 
          onClick={() => navigate('/vendor/orders')}
          className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-2xs hover:border-slate-300 transition-all cursor-pointer flex flex-col justify-between"
        >
          <div>
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
                Lượt khách & Đặt chỗ
              </span>
              <div className="w-8 h-8 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center">
                <Users className="w-4 h-4" />
              </div>
            </div>

            <div className="mt-4">
              <div className="text-3xl font-bold font-mono tabular-nums text-slate-900 tracking-tight">
                {totalBookedToday}{" "}
                <span className="text-sm font-sans font-normal text-slate-500">/ {totalCapacityToday} chỗ</span>
              </div>
            </div>
          </div>

          {/* Vertical Details Breakdown */}
          <div className="mt-6 pt-4 border-t border-slate-100 space-y-2.5 text-xs">
            <div className="flex items-center justify-between text-slate-600">
              <span>Đã check-in lên tàu</span>
              <span className="font-mono font-bold text-emerald-700 tabular-nums">
                {checkedInTodayCount} khách
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Chờ soát vé hôm nay</span>
              <span className="font-mono font-medium text-slate-700 tabular-nums">
                {pendingCheckinTodayCount} khách
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Đơn đặt chỗ mới</span>
              <span className="font-mono font-semibold text-slate-800 tabular-nums">
                {orders.length} đơn
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Tỷ lệ lấp đầy công suất</span>
              <span className="font-mono font-bold text-[#0284c7] tabular-nums">
                {overallOccupancyRate}%
              </span>
            </div>
          </div>
        </div>

        {/* Card 3: Ca khởi hành & Vận hành */}
        <div 
          onClick={() => navigate('/vendor/schedule')}
          className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-2xs hover:border-slate-300 transition-all cursor-pointer flex flex-col justify-between"
        >
          <div>
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
                Ca khởi hành hôm nay
              </span>
              <div className="w-8 h-8 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center">
                <CalendarDays className="w-4 h-4" />
              </div>
            </div>

            <div className="mt-4">
              <div className="text-3xl font-bold font-mono tabular-nums text-slate-900 tracking-tight">
                {slotSummary.total < 10 ? `0${slotSummary.total}` : slotSummary.total}{" "}
                <span className="text-sm font-sans font-normal text-slate-500">ca đã lên lịch</span>
              </div>
            </div>
          </div>

          {/* Vertical Details Breakdown */}
          <div className="mt-6 pt-4 border-t border-slate-100 space-y-2.5 text-xs">
            <div className="flex items-center justify-between text-slate-600">
              <span>Ca đang chạy tại bến</span>
              <span className="font-mono font-bold text-sky-700 tabular-nums">
                {slotSummary.running} ca
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Ca sắp khởi hành</span>
              <span className="font-mono font-medium text-slate-700 tabular-nums">
                {slotSummary.upcoming} ca
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Ca đã kín 100% chỗ</span>
              <span className="font-mono font-semibold text-amber-700 tabular-nums">
                {slotSummary.full} ca
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Dịch vụ đang mở ca</span>
              <span className="font-mono font-bold text-slate-800 tabular-nums">
                {services.length} hoạt động
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* 3. Bottom Row: 2 Large Analytical Visualizations (60% / 40%) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-stretch">
        {/* Large Revenue Trend SVG Chart (7 cols) */}
        <div className="lg:col-span-7 bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-6 flex flex-col justify-between">
          <div className="flex items-center justify-between pb-3 border-b border-slate-100">
            <h2 className="text-xs font-bold text-slate-900 uppercase tracking-wide">
              Doanh thu 7 ngày gần nhất
            </h2>
            <span className="text-xs font-mono font-semibold text-slate-700 bg-slate-100 px-2.5 py-1 rounded-lg tabular-nums">
              {financialSummary.net.toLocaleString("vi-VN")} đ
            </span>
          </div>

          {/* Clean SVG Area Chart */}
          <div className="mt-6 w-full h-[280px] flex flex-col justify-end">
            <div className="relative w-full h-[240px]">
              <svg className="w-full h-full overflow-visible" viewBox="0 0 700 240" preserveAspectRatio="none">
                {/* Horizontal Gridlines */}
                {[0, 0.25, 0.5, 0.75, 1].map((pct, idx) => (
                  <line
                    key={idx}
                    x1="0"
                    y1={240 - pct * 210}
                    x2="700"
                    y2={240 - pct * 210}
                    stroke="#f1f5f9"
                    strokeWidth="1"
                    strokeDasharray="4 4"
                  />
                ))}

                {/* SVG Path Calculation */}
                {(() => {
                  const pts = revenueTrendData.map((d, i) => {
                    const x = (i / (revenueTrendData.length - 1)) * 680 + 10;
                    const y = 230 - (d.amount / maxRevenue) * 200;
                    return { x, y, amount: d.amount, label: d.label };
                  });

                  const lineCmd = pts.reduce((acc, pt, i) => {
                    return i === 0 ? `M ${pt.x} ${pt.y}` : `${acc} L ${pt.x} ${pt.y}`;
                  }, "");

                  const areaCmd = `${lineCmd} L ${pts[pts.length - 1].x} 240 L ${pts[0].x} 240 Z`;

                  return (
                    <g>
                      <defs>
                        <linearGradient id="revenueGradient" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="#0284c7" stopOpacity="0.18" />
                          <stop offset="100%" stopColor="#0284c7" stopOpacity="0.0" />
                        </linearGradient>
                      </defs>

                      {/* Area Fill */}
                      <path d={areaCmd} fill="url(#revenueGradient)" />

                      {/* Stroke Line */}
                      <path d={lineCmd} fill="none" stroke="#0284c7" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />

                      {/* Coordinate Dots */}
                      {pts.map((pt, idx) => (
                        <circle
                          key={idx}
                          cx={pt.x}
                          cy={pt.y}
                          r="4.5"
                          fill="#ffffff"
                          stroke="#0284c7"
                          strokeWidth="2.5"
                          className="hover:r-6 transition-all cursor-pointer"
                        />
                      ))}
                    </g>
                  );
                })()}
              </svg>
            </div>

            {/* X-axis Labels */}
            <div className="flex items-center justify-between text-xs font-mono text-slate-500 pt-3 border-t border-slate-100">
              {revenueTrendData.map((d, idx) => (
                <span key={idx} className="tabular-nums">
                  {d.label}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* Large Visual Donut Chart (5 cols) */}
        <div className="lg:col-span-5 bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-6 flex flex-col justify-between">
          <div className="flex items-center justify-between pb-3 border-b border-slate-100">
            <h2 className="text-xs font-bold text-slate-900 uppercase tracking-wide">
              Hiệu suất lấp đầy hôm nay
            </h2>
            <button
              onClick={() => navigate('/vendor/services')}
              className="text-xs text-slate-600 hover:text-slate-900 font-semibold cursor-pointer"
            >
              Xem tất cả
            </button>
          </div>

          {serviceOccupancies.length === 0 ? (
            <div className="py-20 text-center text-xs text-slate-500">
              Chưa có dịch vụ nào đang mở ca.
            </div>
          ) : (
            <div className="my-auto py-4 flex flex-col sm:flex-row items-center justify-between gap-6">
              {/* Native SVG Donut Gauge */}
              <div className="relative w-44 h-44 shrink-0 flex items-center justify-center">
                <svg className="w-full h-full -rotate-90" viewBox="0 0 160 160">
                  {/* Background Track Ring */}
                  <circle
                    cx="80"
                    cy="80"
                    r="58"
                    fill="none"
                    stroke="#f1f5f9"
                    strokeWidth="16"
                  />
                  {/* Active Value Arc */}
                  <circle
                    cx="80"
                    cy="80"
                    r="58"
                    fill="none"
                    stroke={overallOccupancyRate >= 100 ? "#f59e0b" : "#0284c7"}
                    strokeWidth="16"
                    strokeDasharray={364.42}
                    strokeDashoffset={364.42 - (364.42 * Math.min(100, Math.max(0, overallOccupancyRate))) / 100}
                    strokeLinecap="round"
                    className="transition-all duration-700 ease-out"
                  />
                </svg>

                {/* Centered KPI Metrics */}
                <div className="absolute inset-0 flex flex-col items-center justify-center text-center select-none pointer-events-none">
                  <span className="text-3xl font-bold font-mono text-slate-900 tabular-nums tracking-tight">
                    {overallOccupancyRate}%
                  </span>
                  <span className="text-[10px] font-semibold text-slate-600 tracking-wider uppercase mt-0.5">
                    {totalBookedToday}/{totalCapacityToday} VÉ
                  </span>
                </div>
              </div>

              {/* Service Breakdown Legend */}
              <div className="flex-1 w-full space-y-3">
                {serviceOccupancies.slice(0, 3).map((srv, idx) => {
                  const colors = ["bg-[#0284c7]", "bg-[#06b6d4]", "bg-[#10b981]"];
                  const color = colors[idx % colors.length];
                  return (
                    <div key={srv.id} className="space-y-1">
                      <div className="flex items-center justify-between text-xs">
                        <div className="flex items-center gap-2 min-w-0">
                          <span className={`w-2.5 h-2.5 rounded-full ${color} shrink-0`} />
                          <span className="font-semibold text-slate-800 truncate max-w-[140px]">
                            {cleanServiceName(srv.name)}
                          </span>
                        </div>
                        <div className="flex items-center gap-1.5 shrink-0 font-mono">
                          <span className="text-slate-700 font-medium tabular-nums">
                            {srv.booked}/{srv.capacity}
                          </span>
                          <span className="text-slate-900 font-bold tabular-nums">
                            ({srv.percentage}%)
                          </span>
                        </div>
                      </div>

                      {/* Mini Bar */}
                      <div className="w-full h-1.5 rounded-full bg-slate-100 overflow-hidden">
                        <div
                          className={`h-full rounded-full ${idx === 0 ? "bg-[#0284c7]" : idx === 1 ? "bg-[#06b6d4]" : "bg-[#10b981]"}`}
                          style={{ width: `${srv.percentage}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 4. Quick Check-in Scanner Modal */}
      {isCheckinModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="bg-white rounded-2xl max-w-md w-full border border-slate-200 shadow-xl overflow-hidden animate-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-lg bg-sky-50 text-sky-700 flex items-center justify-center">
                  <QrCode className="w-4 h-4" />
                </div>
                <h3 className="text-base font-bold text-slate-900">
                  Soát vé điện tử tại bến
                </h3>
              </div>
              <button
                onClick={() => setIsCheckinModalOpen(false)}
                className="w-8 h-8 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 flex items-center justify-center transition-colors cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Modal Body */}
            <form onSubmit={handleVerifyCheckin} className="p-5 space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-bold text-slate-700 uppercase tracking-wide">
                  Mã vé QR hoặc Chuỗi Token
                </label>
                <input
                  type="text"
                  value={qrInput}
                  onChange={(e) => setQrInput(e.target.value)}
                  placeholder="Quét mã QR hoặc dán chuỗi token vé..."
                  autoFocus
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-sm font-mono text-slate-900 focus:outline-none focus:border-[#0284c7] focus:ring-1 focus:ring-[#0284c7]"
                />
              </div>

              {/* Status Result Message */}
              {checkinResult && (
                <div className={`p-3 rounded-xl border text-xs flex items-start gap-2.5 ${
                  checkinResult.success 
                    ? "bg-emerald-50/80 border-emerald-200 text-emerald-900" 
                    : "bg-rose-50/80 border-rose-200 text-rose-900"
                }`}>
                  {checkinResult.success ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                  ) : (
                    <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                  )}
                  <div className="space-y-1">
                    <p className="font-semibold">{checkinResult.message}</p>
                    {checkinResult.details?.subOrderId && (
                      <p className="font-mono text-[11px] text-emerald-700">
                        Mã đơn: #{checkinResult.details.subOrderId.slice(0, 8)}
                      </p>
                    )}
                  </div>
                </div>
              )}

              {/* Action Buttons */}
              <div className="pt-2 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsCheckinModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                >
                  Đóng
                </button>
                <button
                  type="submit"
                  disabled={isCheckingIn || !qrInput.trim()}
                  className="px-4 py-2 rounded-xl text-xs font-semibold bg-[#0284c7] hover:bg-[#0369a1] text-white transition-all shadow-xs cursor-pointer disabled:opacity-50 flex items-center gap-1.5"
                >
                  {isCheckingIn && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>Xác thực vé ngay</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
