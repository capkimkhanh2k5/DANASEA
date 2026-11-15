import { useEffect, useRef, useState } from "react";
import { paymentProviderNames, paymentService } from "../../../services/paymentService";
import { useLanguageStore } from "../../../store/useLanguageStore";
import type { HttpRequestError } from "../../../types/http";
import type { OrderPaymentStatusResponse, PaymentProviderType } from "../../../types/order";

export function ContinuePaymentDialog({ orderId, onClose }: { orderId: string; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const { language } = useLanguageStore();
  const isEn = language === 'en';
  const [status, setStatus] = useState<OrderPaymentStatusResponse>();
  const [providers, setProviders] = useState<PaymentProviderType[]>([]);
  const [provider, setProvider] = useState<PaymentProviderType>();
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [reload, setReload] = useState(0);
  const switching = Boolean(status?.activeProvider && provider && status.activeProvider !== provider);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      paymentService.getOrderPaymentStatus(orderId, controller.signal),
      paymentService.getAvailableProviders(controller.signal),
    ]).then(([current, available]) => {
      if (controller.signal.aborted) return;
      setStatus(current);
      setProviders(available);
      setProvider(current.activeProvider
        ? available.find((candidate) => candidate === current.activeProvider)
        : available[0]);
    }).catch(() => {
      if (!controller.signal.aborted) setError(isEn ? "Unable to load payment status. Please try again." : "Không thể tải trạng thái thanh toán. Vui lòng thử lại.");
    });
    return () => controller.abort();
  }, [orderId, reload, isEn]);

  const pay = async () => {
    if (!provider || submitting) return;
    setSubmitting(true);
    setError("");
    try {
      const current = await paymentService.getOrderPaymentStatus(orderId);
      setStatus(current);
      if (current.activeProvider && current.activeProvider !== provider && current.activeProvider !== status?.activeProvider) {
        throw new Error("Phương thức đang giữ giao dịch đã thay đổi. Vui lòng kiểm tra lựa chọn trước khi đổi cổng.");
      }
      const intent = await paymentService.continuePayment(orderId, { provider }, current);
      const url = intent.paymentUrl || intent.paymentReference;
      if (!url || !/^https?:\/\//.test(url)) throw new Error("Không nhận được liên kết thanh toán.");
      window.location.assign(url);
    } catch (caught) {
      const failure = caught as HttpRequestError;
      setError(failure.response?.data?.message || failure.message || "Không thể mở cổng thanh toán.");
      setSubmitting(false);
    }
  };

  const closedMessage = status?.isExpired
    ? (isEn ? "Payment window has expired for this booking." : "Đơn hàng đã hết hạn thanh toán.")
    : status && (status.orderStatus !== "PENDING_PAYMENT" || status.paymentStatus !== "UNPAID")
      ? (isEn ? "This order has already been processed. Please refresh order history." : "Đơn hàng đã được xử lý. Vui lòng tải lại danh sách đơn để xem trạng thái.")
      : "";

  return (
    <dialog
      ref={dialog}
      aria-labelledby="continue-payment-title"
      onCancel={(event) => { event.preventDefault(); if (!submitting) onClose(); }}
      className="fixed inset-0 m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl border border-slate-200 bg-white p-6 text-slate-900 shadow-lg backdrop:bg-slate-950/50"
    >
      <h2 id="continue-payment-title" className="text-lg font-bold">{isEn ? "Order Payment" : "Thanh toán đơn hàng"}</h2>
      {closedMessage ? <p className="mt-3 text-sm">{closedMessage}</p> : status ? (
        <fieldset disabled={submitting} className="mt-4 space-y-2">
          <legend className="mb-2 text-sm font-semibold">{isEn ? "Payment Method" : "Phương thức thanh toán"}</legend>
          {providers.map((option) => (
            <label key={option} className="flex items-center gap-3 rounded-xl border border-slate-200 p-3 text-sm cursor-pointer">
              <input type="radio" name="order-payment-provider" checked={provider === option} onChange={() => setProvider(option)} />
              {paymentProviderNames[option]}
            </label>
          ))}
          {providers.length === 0 && <p className="text-sm">{isEn ? "No payment methods available." : "Hiện chưa có phương thức thanh toán khả dụng."}</p>}
          {status.activeProvider && !providers.includes(status.activeProvider) && (
            <p className="text-sm">{isEn ? "The previous payment gateway is unavailable. Switching gateways requires cancellation confirmation." : "Cổng đang giữ giao dịch không khả dụng. Việc đổi cổng cần xác nhận giao dịch cũ đã hủy."}</p>
          )}
          {switching && <p className="text-sm">{isEn ? "The system will open the new gateway once previous session is released." : "Hệ thống chỉ mở cổng mới sau khi xác nhận giao dịch cũ đã hủy."}</p>}
        </fieldset>
      ) : !error ? <p role="status" className="mt-3 text-sm">{isEn ? "Loading payment status…" : "Đang tải trạng thái thanh toán…"}</p> : null}
      {error && <p role="alert" className="mt-3 text-sm text-rose-700">{error}</p>}
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        <button type="button" disabled={submitting} onClick={onClose} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold cursor-pointer">
          {isEn ? "Close" : "Đóng"}
        </button>
        {!status && error && (
          <button type="button" onClick={() => { setError(""); setReload((value) => value + 1); }}
            className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold cursor-pointer">
            {isEn ? "Retry" : "Tải lại"}
          </button>
        )}
        {status && !closedMessage && (
          <button type="button" onClick={() => void pay()} disabled={!provider || submitting}
            className="rounded-xl bg-sky-800 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50 cursor-pointer">
            {submitting ? (isEn ? "Checking…" : "Đang kiểm tra…") : switching ? (isEn ? "Switch Gateway & Pay" : "Đổi cổng và thanh toán") : (isEn ? "Continue to Payment" : "Tiếp tục thanh toán")}
          </button>
        )}
      </div>
    </dialog>
  );
}
