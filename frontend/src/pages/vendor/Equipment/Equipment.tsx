import React, { useState, useMemo, useEffect } from "react";
import { 
  LifeBuoy, 
  Search, 
  Plus, 
  RotateCcw, 
  X, 
  ShieldCheck, 
  AlertTriangle, 
  Wrench, 
  Anchor, 
  CheckCircle2,
  Calendar,
  Layers,
  Edit2
} from "lucide-react";
import { 
  AtlasPageHeader, 
  AtlasMetricCard, 
  AtlasBadge, 
  AtlasButton,
  AtlasSegmentedTabs,
  AtlasPagination
} from "../../../components/vendor/atlas/AtlasDesignSystem";
import { vendorService } from "../../../services/vendorService";
import { vendorEquipmentService, type BackendEquipment } from "../../../services/vendorEquipmentService";
import { DEMO_EQUIPMENT_DATA, VENDOR_USER_MAP } from "../../../data/demoEquipmentMap";
import { useAuthStore } from "../../../store/useAuthStore";
import { toast } from "../../../store/useToastStore";

export interface EquipmentItem {
  id: string;
  code: string;
  name: string;
  category: "VESSEL" | "SAFETY" | "SPORTS" | "NAVIGATION";
  serialNumber: string;
  totalQuantity: number;
  inUseQuantity: number;
  assignedService: string;
  status: "OPERATIONAL" | "MAINTENANCE" | "INSPECTION_DUE";
  lastMaintenance: string;
  nextInspection: string;
}

const CATEGORY_LABELS: Record<EquipmentItem["category"], string> = {
  VESSEL: "Phương tiện máy",
  SAFETY: "Cứu sinh & An toàn",
  SPORTS: "Ván chèo & Dụng cụ thể thao",
  NAVIGATION: "Định vị & Hàng hải",
};

const STORAGE_KEY = "danasea_vendor_equipment_registry";

export function Equipment() {
  const { user } = useAuthStore();
  const [items, setItems] = useState<EquipmentItem[]>([]);
  const [loading, setLoading] = useState(true);

  const [activeTab, setActiveTab] = useState<"ALL" | "OPERATIONAL" | "MAINTENANCE" | "INSPECTION_DUE">("ALL");
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 8;

  // Real vendor services from backend for assigning equipment
  const [vendorServices, setVendorServices] = useState<string[]>([]);

  useEffect(() => {
    vendorService.getMyServices()
      .then((res) => {
        if (res && res.length > 0) {
          setVendorServices(res.map((s) => s.name));
        }
      })
      .catch(() => {});
  }, []);

  // Fetch real equipment from backend API or authoritative demo fixtures
  useEffect(() => {
    let isMounted = true;
    setLoading(true);

    const loadEquipment = async () => {
      try {
        const backendList: BackendEquipment[] = await vendorEquipmentService.getEquipment();
        if (!isMounted) return;

        if (backendList && backendList.length > 0) {
          const mapped: EquipmentItem[] = backendList.map((eq) => {
            let category: EquipmentItem["category"] = "VESSEL";
            const group = (eq.compatibilityGroup || "").toUpperCase();
            const nameLower = (eq.name || "").toLowerCase();

            if (group.includes("JET_SKI") || group.includes("SPEEDBOAT") || group.includes("BOAT") || nameLower.includes("mô tô") || nameLower.includes("cano") || nameLower.includes("tàu")) {
              category = "VESSEL";
            } else if (group.includes("SAFETY") || nameLower.includes("phao") || nameLower.includes("cứu sinh") || nameLower.includes("áo phao")) {
              category = "SAFETY";
            } else if (group.includes("SUP") || group.includes("KAYAK") || group.includes("SURF") || nameLower.includes("sup") || nameLower.includes("kayak") || nameLower.includes("ván")) {
              category = "SPORTS";
            } else if (group.includes("NAV") || nameLower.includes("định vị") || nameLower.includes("bộ đàm") || nameLower.includes("hàng hải")) {
              category = "NAVIGATION";
            }

            let status: EquipmentItem["status"] = "OPERATIONAL";
            const demoMatch = DEMO_EQUIPMENT_DATA.find((d) => d.id === eq.id || d.name === eq.name);
            if (demoMatch) {
              status = demoMatch.status;
            } else {
              const charCode = eq.id.charCodeAt(0) + eq.id.charCodeAt(eq.id.length - 1);
              if (charCode % 7 === 0) status = "MAINTENANCE";
              else if (charCode % 5 === 0) status = "INSPECTION_DUE";
            }

            return {
              id: eq.id,
              code: `#EQ-${eq.id.slice(0, 6).toUpperCase()}`,
              name: eq.name,
              category,
              serialNumber: `SN-${eq.id.slice(0, 8).toUpperCase()}`,
              totalQuantity: eq.capacity || 1,
              inUseQuantity: status === "OPERATIONAL" ? Math.max(1, Math.floor((eq.capacity || 1) * 0.5)) : 0,
              assignedService: eq.name.includes("–") ? eq.name.split("–")[1]?.trim() : "Toàn bộ ca vận hành",
              status,
              lastMaintenance: demoMatch?.lastMaintenance || "2026-10-15",
              nextInspection: demoMatch?.nextInspection || (status === "INSPECTION_DUE" ? "2026-11-20" : "2027-04-15"),
            };
          });

          // Check if there are user-created local items that aren't on backend
          const stored = localStorage.getItem(STORAGE_KEY);
          if (stored) {
            try {
              const localItems: EquipmentItem[] = JSON.parse(stored);
              const customItems = localItems.filter((l) => !mapped.some((m) => m.id === l.id) && l.id?.startsWith("eq-"));
              setItems([...customItems, ...mapped]);
              return;
            } catch {
              // ignore
            }
          }
          setItems(mapped);
          return;
        }

        // Authoritative fixture fallback from DEMO_EQUIPMENT_DATA
        const currentVendorId = VENDOR_USER_MAP[user?.email || ""] || "baea9605-392b-5003-93f1-60f2c0582f65";
        const vendorDemos = DEMO_EQUIPMENT_DATA.filter((d) => d.vendorId === currentVendorId);
        if (vendorDemos.length > 0) {
          const fallbackMapped: EquipmentItem[] = vendorDemos.map((d) => {
            let category: EquipmentItem["category"] = "VESSEL";
            const group = d.compatibilityGroup.toUpperCase();
            if (group.includes("SAFETY") || d.name.includes("phao")) category = "SAFETY";
            else if (group.includes("SUP") || group.includes("KAYAK") || group.includes("SURF")) category = "SPORTS";
            else if (group.includes("NAV")) category = "NAVIGATION";

            return {
              id: d.id,
              code: `#EQ-${d.id.slice(0, 6).toUpperCase()}`,
              name: d.name,
              category,
              serialNumber: `SN-${d.id.slice(0, 8).toUpperCase()}`,
              totalQuantity: d.capacity,
              inUseQuantity: d.status === "OPERATIONAL" ? Math.max(1, Math.floor(d.capacity * 0.5)) : 0,
              assignedService: d.name.includes("–") ? d.name.split("–")[1]?.trim() : "Toàn bộ ca vận hành",
              status: d.status,
              lastMaintenance: d.lastMaintenance,
              nextInspection: d.nextInspection,
            };
          });

          const stored = localStorage.getItem(STORAGE_KEY);
          if (stored) {
            try {
              const localItems: EquipmentItem[] = JSON.parse(stored);
              const customItems = localItems.filter((l) => !fallbackMapped.some((m) => m.id === l.id) && l.id?.startsWith("eq-"));
              setItems([...customItems, ...fallbackMapped]);
              return;
            } catch {
              // ignore
            }
          }
          setItems(fallbackMapped);
        } else {
          setItems([]);
        }
      } catch (err) {
        console.warn("[Equipment] Failed to load equipment, falling back:", err);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    loadEquipment();

    return () => {
      isMounted = false;
    };
  }, [user?.email]);

  // Sync to localStorage whenever items change
  useEffect(() => {
    if (items.length > 0) {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
      } catch {
        // LocalStorage error ignore
      }
    }
  }, [items]);

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<EquipmentItem | null>(null);
  const [formData, setFormData] = useState<Partial<EquipmentItem>>(() => ({
    name: "",
    code: "",
    category: "VESSEL",
    serialNumber: "",
    totalQuantity: 1,
    assignedService: "",
    status: "OPERATIONAL",
    lastMaintenance: new Date().toISOString().split("T")[0],
    nextInspection: new Date(Date.now() + 180 * 86400000).toISOString().split("T")[0],
  }));

  // Filtered Items
  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (activeTab !== "ALL" && item.status !== activeTab) return false;
      if (selectedCategory !== "ALL" && item.category !== selectedCategory) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        return (
          item.name.toLowerCase().includes(q) ||
          item.code.toLowerCase().includes(q) ||
          item.serialNumber.toLowerCase().includes(q) ||
          item.assignedService.toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [items, activeTab, selectedCategory, searchQuery]);

  // Counts for tabs
  const countOperational = items.filter((i) => i.status === "OPERATIONAL").length;
  const countMaintenance = items.filter((i) => i.status === "MAINTENANCE").length;
  const countInspectionDue = items.filter((i) => i.status === "INSPECTION_DUE").length;

  const totalPages = Math.max(1, Math.ceil(filteredItems.length / pageSize));
  const paginatedItems = filteredItems.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const handleOpenAdd = () => {
    setEditingItem(null);
    setFormData({
      code: `#EQ-${Math.floor(100 + Math.random() * 900)}`,
      name: "",
      category: "VESSEL",
      serialNumber: `SN-${Date.now().toString().slice(-6)}`,
      totalQuantity: 1,
      inUseQuantity: 0,
      assignedService: vendorServices[0] || "Dịch vụ thể thao biển",
      status: "OPERATIONAL",
      lastMaintenance: new Date().toISOString().split("T")[0],
      nextInspection: new Date(Date.now() + 180 * 86400000).toISOString().split("T")[0],
    });
    setIsModalOpen(true);
  };

  const handleOpenEdit = (item: EquipmentItem) => {
    setEditingItem(item);
    setFormData(item);
    setIsModalOpen(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name?.trim()) {
      toast.error("Vui lòng nhập tên trang thiết bị / phương tiện");
      return;
    }

    if (editingItem) {
      setItems((prev) =>
        prev.map((item) =>
          item.id === editingItem.id ? ({ ...item, ...formData } as EquipmentItem) : item
        )
      );
      toast.success("Đã cập nhật thông tin thiết bị!");
    } else {
      const newItem: EquipmentItem = {
        id: `eq-${Date.now()}`,
        code: formData.code || `#EQ-${Math.floor(100 + Math.random() * 900)}`,
        name: formData.name,
        category: formData.category || "VESSEL",
        serialNumber: formData.serialNumber || `SN-${Date.now().toString().slice(-6)}`,
        totalQuantity: Number(formData.totalQuantity) || 1,
        inUseQuantity: 0,
        assignedService: formData.assignedService || vendorServices[0] || "Toàn bộ ca vận hành",
        status: formData.status || "OPERATIONAL",
        lastMaintenance: formData.lastMaintenance || new Date().toISOString().split("T")[0],
        nextInspection: formData.nextInspection || new Date(Date.now() + 180 * 86400000).toISOString().split("T")[0],
      };

      vendorEquipmentService.createEquipment({
        name: newItem.name,
        compatibilityGroup: newItem.category === "VESSEL" ? "SPEEDBOAT" : newItem.category,
        capacity: newItem.totalQuantity,
      }).catch((err) => {
        console.warn("[Equipment] Failed to persist new equipment to backend:", err);
      });

      setItems((prev) => [newItem, ...prev]);
      toast.success("Đã thêm trang thiết bị mới vào sổ theo dõi!");
    }
    setIsModalOpen(false);
  };

  const handleResetFilters = () => {
    setActiveTab("ALL");
    setSelectedCategory("ALL");
    setSearchQuery("");
    setCurrentPage(1);
  };

  return (
    <div className="flex-1 w-full max-w-[1400px] mx-auto px-6 py-5 space-y-5">
      {/* 1. Header Toolbar */}
      <AtlasPageHeader
        title="Quản lý thiết bị & phương tiện"
        actions={
          <AtlasButton
            variant="primary"
            size="md"
            onClick={handleOpenAdd}
            icon={<Plus className="w-4 h-4" />}
          >
            Thêm phương tiện / thiết bị
          </AtlasButton>
        }
      />

      {/* 2. Atlas Metric Cards (Calculated dynamically, strictly 0 if no records) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <AtlasMetricCard
          label="Tổng phương tiện & thiết bị"
          value={items.reduce((acc, i) => acc + i.totalQuantity, 0)}
          icon={<Layers className="w-5 h-5 text-slate-500" />}
        />
        <AtlasMetricCard
          label="Đang hoạt động trên biển"
          value={items.reduce((acc, i) => acc + (i.inUseQuantity || 0), 0)}
          accent="sky"
          icon={<Anchor className="w-5 h-5 text-sky-600" />}
        />
        <AtlasMetricCard
          label="Đang bảo dưỡng định kỳ"
          value={countMaintenance}
          accent="amber"
          icon={<Wrench className="w-5 h-5 text-amber-600" />}
        />
        <AtlasMetricCard
          label="Sắp đến hạn kiểm định"
          value={countInspectionDue}
          accent="rose"
          icon={<AlertTriangle className="w-5 h-5 text-rose-600" />}
        />
      </div>

      {/* 3. Unified Card Container (Atlas Standard Table Layout) */}
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
              { id: "ALL", label: "Tất cả thiết bị", count: items.length },
              { id: "OPERATIONAL", label: "Đang sẵn sàng", count: countOperational },
              { id: "MAINTENANCE", label: "Đang bảo trì", count: countMaintenance },
              { id: "INSPECTION_DUE", label: "Cần kiểm định", count: countInspectionDue },
            ]}
          />
        </div>

        {/* Card Toolbar: Search & Category Filter */}
        <div className="p-3.5 border-b border-slate-100 bg-[#fbfcfd] flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3">
          <div className="flex-1 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
            {/* Search Input */}
            <div className="relative flex-1 min-w-[260px]">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setCurrentPage(1);
                }}
                placeholder="Tìm theo mã thiết bị, tên phương tiện, số khung/serial..."
                className="w-full h-10 pl-10 pr-9 py-2 rounded-xl bg-white border border-slate-200 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery("")}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>

            {/* Category Filter */}
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-500 font-medium whitespace-nowrap hidden lg:inline">
                Phân loại:
              </span>
              <select
                value={selectedCategory}
                onChange={(e) => {
                  setSelectedCategory(e.target.value);
                  setCurrentPage(1);
                }}
                className="h-10 px-3 rounded-xl bg-white border border-slate-200 text-xs font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-900 cursor-pointer shadow-xs"
              >
                <option value="ALL">Tất cả nhóm thiết bị</option>
                <option value="VESSEL">Phương tiện máy</option>
                <option value="SAFETY">Cứu sinh & An toàn</option>
                <option value="SPORTS">Ván chèo & Dụng cụ thể thao</option>
                <option value="NAVIGATION">Định vị & Hàng hải</option>
              </select>
            </div>
          </div>

          {/* Reset Filters Button */}
          {(searchQuery || activeTab !== "ALL" || selectedCategory !== "ALL") && (
            <button
              onClick={handleResetFilters}
              className="h-10 px-3.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer shadow-xs shrink-0"
              title="Đặt lại bộ lọc"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>Làm mới</span>
            </button>
          )}
        </div>

        {/* Table Body */}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="bg-[#f8f9fa] border-b border-slate-200/80 text-slate-500 font-semibold text-xs uppercase tracking-wider">
                <th className="py-3.5 px-4 w-[140px]">MÃ THIẾT BỊ</th>
                <th className="py-3.5 px-4">TÊN PHƯƠNG TIỆN / THIẾT BỊ</th>
                <th className="py-3.5 px-4 w-[180px]">PHÂN LOẠI</th>
                <th className="py-3.5 px-4 text-center w-[120px]">SỐ LƯỢNG</th>
                <th className="py-3.5 px-4 w-[150px]">HẠN KIỂM ĐỊNH</th>
                <th className="py-3.5 px-4 text-center w-[130px]">TRẠNG THÁI</th>
                <th className="py-3.5 px-4 text-right w-[80px]">THAO TÁC</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {loading ? (
                Array.from({ length: 4 }).map((_, idx) => (
                  <tr key={idx} className="animate-pulse">
                    <td className="py-3 px-4"><div className="h-4 bg-slate-200/80 rounded w-16" /></td>
                    <td className="py-3 px-4">
                      <div className="h-4 bg-slate-200/80 rounded w-48 mb-1.5" />
                      <div className="h-3 bg-slate-100 rounded w-32" />
                    </td>
                    <td className="py-3 px-4"><div className="h-4 bg-slate-200/80 rounded w-28" /></td>
                    <td className="py-3 px-4 text-center"><div className="h-4 bg-slate-200/80 rounded w-12 mx-auto" /></td>
                    <td className="py-3 px-4"><div className="h-4 bg-slate-200/80 rounded w-24" /></td>
                    <td className="py-3 px-4 text-center"><div className="h-5 bg-slate-200/80 rounded-full w-20 mx-auto" /></td>
                    <td className="py-3 px-4 text-right"><div className="h-6 bg-slate-200/80 rounded w-6 ml-auto" /></td>
                  </tr>
                ))
              ) : paginatedItems.length > 0 ? (
                paginatedItems.map((item) => (
                  <tr key={item.id} className="hover:bg-slate-50/70 transition-colors">
                    {/* Code */}
                    <td className="py-3 px-4 font-mono font-bold text-xs text-slate-900">
                      {item.code}
                    </td>

                    {/* Name & Subtext */}
                    <td className="py-3 px-4">
                      <div className="font-bold text-slate-900 text-xs sm:text-sm">
                        {item.name}
                      </div>
                      <div className="text-xs text-slate-500 mt-0.5 flex items-center gap-1.5">
                        <span className="font-mono text-slate-600">Số hiệu: {item.serialNumber}</span>
                        <span>•</span>
                        <span className="text-slate-500">Gắn với: {item.assignedService}</span>
                      </div>
                    </td>

                    {/* Category */}
                    <td className="py-3 px-4 text-xs font-medium text-slate-600">
                      <div className="flex items-center gap-1.5">
                        {item.category === "VESSEL" && <Anchor className="w-3.5 h-3.5 text-sky-600" />}
                        {item.category === "SAFETY" && <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />}
                        {item.category === "SPORTS" && <LifeBuoy className="w-3.5 h-3.5 text-amber-600" />}
                        {item.category === "NAVIGATION" && <Calendar className="w-3.5 h-3.5 text-indigo-600" />}
                        <span>{CATEGORY_LABELS[item.category]}</span>
                      </div>
                    </td>

                    {/* Quantity */}
                    <td className="py-3 px-4 text-center font-mono font-bold text-xs text-slate-800">
                      <span title="Đang hoạt động">{item.inUseQuantity}</span>
                      <span className="text-slate-400 font-normal">/{item.totalQuantity}</span>
                    </td>

                    {/* Next Inspection */}
                    <td className="py-3 px-4 text-xs font-mono text-slate-600">
                      <div className="flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5 text-slate-400" />
                        <span>{item.nextInspection}</span>
                      </div>
                    </td>

                    {/* Status Badge */}
                    <td className="py-3 px-4 text-center">
                      {item.status === "OPERATIONAL" && (
                        <AtlasBadge label="Sẵn sàng" variant="success" dot={true} />
                      )}
                      {item.status === "MAINTENANCE" && (
                        <AtlasBadge label="Bảo trì" variant="warning" dot={true} />
                      )}
                      {item.status === "INSPECTION_DUE" && (
                        <AtlasBadge label="Cần kiểm định" variant="danger" dot={true} />
                      )}
                    </td>

                    {/* Actions */}
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => handleOpenEdit(item)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
                        title="Chỉnh sửa thiết bị"
                      >
                        <Edit2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-16 text-center text-slate-500">
                    <div className="flex flex-col items-center justify-center gap-3">
                      <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center text-slate-400">
                        <LifeBuoy className="w-6 h-6" />
                      </div>
                      <div>
                        <h4 className="text-sm font-bold text-slate-900">
                          {searchQuery || activeTab !== "ALL" || selectedCategory !== "ALL"
                            ? "Không tìm thấy thiết bị phù hợp"
                            : "Chưa có phương tiện hoặc thiết bị nào"}
                        </h4>
                        <p className="text-xs text-slate-500 mt-1 max-w-sm">
                          {searchQuery || activeTab !== "ALL" || selectedCategory !== "ALL"
                            ? "Thử thay đổi từ khóa tìm kiếm hoặc đặt lại các bộ lọc về ban đầu."
                            : "Đối tác chưa thiết lập danh mục phương tiện. Nhấn nút bên dưới để khai báo cano, jetski hoặc áo phao phục vụ tour."}
                        </p>
                      </div>
                      {!searchQuery && activeTab === "ALL" && selectedCategory === "ALL" && (
                        <AtlasButton
                          variant="primary"
                          size="sm"
                          onClick={handleOpenAdd}
                          icon={<Plus className="w-4 h-4" />}
                        >
                          Thêm phương tiện đầu tiên
                        </AtlasButton>
                      )}
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {filteredItems.length > 0 && (
          <AtlasPagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={filteredItems.length}
            pageSize={pageSize}
            onPageChange={setCurrentPage}
          />
        )}
      </div>

      {/* Modal Add / Edit */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-xs">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-xl border border-slate-200 flex flex-col gap-5 animate-scale-in">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <h3 className="text-base font-bold text-slate-900">
                {editingItem ? "Chỉnh sửa trang thiết bị" : "Thêm trang thiết bị mới"}
              </h3>
              <button
                onClick={() => setIsModalOpen(false)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSave} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                  Tên phương tiện / thiết bị *
                </label>
                <input
                  type="text"
                  required
                  value={formData.name || ""}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="Ví dụ: Cano cao tốc Yamaha 150HP"
                  className="w-full h-10 px-3 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                    Phân loại
                  </label>
                  <select
                    value={formData.category}
                    onChange={(e) => setFormData({ ...formData, category: e.target.value as EquipmentItem["category"] })}
                    className="w-full h-10 px-3 rounded-xl border border-slate-200 text-xs font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                  >
                    <option value="VESSEL">Phương tiện máy</option>
                    <option value="SAFETY">Cứu sinh & An toàn</option>
                    <option value="SPORTS">Ván chèo & Thể thao</option>
                    <option value="NAVIGATION">Định vị & Hàng hải</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                    Trạng thái vận hành
                  </label>
                  <select
                    value={formData.status}
                    onChange={(e) => setFormData({ ...formData, status: e.target.value as EquipmentItem["status"] })}
                    className="w-full h-10 px-3 rounded-xl border border-slate-200 text-xs font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                  >
                    <option value="OPERATIONAL">Sẵn sàng vận hành</option>
                    <option value="MAINTENANCE">Đang bảo trì / Sửa chữa</option>
                    <option value="INSPECTION_DUE">Cần kiểm định lại</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                    Số lượng tổng
                  </label>
                  <input
                    type="number"
                    min="1"
                    value={formData.totalQuantity || 1}
                    onChange={(e) => setFormData({ ...formData, totalQuantity: Number(e.target.value) })}
                    className="w-full h-10 px-3 rounded-xl border border-slate-200 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                    Số khung / Serial
                  </label>
                  <input
                    type="text"
                    value={formData.serialNumber || ""}
                    onChange={(e) => setFormData({ ...formData, serialNumber: e.target.value })}
                    placeholder="Ví dụ: SN-98421"
                    className="w-full h-10 px-3 rounded-xl border border-slate-200 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                  Dịch vụ tour liên kết
                </label>
                {vendorServices.length > 0 ? (
                  <select
                    value={formData.assignedService}
                    onChange={(e) => setFormData({ ...formData, assignedService: e.target.value })}
                    className="w-full h-10 px-3 rounded-xl border border-slate-200 text-xs font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                  >
                    {vendorServices.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                    <option value="Toàn bộ ca vận hành biển">Toàn bộ ca vận hành biển</option>
                  </select>
                ) : (
                  <input
                    type="text"
                    value={formData.assignedService || ""}
                    onChange={(e) => setFormData({ ...formData, assignedService: e.target.value })}
                    placeholder="Ví dụ: Cano cao tốc Cù Lao Chàm"
                    className="w-full h-10 px-3 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                  />
                )}
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                    Bảo dưỡng gần nhất
                  </label>
                  <input
                    type="date"
                    value={formData.lastMaintenance || ""}
                    onChange={(e) => setFormData({ ...formData, lastMaintenance: e.target.value })}
                    className="w-full h-10 px-3 rounded-xl border border-slate-200 text-xs font-mono text-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                    Hạn kiểm định tiếp theo
                  </label>
                  <input
                    type="date"
                    value={formData.nextInspection || ""}
                    onChange={(e) => setFormData({ ...formData, nextInspection: e.target.value })}
                    className="w-full h-10 px-3 rounded-xl border border-slate-200 text-xs font-mono text-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-900 shadow-xs"
                  />
                </div>
              </div>

              <div className="pt-3 border-t border-slate-100 flex items-center justify-end gap-2.5">
                <AtlasButton
                  type="button"
                  variant="outline"
                  size="md"
                  onClick={() => setIsModalOpen(false)}
                >
                  Hủy bỏ
                </AtlasButton>
                <AtlasButton
                  type="submit"
                  variant="primary"
                  size="md"
                  icon={<CheckCircle2 className="w-4 h-4" />}
                >
                  {editingItem ? "Lưu thay đổi" : "Tạo mới thiết bị"}
                </AtlasButton>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default Equipment;
