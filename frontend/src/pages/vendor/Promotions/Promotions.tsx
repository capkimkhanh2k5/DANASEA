import { useState } from "react";
import { createPortal } from "react-dom";

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
  const [filterStatus, setFilterStatus] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");

  // Vouchers List
  const [promotions, setPromotions] = useState<PromoCode[]>([
    {
      id: "promo-1",
      code: "SUPHE2024",
      description: "Chèo SUP ngắm bình minh mùa hè",
      type: "PERCENTAGE",
      scope: "VENDOR",
      value: 15,
      maxUses: 100,
      usedCount: 45,
      validFrom: "2024-06-01",
      validTo: "2024-10-31",
      isActive: true,
      tag: "Hiệu lực mùa hè",
    },
    {
      id: "promo-2",
      code: "OCEAN50K",
      description: "Giảm thẳng toàn bộ gói lặn biển",
      type: "FIXED",
      scope: "VENDOR",
      value: 50000,
      maxUses: 200,
      usedCount: 82,
      validFrom: "2024-05-01",
      validTo: "2024-12-31",
      isActive: true,
      tag: "Ưu đãi lặn ngắm san hô",
    },
    {
      id: "promo-3",
      code: "BINHMINH10",
      description: "Giảm 10% ca sớm 05:00",
      type: "PERCENTAGE",
      scope: "VENDOR",
      value: 10,
      maxUses: 50,
      usedCount: 15,
      validFrom: "2024-08-01",
      validTo: "2024-11-30",
      isActive: true,
      tag: "Dành riêng ca sáng sớm",
    },
    {
      id: "promo-4",
      code: "TETBIEN2024",
      description: "Tri ân mùa cao điểm đầu năm",
      type: "FIXED",
      scope: "VENDOR",
      value: 100000,
      maxUses: 50,
      usedCount: 50,
      validFrom: "2024-01-01",
      validTo: "2024-03-31",
      isActive: false,
      tag: "Đã hết hạn",
    },
  ]);

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);

  // Form Fields
  const [code, setCode] = useState("SUNRISE20");
  const [description, setDescription] = useState("Giảm giá đặc biệt tour chèo SUP");
  const [type, setType] = useState<"PERCENTAGE" | "FIXED">("PERCENTAGE");
  const [value, setValue] = useState(15);
  const [maxUses, setMaxUses] = useState(100);
  const [validFrom, setValidFrom] = useState("2024-10-01");
  const [validTo, setValidTo] = useState("2024-12-31");
  const [isActive, setIsActive] = useState(true);

  // Toggle Active
  const handleToggleActive = (id: string) => {
    setPromotions((prev) =>
      prev.map((p) => {
        if (p.id === id) {
          const next = !p.isActive;
          showToast(`Đã ${next ? "kích hoạt" : "tạm dừng"} mã ${p.code}!`);
          return { ...p, isActive: next };
        }
        return p;
      })
    );
  };

  // Open Create
  const handleOpenCreate = () => {
    setEditingId(null);
    setCode("");
    setDescription("");
    setType("PERCENTAGE");
    setValue(10);
    setMaxUses(100);
    setValidFrom("2024-10-24");
    setValidTo("2024-12-31");
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
  const handleSaveVoucher = (e: React.FormEvent) => {
    e.preventDefault();

    if (!code.trim()) {
      showToast("Vui lòng nhập mã khuyến mãi!");
      return;
    }

    if (type === "PERCENTAGE" && (value <= 0 || value > 100)) {
      showToast("Lỗi: Mã giảm giá phần trăm phải từ 1% đến 100%!");
      return;
    }

    if (type === "FIXED" && value <= 0) {
      showToast("Lỗi: Số tiền giảm phải lớn hơn 0 VNĐ!");
      return;
    }

    if (validTo <= validFrom) {
      showToast("Lỗi: Ngày kết thúc phải sau ngày bắt đầu!");
      return;
    }

    if (editingId) {
      // Edit
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
      showToast(`Đã cập nhật thành công mã ${code.toUpperCase()}!`);
    } else {
      // Create new
      const exists = promotions.some(
        (p) => p.code.toLowerCase() === code.trim().toLowerCase()
      );
      if (exists) {
        showToast(`Mã "${code}" đã tồn tại trên hệ thống của bạn!`);
        return;
      }

      const newPromo: PromoCode = {
        id: `promo-${Date.now()}`,
        code: code.trim().toUpperCase(),
        description: description || "Khuyến mãi đối tác",
        type,
        value,
        maxUses,
        usedCount: 0,
        validFrom,
        validTo,
        isActive,
        scope: "VENDOR",
        tag: "Mới tạo",
      };
      setPromotions((prev) => [newPromo, ...prev]);
      showToast(`Đã tạo thành công mã khuyến mãi ${newPromo.code}!`);
    }

    setIsModalOpen(false);
  };

  // Filtered
  const filteredPromotions = promotions.filter((p) => {
    if (filterStatus === "ACTIVE") return p.isActive;
    if (filterStatus === "INACTIVE") return !p.isActive;
    return true;
  });

  const activeCount = promotions.filter((p) => p.isActive).length;
  const totalUsed = promotions.reduce((sum, p) => sum + p.usedCount, 0);

  return (
    <div className="w-full pt-4 pb-20 bg-background min-h-screen">
      {/* Toast Alert */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 bg-on-surface text-surface px-5 py-3 rounded-2xl shadow-2xl flex items-center gap-3 border border-outline-variant/30 animate-bounce">
          <span className="material-symbols-outlined text-primary text-[22px]">check_circle</span>
          <span className="font-label-md text-label-md font-medium">{toastMessage}</span>
        </div>
      )}

      <div className="w-full px-6 py-4 space-y-3.5">
        {/* Header Block */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-200">
          <div>
            <h1 className="text-xl md:text-2xl font-bold text-slate-900 tracking-tight">
              Khuyến mãi
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleOpenCreate}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-primary text-white text-xs font-semibold hover:bg-primary-container transition-all cursor-pointer shadow-xs"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              <span>Tạo mã mới</span>
            </button>
          </div>
        </div>

        {/* Filter Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-nowrap scrollbar-none">
          <button
            onClick={() => setFilterStatus("ALL")}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold cursor-pointer transition-colors ${
              filterStatus === "ALL" ? "bg-primary text-white shadow-xs" : "bg-white text-slate-700 hover:bg-slate-50 border border-slate-200"
            }`}
          >
            Tất cả ({promotions.length})
          </button>
          <button
            onClick={() => setFilterStatus("ACTIVE")}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold cursor-pointer transition-colors ${
              filterStatus === "ACTIVE" ? "bg-primary text-white shadow-xs" : "bg-white text-slate-700 hover:bg-slate-50 border border-slate-200"
            }`}
          >
            Đang hoạt động ({activeCount})
          </button>
          <button
            onClick={() => setFilterStatus("INACTIVE")}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold cursor-pointer transition-colors ${
              filterStatus === "INACTIVE" ? "bg-primary text-white shadow-xs" : "bg-white text-slate-700 hover:bg-slate-50 border border-slate-200"
            }`}
          >
            Tạm tắt ({promotions.length - activeCount})
          </button>
        </div>

        {/* Table */}
        <div
          key={filterStatus}
          className="bg-white rounded-xl shadow-xs border border-slate-200 overflow-hidden"
        >
          <div className="overflow-x-auto scrollbar-thin">
            <table className="w-full text-left border-collapse min-w-[900px]">
              <thead>
                <tr className="bg-slate-100 text-slate-900 text-xs font-bold border-b border-slate-200">
                  <th className="py-3 px-4 font-bold border-r border-slate-200">Mã ưu đãi</th>
                  <th className="py-3 px-3 font-bold border-r border-slate-200">Loại ưu đãi</th>
                  <th className="py-3 px-3 font-bold border-r border-slate-200">Giá trị</th>
                  <th className="py-3 px-3 font-bold border-r border-slate-200">Lượt đã dùng</th>
                  <th className="py-3 px-3 font-bold border-r border-slate-200">Thời hạn áp dụng</th>
                  <th className="py-3 px-3 text-center font-bold border-r border-slate-200">Trạng thái</th>
                  <th className="py-3 px-4 text-center font-bold">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 text-sm text-slate-800">
                {filteredPromotions.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3.5 px-4 border-r border-slate-200">
                      <div className="flex items-center gap-3">
                        <div className="p-1.5 rounded bg-slate-100 border border-slate-200 text-primary">
                          <span className="material-symbols-outlined text-[18px]">local_offer</span>
                        </div>
                        <div>
                          <span className="font-mono font-bold text-sm text-slate-900 tracking-wide block">
                            {p.code}
                          </span>
                          <span className="text-xs text-slate-500">{p.description}</span>
                        </div>
                      </div>
                    </td>
                    <td className="py-3.5 px-3 border-r border-slate-200">
                      <span className="px-2 py-0.5 rounded bg-slate-100 border border-slate-200 text-xs font-semibold text-slate-700">
                        {p.type === "PERCENTAGE" ? "Phần trăm (%)" : "Số tiền cố định"}
                      </span>
                    </td>
                    <td className="py-3.5 px-3 font-mono font-bold text-slate-900 border-r border-slate-200">
                      {p.type === "PERCENTAGE" ? `${p.value}%` : `${p.value.toLocaleString("vi-VN")} đ`}
                    </td>
                    <td className="py-3.5 px-3 border-r border-slate-200">
                      <div className="flex flex-col gap-1 w-28">
                        <span className="text-xs font-mono font-bold text-slate-800">{p.usedCount} / {p.maxUses}</span>
                        <div className="w-full h-1.5 bg-slate-200 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-primary rounded-full"
                            style={{ width: `${Math.min(100, (p.usedCount / p.maxUses) * 100)}%` }}
                          ></div>
                        </div>
                      </div>
                    </td>
                    <td className="py-3.5 px-3 text-xs text-slate-600 font-mono border-r border-slate-200">
                      <div>{p.validFrom} – {p.validTo}</div>
                    </td>
                    <td className="py-3.5 px-3 text-center border-r border-slate-200">
                      <button
                        onClick={() => handleToggleActive(p.id)}
                        className={`px-2.5 py-0.5 rounded text-xs font-semibold cursor-pointer transition-all ${
                          p.isActive
                            ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                            : "bg-slate-100 text-slate-600 border border-slate-200"
                        }`}
                      >
                        {p.isActive ? "BẬT" : "TẮT"}
                      </button>
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      <div className="flex items-center justify-center gap-1.5">
                        <button
                          onClick={() => setSelectedVoucherHistory(p)}
                          className="p-1.5 rounded text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                          title="Lịch sử áp dụng"
                        >
                          <span className="material-symbols-outlined text-[18px]">history</span>
                        </button>
                        <button
                          onClick={() => handleOpenEdit(p)}
                          className="p-1.5 rounded text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                          title="Sửa mã"
                        >
                          <span className="material-symbols-outlined text-[18px]">edit</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Minimal Clean Pagination Bar */}
          <div className="px-4 py-2 border-t border-slate-200 bg-slate-50 flex items-center justify-between text-xs select-none">
            <div className="flex items-center gap-2">
              <span className="text-slate-600 font-medium">Số dòng:</span>
              <select
                defaultValue={10}
                className="px-2 py-1 rounded bg-white border border-slate-200 text-xs font-semibold text-slate-800 cursor-pointer focus:outline-none"
              >
                <option value={5}>5</option>
                <option value={10}>10</option>
                <option value={20}>20</option>
              </select>
            </div>
            <div className="flex items-center gap-1">
              <button className="px-2 py-1 rounded text-slate-500 hover:bg-slate-200 font-medium cursor-pointer" disabled>
                Trước
              </button>
              <span className="w-7 h-7 rounded bg-primary text-white flex items-center justify-center font-bold text-xs">
                1
              </span>
              <button className="px-2 py-1 rounded text-slate-500 hover:bg-slate-200 font-medium cursor-pointer" disabled>
                Sau
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* USAGE HISTORY MODAL (Portaled to body) */}
      {selectedVoucherHistory && createPortal(
        <div className="fixed inset-0 z-[9999] bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-space-xl shadow-2xl border border-outline-variant/30 space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-secondary text-[24px]">history</span>
                <h3 className="font-headline-md text-headline-md text-on-surface font-bold">
                  Lịch sử áp dụng: #{selectedVoucherHistory.code}
                </h3>
              </div>
              <button
                onClick={() => setSelectedVoucherHistory(null)}
                className="p-1 rounded-lg text-on-surface-variant hover:bg-surface-container cursor-pointer"
              >
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low text-body-sm">
              <span>Tổng số lượt đã dùng: <strong className="text-primary">{selectedVoucherHistory.usedCount}</strong> / {selectedVoucherHistory.maxUses}</span>
              <span className="px-2 py-0.5 rounded-full bg-primary/10 text-primary font-bold text-[11px]">Scope: VENDOR</span>
            </div>

            <div className="max-h-60 overflow-y-auto flex flex-col divide-y divide-outline-variant/10 font-body-sm">
              <div className="py-2.5 flex items-center justify-between">
                <div>
                  <span className="font-bold text-primary">#SUB-89412-01</span>
                  <div className="text-[12px] text-on-surface-variant">Nguyễn Văn An • Chèo SUP Mỹ Khê</div>
                </div>
                <div className="text-right">
                  <span className="font-bold text-emerald-600">-15%</span>
                  <div className="text-[11px] text-outline">24/10/2024</div>
                </div>
              </div>
              <div className="py-2.5 flex items-center justify-between">
                <div>
                  <span className="font-bold text-primary">#SUB-89380-02</span>
                  <div className="text-[12px] text-on-surface-variant">Lê Quốc Bảo • SUP Bãi Bụt</div>
                </div>
                <div className="text-right">
                  <span className="font-bold text-emerald-600">-15%</span>
                  <div className="text-[11px] text-outline">12/10/2024</div>
                </div>
              </div>
            </div>

            <div className="flex justify-end pt-2 border-t border-outline-variant/20">
              <button
                onClick={() => setSelectedVoucherHistory(null)}
                className="px-4 py-2 rounded-xl bg-surface-container text-on-surface font-label-md"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* CREATE / EDIT PROMOTION MODAL (Portaled to body) */}
      {isModalOpen && createPortal(
        <div className="fixed inset-0 z-[9999] bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-space-xl shadow-2xl border border-outline-variant/30 space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-[24px]">loyalty</span>
                <h3 className="font-headline-md text-headline-md text-on-surface font-bold">
                  {editingId ? "Chỉnh sửa mã khuyến mãi" : "Tạo mã khuyến mãi mới"}
                </h3>
              </div>
              <button
                onClick={() => setIsModalOpen(false)}
                className="p-1 rounded-lg text-on-surface-variant hover:bg-surface-container cursor-pointer"
              >
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            <form onSubmit={handleSaveVoucher} className="space-y-4">
              <div>
                <label className="font-label-md text-label-md text-on-surface font-semibold block mb-1">
                  Mã Code (Chữ hoa & số, không khoảng trắng) *
                </label>
                <input
                  type="text"
                  required
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  placeholder="VÍ DỤ: HELLOSUMMER"
                  className="w-full px-3 py-2 rounded-xl bg-surface-container-low border border-outline-variant/30 font-headline-sm font-bold text-primary"
                />
              </div>

              <div>
                <label className="font-label-md text-label-md text-on-surface font-semibold block mb-1">
                  Mô tả ưu đãi
                </label>
                <input
                  type="text"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Giảm giá tour SUP sáng sớm cho nhóm bạn"
                  className="w-full px-3 py-2 rounded-xl bg-surface-container-low border border-outline-variant/30 font-body-md"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="font-label-md text-label-md text-on-surface font-semibold block mb-1">
                    Loại giảm giá
                  </label>
                  <select
                    value={type}
                    onChange={(e) => setType(e.target.value as any)}
                    className="w-full px-3 py-2 rounded-xl bg-surface-container-low border border-outline-variant/30 font-body-md"
                  >
                    <option value="PERCENTAGE">Theo Phần trăm (%)</option>
                    <option value="FIXED">Số tiền cố định (VNĐ)</option>
                  </select>
                </div>
                <div>
                  <label className="font-label-md text-label-md text-on-surface font-semibold block mb-1">
                    Giá trị giảm ({type === "PERCENTAGE" ? "%" : "VNĐ"}) *
                  </label>
                  <input
                    type="number"
                    required
                    min={1}
                    value={value}
                    onChange={(e) => setValue(Number(e.target.value))}
                    className="w-full px-3 py-2 rounded-xl bg-surface-container-low border border-outline-variant/30 font-body-md font-bold text-secondary"
                  />
                </div>
              </div>

              <div>
                <label className="font-label-md text-label-md text-on-surface font-semibold block mb-1">
                  Số lượng mã tối đa (lượt) *
                </label>
                <input
                  type="number"
                  required
                  min={1}
                  value={maxUses}
                  onChange={(e) => setMaxUses(Number(e.target.value))}
                  className="w-full px-3 py-2 rounded-xl bg-surface-container-low border border-outline-variant/30 font-body-md"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="font-label-md text-label-md text-on-surface font-semibold block mb-1">
                    Có hiệu lực từ
                  </label>
                  <input
                    type="date"
                    required
                    value={validFrom}
                    onChange={(e) => setValidFrom(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-surface-container-low border border-outline-variant/30 font-body-md"
                  />
                </div>
                <div>
                  <label className="font-label-md text-label-md text-on-surface font-semibold block mb-1">
                    Hết hạn ngày
                  </label>
                  <input
                    type="date"
                    required
                    value={validTo}
                    onChange={(e) => setValidTo(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-surface-container-low border border-outline-variant/30 font-body-md"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 rounded-xl bg-surface-container text-on-surface font-label-md cursor-pointer"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl bg-primary text-on-primary font-label-md font-bold cursor-pointer hover:bg-primary-container shadow-md"
                >
                  {editingId ? "Cập nhật mã" : "Lưu mã mới"}
                </button>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
