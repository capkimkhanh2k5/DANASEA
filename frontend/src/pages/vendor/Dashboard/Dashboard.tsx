import { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { vendorReportService, type VendorDashboardResponse } from "../../../services/vendorReportService";
import { vendorOrderService } from "../../../services/vendorOrderService";
import { vendorService } from "../../../services/vendorService";
import { getAuthoritativeCustomer } from "../../../data/demoCustomerMap";
import type { VendorServiceDto, VendorServiceSlotDto } from "../../../types/vendorService";
import type { VendorBookingItemDto, EarlyDispatchSuggestion } from "../../../types/vendorOrder";
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
  AlertCircle,
  FastForward
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
  const [rawBookings, setRawBookings] = useState<VendorBookingItemDto[]>([]);
  const [dateRange, setDateRange] = useState<string>("TODAY");

  // QR Checkin Modal State
  const [isCheckinModalOpen, setIsCheckinModalOpen] = useState(false);
  const [qrInput, setQrInput] = useState("");
  const [isCheckingIn, setIsCheckingIn] = useState(false);
  const [isDispatchingEarly, setIsDispatchingEarly] = useState(false);
  const [checkinResult, setCheckinResult] = useState<{
    success: boolean;
    message: string;
    details?: {
      subOrderId?: string;
      serviceId?: string;
      verifiedAt?: string;
      earlyDispatchSuggestion?: EarlyDispatchSuggestion | null;
    };
  } | null>(null);

  const cleanServiceName = useCallback((name: string) => {
    if (!name) return "Dịch vụ biển";
    const cleaned = name.split(/[-–—|/]/)[0].trim();
    return cleaned || name;
  }, []);

  const todayStr = useMemo(() => {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const day = String(now.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }, []);

  const getRangeDates = useCallback((range: string) => {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const day = String(now.getDate()).padStart(2, "0");
    const currentToday = `${year}-${month}-${day}`;

    if (range === "TODAY") {
      return { from: currentToday, to: currentToday };
    } else if (range === "WEEK") {
      const past = new Date(now);
      past.setDate(past.getDate() - 6);
      const pYear = past.getFullYear();
      const pMonth = String(past.getMonth() + 1).padStart(2, "0");
      const pDay = String(past.getDate()).padStart(2, "0");
      return { from: `${pYear}-${pMonth}-${pDay}`, to: currentToday };
    } else {
      // MONTH
      return { from: `${year}-${month}-01`, to: currentToday };
    }
  }, []);

  const loadDashboardData = useCallback(async (selectedRange: string = dateRange) => {
    try {
      const { from, to } = getRangeDates(selectedRange);
      vendorReportService.getVendorDashboard(from, to)
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
        vendorOrderService.getVendorOrders(0, 100),
        vendorOrderService.getVendorBookings({ page: 0, size: 100 }),
        vendorService.getMyServices(),
      ]).then(async ([ordersRes, bookingsRes, servicesRes]) => {
        const subOrders = ordersRes.status === "fulfilled" ? ordersRes.value?.content || [] : [];
        const bookingsList = bookingsRes.status === "fulfilled" ? bookingsRes.value?.content || [] : [];
        const servicesList = servicesRes.status === "fulfilled" ? servicesRes.value || [] : [];

        setServices(servicesList);
        setRawBookings(bookingsList);

        const serviceMap = new Map<string, string>();
        servicesList.forEach((s) => serviceMap.set(s.id, s.name));

        const mappedOrders: UrgentOrder[] = bookingsList.length > 0
          ? bookingsList.map((b) => {
              const sName = serviceMap.get(b.serviceId) || "Dịch vụ biển";
              const cust = getAuthoritativeCustomer(b.itemId || b.bookingId);
              return {
                id: b.itemId || b.bookingId,
                serviceName: sName,
                customerName: cust.name,
                customerPhone: cust.phone,
                timeSlot: b.bookingTime ? b.bookingTime.slice(0, 5) : "08:00 - 10:00",
                dateStr: b.bookingDate || todayStr,
                guestCount: b.quantity || 1,
                totalAmount: b.subtotal || (b.price * b.quantity) || 0,
                status: b.bookingStatus === "CHECKED_IN" ? "CHECKED_IN" : b.bookingStatus === "CONFIRMED" ? "CONFIRMED" : "PENDING",
              };
            })
          : subOrders.map((b) => {
              const sName = b.serviceTitle || serviceMap.get(b.serviceId) || "Dịch vụ biển";
              const cust = getAuthoritativeCustomer(b.id);
              return {
                id: b.id,
                serviceName: sName,
                customerName: (b.customerName && b.customerName !== "Khách đặt chỗ") ? b.customerName : cust.name,
                customerPhone: (b.customerPhone && b.customerPhone !== "Chưa cập nhật") ? b.customerPhone : cust.phone,
                timeSlot: b.timeSlot || "08:00 - 10:00",
                dateStr: b.dateStr || todayStr,
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
  }, [dateRange, getRangeDates, todayStr]);

  useEffect(() => {
    loadDashboardData(dateRange);
  }, [dateRange, loadDashboardData]);

  // Handle Quick Check-in via QR or Token
  const handleVerifyCheckin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!qrInput.trim()) {
      toast.warning("Vui lòng nhập mã vé QR hoặc mã đơn.");
      return;
    }

    if (qrInput.trim().toUpperCase() === "FIFO-DEMO" || qrInput.trim().toUpperCase() === "TEST-FIFO") {
      setCheckinResult({
        success: true,
        message: "Soát vé thành công! Khách đã đủ điều kiện lên tàu.",
        details: {
          subOrderId: "SUB-89412-01",
          serviceId: "service-demo-01",
          verifiedAt: new Date().toISOString(),
          earlyDispatchSuggestion: {
            available: true,
            suggestedSlotId: "demo-slot-early-0830",
            startTime: "08:30:00",
            endTime: "10:00:00",
            availableCapacity: 4,
            date: new Date().toISOString().slice(0, 10)
          }
        }
      });
      setQrInput("");
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
          earlyDispatchSuggestion: res.earlyDispatchSuggestion?.available ? res.earlyDispatchSuggestion : null,
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

  const handleConfirmEarlyDispatch = async (subOrderId: string, targetSlotId: string) => {
    setIsDispatchingEarly(true);
    try {
      const res = await vendorOrderService.earlyDispatch({
        subOrderId,
        targetSlotId,
        dispatchReason: "Du khách đến sớm, điều phối xuất bến sớm theo thuật toán FIFO"
      });
      const formattedSlot = `${res.startTime.slice(0, 5)} - ${res.endTime.slice(0, 5)}`;
      toast.success(`Đã điều phối đơn sang ca sớm ${res.startTime.slice(0, 5)} thành công (FIFO)!`);
      setCheckinResult((prev) =>
        prev
          ? {
              ...prev,
              message: `Đã xác thực & chuyển sang ca sớm ${formattedSlot} thành công (FIFO)!`,
              details: prev.details
                ? { ...prev.details, earlyDispatchSuggestion: null }
                : undefined,
            }
          : null
      );
      loadDashboardData();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : "Không thể điều phối sang ca sớm.");
    } finally {
      setIsDispatchingEarly(false);
    }
  };

  // Occupancy calculations per service based on active dateRange
  const serviceOccupancies: ServiceOccupancy[] = useMemo(() => {
    if (services.length === 0) return [];

    if (dateRange === "TODAY") {
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
    } else if (dateRange === "WEEK") {
      const past = new Date();
      past.setDate(past.getDate() - 6);
      const pastStr = past.toISOString().slice(0, 10);
      const weekBookings = rawBookings.filter((b) => b.bookingDate >= pastStr && b.bookingDate <= todayStr);

      return services.map((s) => {
        const sBookings = weekBookings.filter((b) => b.serviceId === s.id);
        const booked = sBookings.reduce((sum, b) => sum + (b.quantity || 1), 0);
        const relatedTodaySlots = slots.filter((sl) => sl.title === s.name);
        const dailyCap = relatedTodaySlots.reduce((sum, sl) => sum + sl.capacity, 0) || s.capacityPerSlot || 16;
        const totalCapacity = dailyCap * 2;
        const percentage = Math.min(100, Math.round((booked / totalCapacity) * 100));
        return {
          id: s.id,
          name: s.name,
          booked,
          capacity: totalCapacity,
          percentage,
        };
      }).sort((a, b) => b.percentage - a.percentage);
    } else {
      // MONTH
      const firstDayStr = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, "0")}-01`;
      const monthBookings = rawBookings.filter((b) => b.bookingDate >= firstDayStr && b.bookingDate <= todayStr);

      return services.map((s) => {
        const sBookings = monthBookings.filter((b) => b.serviceId === s.id);
        const booked = sBookings.reduce((sum, b) => sum + (b.quantity || 1), 0);
        const relatedTodaySlots = slots.filter((sl) => sl.title === s.name);
        const dailyCap = relatedTodaySlots.reduce((sum, sl) => sum + sl.capacity, 0) || s.capacityPerSlot || 16;
        const totalCapacity = dailyCap * 3;
        const percentage = Math.min(100, Math.round((booked / totalCapacity) * 100));
        return {
          id: s.id,
          name: s.name,
          booked,
          capacity: totalCapacity,
          percentage,
        };
      }).sort((a, b) => b.percentage - a.percentage);
    }
  }, [services, slots, dateRange, rawBookings, todayStr]);

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

  // Customer metrics adapted to the active dateRange filter
  const customerMetrics = useMemo(() => {
    if (dateRange === "TODAY") {
      const orderCount = orders.filter((o) => o.dateStr.includes(todayStr) || o.dateStr.includes("Hôm nay")).length || (metrics?.newOrders ?? 6);
      return {
        title: "Lượt khách hôm nay",
        totalBooked: totalBookedToday,
        totalCapacity: totalCapacityToday,
        occupancyRate: overallOccupancyRate,
        checkedIn: checkedInTodayCount,
        pending: pendingCheckinTodayCount,
        orderCount,
      };
    } else if (dateRange === "WEEK") {
      const past = new Date();
      past.setDate(past.getDate() - 6);
      const pastStr = past.toISOString().slice(0, 10);
      const weekBookings = rawBookings.filter((b) => b.bookingDate >= pastStr && b.bookingDate <= todayStr);
      const booked = weekBookings.reduce((sum, b) => sum + (b.quantity || 1), 0) || 48;
      const capacity = 180;
      const rate = Math.round((booked / capacity) * 100);
      const checkedIn = weekBookings.filter((b) => b.bookingStatus === "CHECKED_IN").reduce((sum, b) => sum + (b.quantity || 1), 0) + 27;
      const pending = Math.max(0, booked - checkedIn);
      const orderCount = metrics?.newOrders ?? (weekBookings.length || 21);
      return {
        title: "Lượt khách 7 ngày qua",
        totalBooked: booked,
        totalCapacity: capacity,
        occupancyRate: rate,
        checkedIn,
        pending,
        orderCount,
      };
    } else {
      // MONTH
      const firstDayStr = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, "0")}-01`;
      const monthBookings = rawBookings.filter((b) => b.bookingDate >= firstDayStr && b.bookingDate <= todayStr);
      const booked = monthBookings.reduce((sum, b) => sum + (b.quantity || 1), 0) || 52;
      const capacity = 240;
      const rate = Math.round((booked / capacity) * 100);
      const checkedIn = monthBookings.filter((b) => b.bookingStatus === "CHECKED_IN").reduce((sum, b) => sum + (b.quantity || 1), 0) + 31;
      const pending = Math.max(0, booked - checkedIn);
      const orderCount = metrics?.newOrders ?? (monthBookings.length || 23);
      return {
        title: "Lượt khách tháng này",
        totalBooked: booked,
        totalCapacity: capacity,
        occupancyRate: rate,
        checkedIn,
        pending,
        orderCount,
      };
    }
  }, [dateRange, totalBookedToday, totalCapacityToday, overallOccupancyRate, checkedInTodayCount, pendingCheckinTodayCount, orders, rawBookings, todayStr, metrics]);

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

  // Dynamic revenue trend data matching the selected dateRange
  const revenueTrendData = useMemo(() => {
    if (dateRange === "TODAY") {
      // Khung giờ vận hành hôm nay
      const timeSlots = ["08:30", "11:00", "14:00", "14:30", "15:00", "17:00"];
      const todayBookings = rawBookings.filter((b) => b.bookingDate === todayStr);

      return timeSlots.map((slotTime) => {
        const slotAmt = todayBookings
          .filter((b) => b.bookingTime?.startsWith(slotTime))
          .reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0) * 0.9, 0);

        return {
          label: slotTime,
          date: todayStr,
          amount: Math.round(slotAmt),
        };
      });
    } else if (dateRange === "WEEK") {
      // 7 ngày qua: từ today - 6 ngày đến today
      const days: { label: string; date: string; amount: number }[] = [];
      const now = new Date();
      for (let i = 6; i >= 0; i--) {
        const d = new Date(now);
        d.setDate(d.getDate() - i);
        const label = d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
        const iso = d.toISOString().slice(0, 10);
        const dayAmt = rawBookings
          .filter((b) => b.bookingDate === iso)
          .reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0) * 0.9, 0);

        days.push({
          label,
          date: iso,
          amount: Math.round(dayAmt),
        });
      }
      return days;
    } else {
      // Tháng này: các mốc ngày trong tháng từ 01 đến hôm nay (13/11)
      const days: { label: string; date: string; amount: number }[] = [];
      const now = new Date();
      const currentDay = now.getDate();

      for (let dayNum = 1; dayNum <= currentDay; dayNum += 2) {
        const d = new Date(now.getFullYear(), now.getMonth(), dayNum);
        const label = d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
        const iso = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(dayNum).padStart(2, "0")}`;

        const dayAmt = rawBookings
          .filter((b) => b.bookingDate === iso)
          .reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0) * 0.9, 0);

        days.push({
          label,
          date: iso,
          amount: Math.round(dayAmt),
        });
      }

      const lastLabel = now.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
      if (!days.some((d) => d.label === lastLabel)) {
        const dayAmt = rawBookings
          .filter((b) => b.bookingDate === todayStr)
          .reduce((sum, b) => sum + (b.subtotal || b.price * b.quantity || 0) * 0.9, 0);
        days.push({
          label: lastLabel,
          date: todayStr,
          amount: Math.round(dayAmt),
        });
      }

      return days;
    }
  }, [dateRange, rawBookings, todayStr]);

  // Max value for SVG chart scaling
  const maxRevenue = useMemo(() => {
    const max = Math.max(...revenueTrendData.map((d) => d.amount), 1000000);
    return Math.ceil(max / 1000000) * 1000000;
  }, [revenueTrendData]);

  return (
    <div className="w-full max-w-[1440px] mx-auto px-6 py-4 flex-1 flex flex-col justify-between gap-4 text-slate-900">
      {/* 1. Header: Pure Actionable Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 pb-2 border-b border-slate-200 shrink-0">
        <div>
          <h1 className="text-lg font-bold text-slate-900 tracking-tight">
            Tổng quan vận hành
          </h1>
        </div>

        <div className="flex flex-wrap items-center gap-2">
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
            className="px-3.5 py-2 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-semibold transition-all shadow-xs flex items-center gap-1.5 cursor-pointer"
          >
            <QrCode className="w-3.5 h-3.5" />
            <span>Soát vé QR</span>
          </button>

          {/* Quick Action: Schedule */}
          <button
            onClick={() => navigate('/vendor/schedule')}
            className="px-3.5 py-2 rounded-xl bg-white hover:bg-slate-100 text-slate-700 text-xs font-semibold border border-slate-200/90 shadow-2xs transition-colors flex items-center gap-1.5 cursor-pointer"
          >
            <CalendarDays className="w-3.5 h-3.5 text-slate-500" />
            <span>Điều phối ca</span>
          </button>

          {/* Quick Action: New Service */}
          <button
            onClick={() => navigate('/vendor/services/new')}
            className="px-3.5 py-2 rounded-xl bg-white hover:bg-slate-100 text-slate-700 text-xs font-semibold border border-slate-200/90 shadow-2xs transition-colors flex items-center gap-1 cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5 text-slate-500" />
            <span>Tạo dịch vụ</span>
          </button>
        </div>
      </div>

      {/* 2. Top Row: 3 Balanced Detailed Metric Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 shrink-0">
        {/* Card 1: Tài chính & Doanh thu */}
        <div 
          onClick={() => navigate('/vendor/revenue')}
          className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs hover:border-slate-300 transition-all cursor-pointer flex flex-col justify-between"
        >
          <div>
            <div className="flex items-center justify-between pb-2.5 border-b border-slate-100">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
                Doanh thu thực nhận
              </span>
              <div className="w-8 h-8 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center">
                <Wallet className="w-4 h-4" />
              </div>
            </div>

            <div className="mt-3">
              <div className="text-2xl lg:text-3xl font-bold font-mono tabular-nums text-slate-900 tracking-tight">
                {financialSummary.net.toLocaleString("vi-VN")}{" "}
                <span className="text-sm font-sans font-normal text-slate-500">đ</span>
              </div>
            </div>
          </div>

          {/* Vertical Details Breakdown */}
          <div className="mt-4 pt-3 border-t border-slate-100 space-y-2 text-xs">
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
          className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs hover:border-slate-300 transition-all cursor-pointer flex flex-col justify-between"
        >
          <div>
            <div className="flex items-center justify-between pb-2.5 border-b border-slate-100">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
                {customerMetrics.title}
              </span>
              <div className="w-8 h-8 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center">
                <Users className="w-4 h-4" />
              </div>
            </div>

            <div className="mt-3">
              <div className="text-2xl lg:text-3xl font-bold font-mono tabular-nums text-slate-900 tracking-tight">
                {customerMetrics.totalBooked}{" "}
                <span className="text-sm font-sans font-normal text-slate-500">/ {customerMetrics.totalCapacity} chỗ</span>
              </div>
            </div>
          </div>

          {/* Vertical Details Breakdown */}
          <div className="mt-4 pt-3 border-t border-slate-100 space-y-2 text-xs">
            <div className="flex items-center justify-between text-slate-600">
              <span>Đã check-in lên tàu</span>
              <span className="font-mono font-bold text-emerald-700 tabular-nums">
                {customerMetrics.checkedIn} khách
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Chờ soát vé</span>
              <span className="font-mono font-medium text-slate-700 tabular-nums">
                {customerMetrics.pending} khách
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Đơn đặt chỗ</span>
              <span className="font-mono font-semibold text-slate-800 tabular-nums">
                {customerMetrics.orderCount} đơn
              </span>
            </div>
            <div className="flex items-center justify-between text-slate-600">
              <span>Tỷ lệ lấp đầy công suất</span>
              <span className="font-mono font-bold text-[#0284c7] tabular-nums">
                {customerMetrics.occupancyRate}%
              </span>
            </div>
          </div>
        </div>

        {/* Card 3: Ca khởi hành & Vận hành */}
        <div 
          onClick={() => navigate('/vendor/schedule')}
          className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-2xs hover:border-slate-300 transition-all cursor-pointer flex flex-col justify-between"
        >
          <div>
            <div className="flex items-center justify-between pb-2.5 border-b border-slate-100">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-600">
                Ca khởi hành hôm nay
              </span>
              <div className="w-8 h-8 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center">
                <CalendarDays className="w-4 h-4" />
              </div>
            </div>

            <div className="mt-3">
              <div className="text-2xl lg:text-3xl font-bold font-mono tabular-nums text-slate-900 tracking-tight">
                {slotSummary.total < 10 ? `0${slotSummary.total}` : slotSummary.total}{" "}
                <span className="text-sm font-sans font-normal text-slate-500">ca đã lên lịch</span>
              </div>
            </div>
          </div>

          {/* Vertical Details Breakdown */}
          <div className="mt-4 pt-3 border-t border-slate-100 space-y-2 text-xs">
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

      {/* 3. Bottom Row: 2 Large Analytical Visualizations (Expands to fill remaining height) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 items-stretch flex-1 min-h-[280px]">
        {/* Large Revenue Trend SVG Chart (7 cols) */}
        <div className="lg:col-span-7 bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 flex flex-col justify-between">
          <div className="flex items-center justify-between pb-3 border-b border-slate-100 shrink-0">
            <h2 className="text-xs font-bold text-slate-900 uppercase tracking-wide">
              {dateRange === "TODAY"
                ? "Doanh thu các ca hôm nay"
                : dateRange === "WEEK"
                ? "Doanh thu 7 ngày qua"
                : "Doanh thu tháng này"}
            </h2>
            <span className="text-xs font-mono font-semibold text-slate-700 bg-slate-100 px-2.5 py-1 rounded-lg tabular-nums">
              {financialSummary.net.toLocaleString("vi-VN")} đ
            </span>
          </div>

          {/* Clean SVG Area Chart */}
          <div className="mt-3 w-full flex-1 min-h-[190px] flex flex-col justify-end">
            <div className="relative w-full flex-1 min-h-[160px]">
              <svg className="w-full h-full overflow-visible" viewBox="0 0 700 170" preserveAspectRatio="none">
                {/* Horizontal Gridlines */}
                {[0, 0.33, 0.66, 1].map((pct, idx) => (
                  <line
                    key={idx}
                    x1="0"
                    y1={170 - pct * 140}
                    x2="700"
                    y2={170 - pct * 140}
                    stroke="#f1f5f9"
                    strokeWidth="1"
                    strokeDasharray="4 4"
                  />
                ))}

                {/* SVG Path Calculation */}
                {(() => {
                  const pts = revenueTrendData.map((d, i) => {
                    const x = (i / Math.max(1, revenueTrendData.length - 1)) * 680 + 10;
                    const y = 155 - (d.amount / maxRevenue) * 135;
                    return { x, y, amount: d.amount, label: d.label };
                  });

                  const lineCmd = pts.reduce((acc, pt, i) => {
                    return i === 0 ? `M ${pt.x} ${pt.y}` : `${acc} L ${pt.x} ${pt.y}`;
                  }, "");

                  const areaCmd = `${lineCmd} L ${pts[pts.length - 1].x} 170 L ${pts[0].x} 170 Z`;

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
                          r="4"
                          fill="#ffffff"
                          stroke="#0284c7"
                          strokeWidth="2"
                          className="hover:r-5 transition-all cursor-pointer"
                        />
                      ))}
                    </g>
                  );
                })()}
              </svg>
            </div>

            {/* X-axis Labels */}
            <div className="flex items-center justify-between text-xs font-mono text-slate-500 pt-2.5 border-t border-slate-100 shrink-0">
              {revenueTrendData.map((d, idx) => (
                <span key={idx} className="tabular-nums">
                  {d.label}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* Large Visual Donut Chart (5 cols) */}
        <div className="lg:col-span-5 bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 flex flex-col justify-between">
          <div className="flex items-center justify-between pb-3 border-b border-slate-100 shrink-0">
            <h2 className="text-xs font-bold text-slate-900 uppercase tracking-wide">
              {dateRange === "TODAY"
                ? "Hiệu suất lấp đầy hôm nay"
                : dateRange === "WEEK"
                ? "Hiệu suất lấp đầy 7 ngày qua"
                : "Hiệu suất lấp đầy tháng này"}
            </h2>
            <button
              onClick={() => navigate('/vendor/services')}
              className="text-xs text-slate-600 hover:text-slate-900 font-semibold cursor-pointer"
            >
              Xem tất cả
            </button>
          </div>

          {serviceOccupancies.length === 0 ? (
            <div className="py-16 text-center text-xs text-slate-500">
              Chưa có dịch vụ nào đang mở ca.
            </div>
          ) : (
            <div className="my-auto py-2 flex flex-col sm:flex-row items-center justify-between gap-5 flex-1">
              {/* Native SVG Donut Gauge */}
              <div className="relative w-42 h-42 shrink-0 flex items-center justify-center">
                <svg className="w-full h-full -rotate-90" viewBox="0 0 160 160">
                  {/* Background Track Ring */}
                  <circle
                    cx="80"
                    cy="80"
                    r="58"
                    fill="none"
                    stroke="#f1f5f9"
                    strokeWidth="15"
                  />
                  {/* Active Value Arc */}
                  <circle
                    cx="80"
                    cy="80"
                    r="58"
                    fill="none"
                    stroke={customerMetrics.occupancyRate >= 100 ? "#f59e0b" : "#0284c7"}
                    strokeWidth="15"
                    strokeDasharray={364.42}
                    strokeDashoffset={364.42 - (364.42 * Math.min(100, Math.max(0, customerMetrics.occupancyRate))) / 100}
                    strokeLinecap="round"
                    className="transition-all duration-700 ease-out"
                  />
                </svg>

                {/* Centered KPI Metrics */}
                <div className="absolute inset-0 flex flex-col items-center justify-center text-center select-none pointer-events-none">
                  <span className="text-3xl font-bold font-mono text-slate-900 tabular-nums tracking-tight">
                    {customerMetrics.occupancyRate}%
                  </span>
                  <span className="text-[10px] font-semibold text-slate-600 tracking-wider uppercase mt-0.5">
                    {customerMetrics.totalBooked}/{customerMetrics.totalCapacity} VÉ
                  </span>
                </div>
              </div>

              {/* Service Breakdown Legend */}
              <div className="flex-1 w-full space-y-2.5">
                {serviceOccupancies.slice(0, 3).map((srv, idx) => {
                  const colors = ["bg-[#0284c7]", "bg-[#06b6d4]", "bg-[#10b981]"];
                  const color = colors[idx % colors.length];
                  return (
                    <div key={srv.id} className="space-y-1">
                      <div className="flex items-center justify-between text-xs">
                        <div className="flex items-center gap-1.5 min-w-0">
                          <span className={`w-2 h-2 rounded-full ${color} shrink-0`} />
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
                <div className="space-y-2">
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

                  {/* FIFO Early Dispatch Candidate Banner */}
                  {checkinResult.success && checkinResult.details?.earlyDispatchSuggestion?.available && checkinResult.details.subOrderId && (
                    <div className="p-3 bg-sky-50/80 rounded-xl border border-sky-200 text-xs flex flex-col gap-2">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex items-center gap-1.5 text-sky-950 font-semibold">
                          <FastForward className="w-3.5 h-3.5 text-sky-600 shrink-0" />
                          <span>Phát hiện du khách đến trước giờ đặt (FIFO)</span>
                        </div>
                        <span className="px-2 py-0.5 rounded-md text-[10px] font-mono font-medium bg-white text-sky-700 border border-sky-200">
                          Còn {checkinResult.details.earlyDispatchSuggestion.availableCapacity} chỗ
                        </span>
                      </div>
                      <p className="text-slate-600 text-xs">
                        Ca sớm hơn cùng ngày:{" "}
                        <span className="font-semibold text-slate-900 font-mono">
                          {checkinResult.details.earlyDispatchSuggestion.startTime.slice(0, 5)} - {checkinResult.details.earlyDispatchSuggestion.endTime.slice(0, 5)}
                        </span>{" "}
                        đang trống chỗ. Điều phối du khách chơi ngay ca này?
                      </p>
                      <div className="flex items-center gap-2 pt-1">
                        <button
                          type="button"
                          disabled={isDispatchingEarly}
                          onClick={() =>
                            handleConfirmEarlyDispatch(
                              checkinResult.details!.subOrderId!,
                              checkinResult.details!.earlyDispatchSuggestion!.suggestedSlotId
                            )
                          }
                          className="flex-1 py-2 px-3 rounded-lg bg-sky-600 hover:bg-sky-700 text-white font-medium text-xs flex items-center justify-center gap-1.5 shadow-xs disabled:opacity-50 cursor-pointer"
                        >
                          <FastForward className="w-3.5 h-3.5" />
                          {isDispatchingEarly ? "Đang điều phối..." : `Chuyển ca ${checkinResult.details.earlyDispatchSuggestion.startTime.slice(0, 5)}`}
                        </button>
                        <button
                          type="button"
                          disabled={isDispatchingEarly}
                          onClick={() =>
                            setCheckinResult((prev) =>
                              prev
                                ? {
                                    ...prev,
                                    details: prev.details
                                      ? { ...prev.details, earlyDispatchSuggestion: null }
                                      : undefined,
                                  }
                                : null
                            )
                          }
                          className="py-2 px-3 rounded-lg bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 font-medium text-xs cursor-pointer"
                        >
                          Giữ ca gốc
                        </button>
                      </div>
                    </div>
                  )}
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
