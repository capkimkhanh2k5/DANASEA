import React, { useState, useEffect } from "react";
import { vendorSettlementService } from "../../../services/vendorSettlementService";
import { vendorProfileService } from "../../../services/vendorProfileService";
import { 
  Building2, 
  Eye, 
  EyeOff, 
  Copy, 
  Send, 
  FileText, 
  CheckCircle2 
} from "lucide-react";
import { 
  AtlasPageHeader, 
  AtlasMetricCard, 
  AtlasBadge, 
  AtlasButton 
} from "../../../components/vendor/atlas/AtlasDesignSystem";

interface PayoutHistoryItem {
  id: string;
  cycleId: string;
  amount: number;
  status: "REQUESTED" | "APPROVED" | "PAID" | "REJECTED";
  createdAt: string;
  processedAt: string | null;
  bankRef: string;
}

export function Payouts() {
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 4000);
  };

  // Available Balance for withdrawal
  const [availableBalance, setAvailableBalance] = useState(0);
  const [selectedCycle, setSelectedCycle] = useState("");
  const [availableCycles, setAvailableCycles] = useState<{ id: string; label: string; amount: number }[]>([]);
  const [payoutAmount, setPayoutAmount] = useState<string>("0");
  const [payoutNote, setPayoutNote] = useState("");
  const [showAccount, setShowAccount] = useState(false);
  const [bankInfo, setBankInfo] = useState<{ bankName?: string; accountNumber?: string; accountHolder?: string }>({});

  // Payout History State
  const [history, setHistory] = useState<PayoutHistoryItem[]>([]);

  useEffect(() => {
    // 1. Load real settlements
    vendorSettlementService.getSettlements().then((res) => {
      if (res && res.content && res.content.length > 0) {
        const cycles = res.content.map((s) => ({
          id: s.id,
          label: `#SET-${s.periodStart.replace(/-/g, "").substring(0, 6)} (${s.periodStart} - ${s.periodEnd})`,
          amount: Number(s.netPayableAmount) || 0,
        }));
        setAvailableCycles(cycles);
        setSelectedCycle(cycles[0].id);
        const total = cycles.reduce((acc, c) => acc + c.amount, 0);
        setAvailableBalance(total);
        setPayoutAmount(total.toString());
      } else {
        setAvailableCycles([]);
        setAvailableBalance(0);
        setPayoutAmount("0");
      }
    }).catch(() => {
      setAvailableBalance(0);
    });

    // 2. Load real vendor bank account
    vendorProfileService.getProfile().then((p) => {
      setBankInfo({
        bankName: p.bankName || "Vietcombank",
        accountNumber: p.bankAccountNumber || "",
        accountHolder: p.bankAccountHolder || p.businessName || "",
      });
    }).catch(() => {});
  }, []);

  // Validation
  const numericAmount = Number(payoutAmount.replace(/\D/g, "")) || 0;
  const isAmountOver = numericAmount > availableBalance;

  // Handle Fill Max
  const handleFillMax = () => {
    setPayoutAmount(availableBalance.toString());
  };

  // Submit Payout Request
  const handleSubmitPayout = (e: React.FormEvent) => {
    e.preventDefault();

    if (numericAmount <= 0) {
      showToast("Vui lòng nhập số tiền hợp lệ lớn hơn 0 đ!");
      return;
    }

    if (numericAmount > availableBalance) {
      showToast("Lỗi: Số tiền rút không được vượt quá số dư khả dụng!");
      return;
    }

    const newPayout: PayoutHistoryItem = {
      id: `PAY-${new Date().getFullYear()}${String(new Date().getMonth() + 1).padStart(2, "0")}-${String(history.length + 1).padStart(2, "0")}`,
      cycleId: selectedCycle,
      amount: numericAmount,
      status: "REQUESTED",
      createdAt: new Date().toLocaleDateString("vi-VN"),
      processedAt: null,
      bankRef: "CHỜ-DUYỆT-VITA",
    };

    setHistory((prev) => [newPayout, ...prev]);
    setAvailableBalance((prev) => prev - numericAmount);
    setPayoutAmount("0");
    showToast(
      `Đã tạo lệnh yêu cầu rút ${numericAmount.toLocaleString("vi-VN")} đ (Mã: #${newPayout.id}). Hồ sơ gửi tới Kế toán Cảng vụ.`
    );
  };

  const copyBankInfo = () => {
    if (bankInfo.accountNumber) {
      navigator.clipboard?.writeText(bankInfo.accountNumber);
      showToast("Đã sao chép số tài khoản vào bộ nhớ tạm!");
    } else {
      showToast("Chưa có số tài khoản để sao chép!");
    }
  };

  return (
    <div className="w-full max-w-[1400px] mx-auto px-6 py-5 space-y-5">
      {/* 1. Header Toolbar (Atlas Page Header) */}
      <AtlasPageHeader
        title="Quyết toán & Rút tiền"
      />

      {/* 2. 3-Card Financial Metric Strip (Atlas Standard) */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <AtlasMetricCard
          label="Khả dụng rút ngay"
          value={`${availableBalance.toLocaleString("vi-VN")} đ`}
          accent="emerald"
        />

        <AtlasMetricCard
          label="Lệnh đang chờ duyệt"
          value={`${history
            .filter((h) => h.status === "REQUESTED")
            .reduce((sum, h) => sum + h.amount, 0)
            .toLocaleString("vi-VN")} đ`}
          accent="amber"
        />

        <AtlasMetricCard
          label="Đã giải ngân thành công"
          value={`${history
            .filter((h) => h.status === "PAID")
            .reduce((sum, h) => sum + h.amount, 0)
            .toLocaleString("vi-VN")} đ`}
        />
      </div>

      {/* 3. Core Layout: Form (Left 7 Cols), History Ledger (Right 5 Cols) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
        {/* Form Area */}
        <div className="lg:col-span-7 space-y-4">
          <div className="bg-white p-5 rounded-xl border border-slate-200/80 shadow-xs space-y-4">
            {/* Linked Bank Card */}
            <div className="space-y-2">
              <span className="text-xs text-slate-500 font-semibold uppercase tracking-wider block">
                Tài khoản ngân hàng thụ hưởng
              </span>
              <div className="bg-slate-50/80 p-4 rounded-xl border border-slate-200/80 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-white flex items-center justify-center text-slate-900 border border-slate-200 shadow-xs">
                    <Building2 className="w-5 h-5 text-slate-700" />
                  </div>
                  <div>
                    <span className="text-sm font-bold text-slate-900 block">
                      {bankInfo.bankName || "Tài khoản ngân hàng"}
                    </span>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="font-mono text-slate-800 font-bold tracking-wider text-xs">
                        {bankInfo.accountNumber
                          ? showAccount
                            ? bankInfo.accountNumber
                            : `•••• •••• •••• ${bankInfo.accountNumber.slice(-4)}`
                          : "Chưa cấu hình tài khoản"}
                      </span>
                      {bankInfo.accountNumber && (
                        <div className="flex items-center gap-1">
                          <button
                            onClick={() => setShowAccount(!showAccount)}
                            className="text-slate-400 hover:text-slate-700 cursor-pointer p-0.5"
                            type="button"
                          >
                            {showAccount ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                          </button>
                          <button
                            onClick={copyBankInfo}
                            className="text-slate-400 hover:text-slate-700 cursor-pointer p-0.5"
                            type="button"
                            title="Sao chép số tài khoản"
                          >
                            <Copy className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      )}
                    </div>
                    <div className="text-xs text-slate-500 font-medium mt-0.5">
                      {bankInfo.accountHolder || "Chưa cập nhật tên chủ tài khoản"}
                    </div>
                  </div>
                </div>
                <AtlasBadge label="Chính thức" variant="success" />
              </div>
            </div>

            {/* Form Input */}
            <form onSubmit={handleSubmitPayout} className="space-y-4">
              {/* Settlement select */}
              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1.5">
                  Chọn kỳ đối soát liên kết
                </label>
                <select
                  value={selectedCycle}
                  onChange={(e) => setSelectedCycle(e.target.value)}
                  disabled={availableCycles.length === 0}
                  className="w-full h-11 px-3.5 rounded-xl bg-slate-50/70 border border-slate-200 text-slate-800 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900 disabled:bg-slate-100"
                >
                  {availableCycles.length === 0 ? (
                    <option value="">Chưa có kỳ đối soát khả dụng</option>
                  ) : (
                    availableCycles.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.label} — Khả dụng: {c.amount.toLocaleString("vi-VN")} đ
                      </option>
                    ))
                  )}
                </select>
              </div>

              {/* Amount input */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="text-xs font-semibold text-slate-700">
                    Số tiền yêu cầu giải ngân (VNĐ) *
                  </label>
                  <button
                    type="button"
                    onClick={handleFillMax}
                    className="text-xs font-bold text-primary hover:underline cursor-pointer"
                  >
                    Rút toàn bộ số dư
                  </button>
                </div>

                <div className="relative">
                  <input
                    type="number"
                    required
                    min={100000}
                    max={availableBalance}
                    value={payoutAmount}
                    onChange={(e) => setPayoutAmount(e.target.value)}
                    className={`w-full h-11 pl-3.5 pr-10 rounded-xl bg-white border font-mono font-bold text-base text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900 ${
                      isAmountOver ? "border-rose-500 text-rose-600" : "border-slate-200"
                    }`}
                  />
                  <span className="absolute right-3.5 top-1/2 -translate-y-1/2 font-bold text-slate-400 text-sm">
                    đ
                  </span>
                </div>

                {isAmountOver && (
                  <p className="text-rose-600 text-xs mt-1.5 font-medium">
                    Số tiền yêu cầu không được vượt quá số dư khả dụng ({availableBalance.toLocaleString("vi-VN")} đ).
                  </p>
                )}
              </div>

              {/* Note */}
              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1.5">
                  Ghi chú gửi Bộ phận Tài vụ Cảng vụ
                </label>
                <textarea
                  rows={2}
                  value={payoutNote}
                  onChange={(e) => setPayoutNote(e.target.value)}
                  placeholder="Ghi chú thêm nếu có..."
                  className="w-full p-3 rounded-xl bg-white border border-slate-200 text-slate-800 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900"
                />
              </div>

              <div className="pt-2">
                <AtlasButton
                  type="submit"
                  variant="primary"
                  size="lg"
                  disabled={isAmountOver || availableBalance <= 0}
                  className="w-full"
                  icon={<Send className="w-4 h-4" />}
                >
                  Gửi yêu cầu giải ngân ({numericAmount.toLocaleString("vi-VN")} đ)
                </AtlasButton>
              </div>
            </form>
          </div>
        </div>

        {/* Right History Area: Financial Ledger Table */}
        <div className="lg:col-span-5 bg-white rounded-xl border border-slate-200/80 shadow-xs overflow-hidden">
          <div className="px-4 py-3.5 border-b border-slate-100 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <FileText className="w-4 h-4 text-slate-600" />
              <h3 className="text-sm font-semibold text-slate-900">
                Sổ Cái Lệnh Chi
              </h3>
            </div>
            <span className="text-xs text-slate-500 font-mono">{history.length} giao dịch</span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm border-collapse">
              <thead>
                <tr className="bg-[#f8f9fa] border-b border-slate-200/80 text-slate-500 font-semibold text-xs uppercase tracking-wider">
                  <th className="py-3 px-4">MÃ &amp; KỲ</th>
                  <th className="py-3 px-3 text-center">TRẠNG THÁI</th>
                  <th className="py-3 px-4 text-right">GIẢI NGÂN</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700">
                {history.length === 0 ? (
                  <tr>
                    <td colSpan={3} className="py-12 text-center text-slate-400 text-sm">
                      Chưa có lệnh yêu cầu rút tiền nào.
                    </td>
                  </tr>
                ) : (
                  history.map((item) => (
                    <tr key={item.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="py-3.5 px-4">
                        <div className="font-mono font-bold text-slate-900 text-sm">#{item.id}</div>
                        <div className="text-xs text-slate-500 font-mono mt-0.5">{item.createdAt}</div>
                      </td>
                      <td className="py-3.5 px-3 text-center">
                        {item.status === "PAID" ? (
                          <AtlasBadge label="Đã chi trả" variant="success" />
                        ) : (
                          <AtlasBadge label="Chờ duyệt" variant="warning" />
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <span className="font-mono text-sm font-bold text-slate-900 tabular-nums">
                          {item.amount.toLocaleString("vi-VN")}
                        </span>
                        <span className="text-xs text-slate-400 ml-1">đ</span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Toast Alert */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 bg-slate-900 text-white px-4 py-2.5 rounded-xl shadow-lg flex items-center gap-2 text-sm border border-slate-700">
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  );
}
