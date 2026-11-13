import React, { useState, useEffect, useCallback } from "react";
import { 
  vendorPayoutService, 
  type EligibleSettlementPayout, 
  type PayoutRequestItem 
} from "../../../services/vendorPayoutService";
import { vendorProfileService } from "../../../services/vendorProfileService";
import { 
  Building2, 
  Eye, 
  EyeOff, 
  Copy, 
  Send, 
  FileText, 
  CheckCircle2,
  AlertCircle,
  Loader2
} from "lucide-react";
import { 
  AtlasPageHeader, 
  AtlasMetricCard, 
  AtlasBadge, 
  AtlasButton 
} from "../../../components/vendor/atlas/AtlasDesignSystem";

export function Payouts() {
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastType, setToastType] = useState<"success" | "error">("success");

  const showToast = (msg: string, type: "success" | "error" = "success") => {
    setToastMessage(msg);
    setToastType(type);
    setTimeout(() => setToastMessage(null), 4000);
  };

  // Available Balance & Cycles for withdrawal
  const [availableBalance, setAvailableBalance] = useState(0);
  const [selectedCycle, setSelectedCycle] = useState("");
  const [eligibleCycles, setEligibleCycles] = useState<EligibleSettlementPayout[]>([]);
  const [payoutAmount, setPayoutAmount] = useState<string>("0");
  const [payoutNote, setPayoutNote] = useState("");
  const [showAccount, setShowAccount] = useState(false);
  const [bankInfo, setBankInfo] = useState<{ bankName?: string; accountNumber?: string; accountHolder?: string }>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  // Payout History State
  const [history, setHistory] = useState<PayoutRequestItem[]>([]);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [eligibleRes, historyRes, profileRes] = await Promise.allSettled([
        vendorPayoutService.getEligibleSettlements(),
        vendorPayoutService.getPayoutRequests('ALL', 0, 50),
        vendorProfileService.getProfile(),
      ]);

      // 1. Eligible Settlements
      if (eligibleRes.status === "fulfilled" && eligibleRes.value.length > 0) {
        const cycles = eligibleRes.value;
        setEligibleCycles(cycles);
        setSelectedCycle(cycles[0].settlementId);
        const total = cycles.reduce((acc, c) => acc + (Number(c.remainingEligibleBalance) || 0), 0);
        setAvailableBalance(total);
        setPayoutAmount(total > 0 ? total.toString() : "0");
      } else {
        setEligibleCycles([]);
        setAvailableBalance(0);
        setPayoutAmount("0");
      }

      // 2. Payout History
      if (historyRes.status === "fulfilled") {
        setHistory(historyRes.value.content || []);
      }

      // 3. Vendor Bank Info
      if (profileRes.status === "fulfilled" && profileRes.value) {
        const p = profileRes.value;
        setBankInfo({
          bankName: p.bankName || "Vietcombank (Hàng hải)",
          accountNumber: p.bankAccountNumber || "",
          accountHolder: p.bankAccountHolder || p.businessName || "",
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Validation
  const numericAmount = Number(payoutAmount.replace(/\D/g, "")) || 0;
  const currentSelectedCycle = eligibleCycles.find((c) => c.settlementId === selectedCycle);
  const maxCycleBalance = currentSelectedCycle ? Number(currentSelectedCycle.remainingEligibleBalance) || 0 : availableBalance;
  const isAmountOver = numericAmount > maxCycleBalance;

  // Handle Fill Max
  const handleFillMax = () => {
    if (maxCycleBalance > 0) {
      setPayoutAmount(maxCycleBalance.toString());
    } else {
      setPayoutAmount(availableBalance.toString());
    }
  };

  // Submit Payout Request
  const handleSubmitPayout = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!selectedCycle) {
      showToast("Vui lòng chọn kỳ đối soát có số dư khả dụng!", "error");
      return;
    }

    if (numericAmount < 1000) {
      showToast("Số tiền rút tối thiểu là 1.000 đ!", "error");
      return;
    }

    if (numericAmount > maxCycleBalance) {
      showToast(`Số tiền rút không được vượt quá số dư kỳ này (${maxCycleBalance.toLocaleString("vi-VN")} đ)!`, "error");
      return;
    }

    setIsSubmitting(true);
    try {
      const created = await vendorPayoutService.createPayoutRequest({
        settlementId: selectedCycle,
        amount: numericAmount,
        note: payoutNote.trim() || undefined,
      });

      showToast(`Tạo lệnh rút ${numericAmount.toLocaleString("vi-VN")} đ thành công (Mã #${created.id.slice(0, 8).toUpperCase()}).`);
      setPayoutNote("");
      await loadData();
    } catch (err: unknown) {
      const errorMsg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || "Không thể gửi yêu cầu rút tiền. Vui lòng thử lại sau.";
      showToast(errorMsg, "error");
    } finally {
      setIsSubmitting(false);
    }
  };

  const copyBankInfo = () => {
    if (bankInfo.accountNumber) {
      navigator.clipboard?.writeText(bankInfo.accountNumber);
      showToast("Đã sao chép số tài khoản vào bộ nhớ tạm!");
    } else {
      showToast("Chưa có số tài khoản để sao chép!", "error");
    }
  };

  const pendingAmount = history
    .filter((h) => h.status === "REQUESTED")
    .reduce((sum, h) => sum + Number(h.amount), 0);

  const approvedAmount = history
    .filter((h) => h.status === "APPROVED")
    .reduce((sum, h) => sum + Number(h.amount), 0);

  const paidAmount = history
    .filter((h) => h.status === "PAID")
    .reduce((sum, h) => sum + Number(h.amount), 0);

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
          label="Chờ duyệt & Đang chi trả"
          value={`${(pendingAmount + approvedAmount).toLocaleString("vi-VN")} đ`}
          accent="amber"
        />

        <AtlasMetricCard
          label="Đã giải ngân thành công"
          value={`${paidAmount.toLocaleString("vi-VN")} đ`}
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
                  Chọn kỳ đối soát liên kết *
                </label>
                <select
                  value={selectedCycle}
                  onChange={(e) => {
                    setSelectedCycle(e.target.value);
                    const sel = eligibleCycles.find((c) => c.settlementId === e.target.value);
                    if (sel) {
                      setPayoutAmount(Number(sel.remainingEligibleBalance || 0).toString());
                    }
                  }}
                  disabled={eligibleCycles.length === 0}
                  className="w-full h-11 px-3.5 rounded-xl bg-slate-50/70 border border-slate-200 text-slate-800 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900 disabled:bg-slate-100"
                >
                  {eligibleCycles.length === 0 ? (
                    <option value="">Chưa có kỳ đối soát khả dụng (cần được chốt FINALIZED)</option>
                  ) : (
                    eligibleCycles.map((c) => (
                      <option key={c.settlementId} value={c.settlementId}>
                        #SET-{c.periodStart.replace(/-/g, "").substring(0, 6)} ({c.periodStart} – {c.periodEnd}) — Khả dụng: {Number(c.remainingEligibleBalance).toLocaleString("vi-VN")} đ
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
                    Rút toàn bộ số dư kỳ này
                  </button>
                </div>

                <div className="relative">
                  <input
                    type="number"
                    required
                    min={1000}
                    max={maxCycleBalance}
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
                    Số tiền yêu cầu không được vượt quá số dư khả dụng kỳ này ({maxCycleBalance.toLocaleString("vi-VN")} đ).
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
                  placeholder="Ghi chú thêm nếu có (ví dụ: chuyển khoản gấp)..."
                  className="w-full p-3 rounded-xl bg-white border border-slate-200 text-slate-800 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900"
                />
              </div>

              <div className="pt-2">
                <AtlasButton
                  type="submit"
                  variant="primary"
                  size="lg"
                  disabled={isSubmitting || isAmountOver || maxCycleBalance <= 0 || !selectedCycle}
                  className="w-full"
                  icon={isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                >
                  {isSubmitting ? "Đang xử lý..." : `Gửi yêu cầu giải ngân (${numericAmount.toLocaleString("vi-VN")} đ)`}
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
                {isLoading ? (
                  <tr>
                    <td colSpan={3} className="py-12 text-center text-slate-400 text-sm">
                      <div className="inline-flex items-center gap-2">
                        <Loader2 className="w-4 h-4 animate-spin text-primary" />
                        <span>Đang tải sổ cái giao dịch...</span>
                      </div>
                    </td>
                  </tr>
                ) : history.length === 0 ? (
                  <tr>
                    <td colSpan={3} className="py-12 text-center text-slate-400 text-sm">
                      Chưa có lệnh yêu cầu rút tiền nào.
                    </td>
                  </tr>
                ) : (
                  history.map((item) => {
                    const shortId = item.id.replace(/-/g, '').slice(0, 8).toUpperCase();
                    const createdDate = item.createdAt ? item.createdAt.slice(0, 10) : "—";
                    return (
                      <tr key={item.id} className="hover:bg-slate-50/70 transition-colors">
                        <td className="py-3.5 px-4">
                          <div className="font-mono font-bold text-slate-900 text-sm">#PAY-{shortId}</div>
                          <div className="text-xs text-slate-500 font-mono mt-0.5">{createdDate}</div>
                          {item.bankReference && (
                            <div className="text-[11px] text-emerald-600 font-mono font-semibold mt-0.5">
                              Ref: {item.bankReference}
                            </div>
                          )}
                          {item.rejectionReason && (
                            <div className="text-[11px] text-rose-600 mt-0.5">
                              Lý do: {item.rejectionReason}
                            </div>
                          )}
                        </td>
                        <td className="py-3.5 px-3 text-center">
                          {item.status === "PAID" ? (
                            <AtlasBadge label="Đã chi trả" variant="success" />
                          ) : item.status === "APPROVED" ? (
                            <AtlasBadge label="Đã duyệt" variant="info" />
                          ) : item.status === "REJECTED" ? (
                            <AtlasBadge label="Từ chối" variant="danger" />
                          ) : (
                            <AtlasBadge label="Chờ duyệt" variant="warning" />
                          )}
                        </td>
                        <td className="py-3.5 px-4 text-right">
                          <span className="font-mono text-sm font-bold text-slate-900 tabular-nums">
                            {Number(item.amount).toLocaleString("vi-VN")}
                          </span>
                          <span className="text-xs text-slate-400 ml-1">đ</span>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Toast Alert */}
      {toastMessage && (
        <div className={`fixed bottom-6 right-6 z-50 px-4 py-2.5 rounded-xl shadow-lg flex items-center gap-2 text-sm border ${
          toastType === "error" 
            ? "bg-rose-950 text-rose-100 border-rose-800" 
            : "bg-slate-900 text-white border-slate-700"
        }`}>
          {toastType === "error" ? (
            <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
          ) : (
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          )}
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  );
}
