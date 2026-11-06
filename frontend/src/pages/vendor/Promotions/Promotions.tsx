import { useState, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";
import { vendorPromotionService } from "../../../services/vendorPromotionService";
import { Plus, History, Edit2, X, Tag, Search, RotateCcw, Loader2 } from "lucide-react";
import { 
  AtlasPageHeader, 
  AtlasMetricCard, 
  AtlasSegmentedTabs, 
  AtlasBadge, 
  AtlasButton,
  AtlasPagination
} from "../../../components/vendor/atlas/AtlasDesignSystem";

interface PromoCode {
  id: string;
  code: string;
  description: string;
  type: "PERCENTAGE" | "FIXED";
  scope: "VENDOR";
  value: number;
  maxUses: number;
  usedCount: number;
  validFrom: string;
  validTo: string;
  isActive: boolean;
  tag: string;
}

export function Promotions() {
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [selectedVoucherHistory, setSelectedVoucherHistory] = useState<PromoCode | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 4000);
  };

  // Filter tab
  const [searchQuery, setSearchQuery] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 8;
  const [filterStatus, setFilterStatus] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  // Vouchers List
  const [promotions, setPromotions] = useState<PromoCode[]>([]);

  const fetchPromotions = useCallback(async () => {
    try {
      setIsLoading(true);
      const res = await vendorPromotionService.getPromotions(0, 50);
      if (res && res.content && res.content.length > 0) {
        const mapped: PromoCode[] = res.content.map((p) => ({
          id: p.id,
          code: p.code,
          description: p.scope === "VENDOR" ? "Toàn bộ dịch vụ của gian hàng" : "Dịch vụ chỉ định",
          type: p.discountType,
          scope: "VENDOR",
          value: Number(p.discountValue) || 0,
          maxUses: p.maxUses || 100,
          usedCount: p.usedCount || 0,
          validFrom: p.validFrom ? p.validFrom.split("T")[0] : "2024-01-01",
          validTo: p.validTo ? p.validTo.split("T")[0] : "2026-12-31",
          isActive: p.isActive !== false,
          tag: p.scope === "VENDOR" ? "Toàn bộ gian hàng" : "Chỉ định",
        }));
        setPromotions(mapped);
      } else {
        setPromotions([]);
      }
    } catch (err) {
      console.error("[Promotions] Lỗi tải mã khuyến mãi:", err);
      setPromotions([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPromotions();
  }, [fetchPromotions]);

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);

  // Form Fields
  const [code, setCode] = useState("");
  const [description, setDescription] = useState("");
  const [type, setType] = useState<"PERCENTAGE" | "FIXED">("PERCENTAGE");
  const [value, setValue] = useState(10);
  const [maxUses, setMaxUses] = useState(100);
  const [validFrom, setValidFrom] = useState(new Date().toISOString().split("T")[0]);
  const [validTo, setValidTo] = useState("2026-12-31");
  const [isActive, setIsActive] = useState(true);

  // Toggle Active
  const handleToggleActive = async (id: string) => {
    const target = promotions.find((p) => p.id === id);
    if (!target) return;
    const next = !target.isActive;
    try {
      await vendorPromotionService.updatePromotionStatus(id, next);
      setPromotions((prev) =>
        prev.map((p) => (p.id === id ? { ...p, isActive: next } : p))
      );
      showToast(`Đã ${next ? "kích hoạt" : "tạm dừng"} mã ${target.code}!`);
    } catch (err: unknown) {
      console.error("[Promotions] Lỗi cập nhật trạng thái mã:", err);
      showToast("Lỗi: Không thể cập nhật trạng thái mã khuyến mãi từ máy chủ!");
    }
  };

  // Open Create
  const handleOpenCreate = () => {
    setEditingId(null);
    setCode("");
    setDescription("");
    setType("PERCENTAGE");
    setValue(10);
    setMaxUses(100);
    setValidFrom(new Date().toISOString().split("T")[0]);
    setValidTo("2026-12-31");
    setIsActive(true);
    setIsModalOpen(true);
  };

  // Open Edit
  const handleOpenEdit = (p: PromoCode) => {
    setEditingId(p.id);
    setCode(p.code);
    setDescription(p.description);
    setType(p.type);
    setValue(p.value);
    setMaxUses(p.maxUses);
    setValidFrom(p.validFrom);
    setValidTo(p.validTo);
    setIsActive(p.isActive);
    setIsModalOpen(true);
  };

  // Save Voucher
  const handleSaveVoucher = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!code.trim()) {
      showToast("Vui lòng nhập mã khuyến mãi!");
      return;
    }
    if (value <= 0) {
      showToast("Giá trị giảm giá phải lớn hơn 0!");
      return;
    }

    try {
      setIsSaving(true);
      if (editingId) {
        await vendorPromotionService.updatePromotionStatus(editingId, isActive);
        setPromotions((prev) =>
          prev.map((p) =>
            p.id === editingId
              ? {
                  ...p,
                  code: code.trim().toUpperCase(),
                  description,
                  type,
                  value,
                  maxUses,
                  validFrom,
                  validTo,
                  isActive,
                }
              : p
          )
        );
        showToast(`Đã cập nhật mã khuyến mãi ${code}!`);
        setIsModalOpen(false);
      } else {
        const created = await vendorPromotionService.createPromotion({
          code: code.trim().toUpperCase(),
          scope: 'VENDOR',
          discountType: type,
          discountValue: value,
          maxUses: maxUses,
          validFrom: validFrom ? `${validFrom}T00:00:00Z` : undefined,
          validTo: validTo ? `${validTo}T23:59:59Z` : undefined,
          isActive: isActive,
        });
        const newPromo: PromoCode = {
          id: created.id,
          code: created.code,
          description: description || "Toàn bộ gian hàng",
          type: created.discountType,
          value: Number(created.discountValue) || value,
          maxUses: created.maxUses || maxUses,
          usedCount: 0,
          validFrom: created.validFrom ? created.validFrom.split("T")[0] : validFrom,
          validTo: created.validTo ? created.validTo.split("T")[0] : validTo,
          isActive: created.isActive !== false,
          scope: "VENDOR",
          tag: "Mới tạo",
        };
        setPromotions((prev) => [newPromo, ...prev]);
        showToast(`Đã tạo thành công mã khuyến mãi ${newPromo.code}!`);
        setIsModalOpen(false);
      }
    } catch (err: unknown) {
      console.error("[Promotions] Lỗi lưu mã:", err);
      let msg = "Không thể lưu mã khuyến mãi.";
      if (err && typeof err === "object" && "response" in err) {
        const resp = (err as { response?: { data?: { message?: string } } }).response;
        if (resp?.data?.message) msg = resp.data.message;
      }
      showToast(`Lỗi: ${msg}`);
    } finally {
      setIsSaving(false);
    }
  };

  // Filtered
  const filteredPromotions = promotions.filter((p) => {
    if (filterStatus === "ACTIVE" && !p.isActive) return false;
    if (filterStatus === "INACTIVE" && p.isActive) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      return p.code.toLowerCase().includes(q) || p.description.toLowerCase().includes(q);
    }
    return true;
  });

  const totalPages = Math.max(1, Math.ceil(filteredPromotions.length / pageSize));
  const paginatedPromotions = filteredPromotions.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const activeCount = promotions.filter((p) => p.isActive).length;
  const totalUsed = promotions.reduce((acc, p) => acc + p.usedCount, 0);
  const totalMaxUses = promotions.reduce((acc, p) => acc + p.maxUses, 0);
  const redemptionRate = totalMaxUses > 0 ? Math.round((totalUsed / totalMaxUses) * 100) : 0;

  return (
    <div className="w-full max-w-[1400px] mx-auto px-6 py-5 space-y-5">
      {/* 1. Header Toolbar (Atlas Page Header) */}
      <AtlasPageHeader
        title="Quản lý Khuyến mãi & Voucher"
        actions={
          <AtlasButton
            variant="primary"
            size="md"
            onClick={handleOpenCreate}
            icon={<Plus className="w-4 h-4" />}
          >
            Tạo mã mới
          </AtlasButton>
        }
      />

      {/* 2. 3-Card Campaign Performance Strip (Atlas Standard) */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <AtlasMetricCard
          label="Đang mở hiệu lực"
          value={activeCount}
          accent={activeCount > 0 ? "emerald" : undefined}
        />

        <AtlasMetricCard
          label="Lượt khách đã áp dụng"
          value={`${totalUsed} / ${totalMaxUses}`}
        />

        <AtlasMetricCard
          label="Tỷ lệ sử dụng"
          value={`${redemptionRate}%`}
          accent="sky"
        />
      </div>

      {/* Unified Card Container (Atlas Standard Table Layout) */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden flex flex-col">
        {/* Card Head: Segmented Tabs */}
        <div className="p-4 sm:p-5 border-b border-slate-100">
          <AtlasSegmentedTabs<"ALL" | "ACTIVE" | "INACTIVE">
            activeTab={filterStatus}
            onChange={(tab) => { setFilterStatus(tab); setCurrentPage(1); }}
            tabs={[
              { id: "ALL", label: "Tất cả", count: promotions.length },
              { id: "ACTIVE", label: "Đang hoạt động", count: activeCount },
              { id: "INACTIVE", label: "Tạm tắt", count: promotions.length - activeCount },
            ]}
          />
        </div>

        {/* Card Toolbar: Search & Refresh */}
        <div className="p-3.5 border-b border-slate-100 bg-[#fbfcfd] flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3">
          <div className="relative flex-1 min-w-[260px]">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
              placeholder="Tìm theo mã ưu đãi, mô tả..."
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

          <button
            onClick={() => {
              setSearchQuery("");
              setFilterStatus("ALL");
              setCurrentPage(1);
              fetchPromotions();
              showToast("Đã đồng bộ lại danh sách ưu đãi!");
            }}
            className="h-10 w-10 flex items-center justify-center rounded-xl bg-white hover:bg-slate-50 border border-slate-200 text-slate-700 shadow-xs transition-colors cursor-pointer shrink-0"
            title="Làm mới sổ ưu đãi"
          >
            <RotateCcw className="w-4 h-4 text-slate-600" />
          </button>
        </div>

        {/* Table Body */}
        <div className="overflow-x-auto flex-1 scrollbar-thin">
          <table className="w-full text-left border-collapse table-auto text-sm">
            <thead>
              <tr className="bg-[#f8f9fa] text-slate-500 text-xs uppercase tracking-wider font-semibold border-b border-slate-200/80">
                <th className="py-3.5 px-4 w-[200px]">MÃ ƯU ĐÃI</th>
                <th className="py-3.5 px-4 w-[160px]">LOẠI CHIẾT KHẤU</th>
                <th className="py-3.5 px-4 text-right w-[140px]">GIÁ TRỊ</th>
                <th className="py-3.5 px-4 w-[150px]">GIỚI HẠN</th>
                <th className="py-3.5 px-4">HIỆU LỰC</th>
                <th className="py-3.5 px-4 text-center w-[130px]">TRẠNG THÁI</th>
                <th className="py-3.5 px-4 text-center w-[100px]">THAO TÁC</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {isLoading ? (
                Array.from({ length: 3 }).map((_, idx) => (
                  <tr key={`sk-${idx}`} className="animate-pulse">
                    <td className="py-4 px-4"><div className="h-4 w-28 bg-slate-200 rounded" /></td>
                    <td className="py-4 px-3"><div className="h-4 w-24 bg-slate-200 rounded" /></td>
                    <td className="py-4 px-3 text-right"><div className="h-4 w-20 bg-slate-200 rounded ml-auto" /></td>
                    <td className="py-4 px-3"><div className="h-4 w-20 bg-slate-200 rounded" /></td>
                    <td className="py-4 px-3"><div className="h-4 w-28 bg-slate-200 rounded" /></td>
                    <td className="py-4 px-3 text-center"><div className="h-6 w-20 bg-slate-200 rounded-full mx-auto" /></td>
                    <td className="py-4 px-4 text-center"><div className="h-8 w-16 bg-slate-200 rounded-lg mx-auto" /></td>
                  </tr>
                ))
              ) : paginatedPromotions.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-16 text-center">
                    <div className="max-w-sm mx-auto flex flex-col items-center gap-2">
                      <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center text-slate-400">
                        <Tag className="w-6 h-6" />
                      </div>
                      <p className="text-base font-semibold text-slate-900">Chưa có mã khuyến mãi nào</p>
                      <p className="text-xs text-slate-500">
                        Tạo mã giảm giá theo % hoặc số tiền để thu hút du khách đặt tour.
                      </p>
                      <AtlasButton
                        variant="primary"
                        size="sm"
                        onClick={handleOpenCreate}
                        icon={<Plus className="w-4 h-4" />}
                      >
                        Tạo mã mới
                      </AtlasButton>
                    </div>
                  </td>
                </tr>
              ) : (
                paginatedPromotions.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-50/70 transition-colors">
                    <td className="py-3.5 px-4">
                      <div>
                        <span className="font-mono font-bold text-sm text-slate-900 tracking-wide block">
                          {p.code}
                        </span>
                        <span className="text-xs text-slate-500 line-clamp-1">{p.description}</span>
                      </div>
                    </td>
                    <td className="py-3.5 px-3">
                      <span className="inline-flex items-center px-2.5 py-1 rounded-md text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200/70">
                        {p.type === "PERCENTAGE" ? "Phần trăm (%)" : "Số tiền cố định"}
                      </span>
                    </td>
                    <td className="py-3.5 px-3 text-right font-mono font-bold text-sm text-slate-900 tabular-nums">
                      {p.type === "PERCENTAGE" ? `${p.value}%` : `${p.value.toLocaleString("vi-VN")} đ`}
                    </td>
                    <td className="py-3.5 px-3">
                      <div className="flex flex-col gap-1 w-28">
                        <span className="text-xs font-mono font-bold text-slate-800 tabular-nums">{p.usedCount} / {p.maxUses}</span>
                        <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-slate-900 rounded-full"
                            style={{ width: `${Math.min(100, (p.usedCount / p.maxUses) * 100)}%` }}
                          />
                        </div>
                      </div>
                    </td>
                    <td className="py-3.5 px-3 text-xs text-slate-600 font-mono">
                      <div>{p.validFrom} – {p.validTo}</div>
                    </td>
                    <td className="py-3.5 px-3 text-center">
                      <button
                        onClick={() => handleToggleActive(p.id)}
                        className="cursor-pointer"
                      >
                        <AtlasBadge
                          label={p.isActive ? "Đang mở" : "Tạm tắt"}
                          variant={p.isActive ? "success" : "neutral"}
                        />
                      </button>
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      <div className="flex items-center justify-center gap-1.5">
                        <button
                          onClick={() => setSelectedVoucherHistory(p)}
                          className="p-1.5 rounded-lg text-slate-500 hover:text-slate-900 hover:bg-slate-100 transition-colors cursor-pointer"
                          title="Lịch sử áp dụng"
                        >
                          <History className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleOpenEdit(p)}
                          className="p-1.5 rounded-lg text-slate-500 hover:text-slate-900 hover:bg-slate-100 transition-colors cursor-pointer"
                          title="Sửa mã"
                        >
                          <Edit2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Atlas Pagination */}
        <AtlasPagination
          currentPage={currentPage}
          totalPages={totalPages}
          totalItems={filteredPromotions.length}
          pageSize={pageSize}
          onPageChange={setCurrentPage}
        />
      </div>

      {/* USAGE HISTORY MODAL */}
      {selectedVoucherHistory && createPortal(
        <div className="fixed inset-0 z-50 bg-slate-900/40 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-xl border border-slate-200/90 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <History className="w-5 h-5 text-slate-700" />
                <h3 className="text-base font-bold text-slate-900 font-display">
                  Lịch sử áp dụng: #{selectedVoucherHistory.code}
                </h3>
              </div>
              <button
                onClick={() => setSelectedVoucherHistory(null)}
                className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex items-center justify-between p-3.5 rounded-xl bg-slate-50 border border-slate-200/70 text-xs">
              <span className="text-slate-600">Tổng số lượt đã dùng: <strong className="text-slate-900 font-mono tabular-nums">{selectedVoucherHistory.usedCount}</strong> / {selectedVoucherHistory.maxUses}</span>
              <AtlasBadge label="VENDOR" variant="neutral" />
            </div>

            <div className="max-h-60 overflow-y-auto flex flex-col divide-y divide-slate-100 text-xs">
              <div className="py-3 flex items-center justify-between">
                <div>
                  <span className="font-bold text-slate-900 font-mono text-sm">#SUB-89412-01</span>
                  <div className="text-xs text-slate-500 mt-0.5">Khách đặt tour • Dịch vụ biển</div>
                </div>
                <div className="text-right">
                  <span className="font-bold text-emerald-600 font-mono text-sm">-{selectedVoucherHistory.value}%</span>
                  <div className="text-xs text-slate-400 font-mono mt-0.5">2026-11-02</div>
                </div>
              </div>
            </div>

            <div className="flex justify-end pt-3 border-t border-slate-100">
              <AtlasButton
                variant="outline"
                size="sm"
                onClick={() => setSelectedVoucherHistory(null)}
              >
                Đóng
              </AtlasButton>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* CREATE / EDIT PROMOTION MODAL */}
      {isModalOpen && createPortal(
        <div className="fixed inset-0 z-50 bg-slate-900/40 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-xl border border-slate-200/90 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <h3 className="text-base font-bold text-slate-900 font-display">
                {editingId ? "Chỉnh sửa mã khuyến mãi" : "Tạo mã khuyến mãi mới"}
              </h3>
              <button
                onClick={() => setIsModalOpen(false)}
                className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSaveVoucher} className="space-y-4 text-sm">
              <div>
                <label className="text-xs text-slate-700 font-semibold block mb-1">
                  Mã Code (Chữ hoa &amp; số, không khoảng trắng) *
                </label>
                <input
                  type="text"
                  required
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  placeholder="VÍ DỤ: HELLOSUMMER"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-white border border-slate-200 text-sm font-mono font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900"
                />
              </div>

              <div>
                <label className="text-xs text-slate-700 font-semibold block mb-1">
                  Mô tả ưu đãi
                </label>
                <input
                  type="text"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Giảm giá tour ca sáng cho nhóm bạn"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-white border border-slate-200 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-slate-700 font-semibold block mb-1">
                    Loại giảm giá
                  </label>
                  <select
                    value={type}
                    onChange={(e) => setType(e.target.value as typeof type)}
                    className="w-full h-11 px-3.5 rounded-xl bg-white border border-slate-200 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900 cursor-pointer"
                  >
                    <option value="PERCENTAGE">Theo Phần trăm (%)</option>
                    <option value="FIXED">Số tiền cố định (VNĐ)</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs text-slate-700 font-semibold block mb-1">
                    Giá trị giảm ({type === "PERCENTAGE" ? "%" : "VNĐ"}) *
                  </label>
                  <input
                    type="number"
                    required
                    min={1}
                    value={value}
                    onChange={(e) => setValue(Number(e.target.value))}
                    className="w-full h-11 px-3.5 rounded-xl bg-white border border-slate-200 text-sm font-mono font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs text-slate-700 font-semibold block mb-1">
                  Số lượng mã tối đa (lượt) *
                </label>
                <input
                  type="number"
                  required
                  min={1}
                  value={maxUses}
                  onChange={(e) => setMaxUses(Number(e.target.value))}
                  className="w-full h-11 px-3.5 rounded-xl bg-white border border-slate-200 text-sm font-mono text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-slate-700 font-semibold block mb-1">
                    Có hiệu lực từ
                  </label>
                  <input
                    type="date"
                    required
                    value={validFrom}
                    onChange={(e) => setValidFrom(e.target.value)}
                    className="w-full h-11 px-3.5 rounded-xl bg-white border border-slate-200 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900"
                  />
                </div>
                <div>
                  <label className="text-xs text-slate-700 font-semibold block mb-1">
                    Hết hạn ngày
                  </label>
                  <input
                    type="date"
                    required
                    value={validTo}
                    onChange={(e) => setValidTo(e.target.value)}
                    className="w-full h-11 px-3.5 rounded-xl bg-white border border-slate-200 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
                <AtlasButton
                  variant="outline"
                  size="sm"
                  disabled={isSaving}
                  onClick={() => setIsModalOpen(false)}
                >
                  Hủy bỏ
                </AtlasButton>
                <AtlasButton
                  type="submit"
                  variant="primary"
                  size="sm"
                  disabled={isSaving}
                  icon={isSaving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : undefined}
                >
                  {editingId ? "Cập nhật mã" : "Lưu mã mới"}
                </AtlasButton>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}

      {/* Toast Alert */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 bg-slate-900 text-white px-4 py-2.5 rounded-xl shadow-lg flex items-center gap-2 text-sm">
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  );
}
