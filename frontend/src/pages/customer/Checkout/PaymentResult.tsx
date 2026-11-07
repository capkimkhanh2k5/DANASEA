import { BookingStepper } from "../../../components/customer/BookingStepper";
import { useEffect, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { AlertCircle, ArrowRight, CheckCircle2, Clock } from "lucide-react";
import { orderService } from "../../../services/orderService";
import { paymentService } from "../../../services/paymentService";
import { useAuthStore } from "../../../store/useAuthStore";
import { useCartStore } from "../../../store/useCartStore";
import type { PaymentResponse } from "../../../types/order";
import { takePurchasedItems } from "../../../utils/checkoutPaymentSession";

const paymentIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const providerNames = { VNPAY: "VNPAY", PAYPAL: "PayPal", PAYOS: "payOS", ZALOPAY: "ZaloPay", MOMO: "MoMo", SEPAY: "SePay" };

function clearPurchasedItems(paymentId: string) {
  const cart = useCartStore.getState();
  for (const purchased of takePurchasedItems(paymentId)) {
    if (cart.items.some((item) => item.id === purchased.id && item.quantity === purchased.quantity)) {
      cart.removeItem(purchased.id);
    }
  }
}

export function PaymentResult({ cancelled = false }: { cancelled?: boolean }) {
  const { pathname, search } = useLocation();
  const [searchParams] = useSearchParams();
  const customerId = useAuthStore((state) => state.isAuthenticated ? state.user?.id : undefined);
  const [retry, setRetry] = useState(0);
  const [status, setStatus] = useState<{ key: string; payment?: PaymentResponse }>();

  const paymentId = searchParams.get("paymentId") ?? searchParams.get("vnp_TxnRef");
  const orderId = searchParams.get("orderId");
  const token = searchParams.get("token");
  const leftGateway = cancelled || searchParams.get("status") === "cancelled" || searchParams.get("vnp_ResponseCode") === "24";
  const canVerify = Boolean(paymentId && paymentIdPattern.test(paymentId));
  const requestKey = `${customerId}:${pathname}${search}:${retry}`;
  const verified = customerId && status?.key === requestKey ? status : undefined;
  const payment = verified?.payment;

  useEffect(() => {
    if (!customerId || !paymentId || !canVerify) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    let requestedReconciliation = false;

    const verify = async (attempt: number) => {
      try {
        let detail = await paymentService.getPaymentDetail(paymentId, controller.signal);
        if (controller.signal.aborted) return;
        if (orderId && detail.masterOrderId !== orderId) {
          setStatus({ key: requestKey });
          return;
        }

        if (attempt === 0 && !leftGateway && detail.status === "PENDING"
            && detail.provider === "PAYPAL" && token && token === detail.providerOrderId) {
          try {
            detail = await paymentService.capturePayPalOrder(paymentId, token);
          } catch {
            // A capture request may have succeeded before its response was lost.
            detail = await paymentService.getPaymentDetail(paymentId, controller.signal);
          }
        }

        if (detail.status === "PENDING" && !requestedReconciliation
            && ["VNPAY", "PAYOS", "ZALOPAY"].includes(detail.provider)
            && (!detail.reconciliationNextAttemptAt || Date.parse(detail.reconciliationNextAttemptAt) <= Date.now())) {
          requestedReconciliation = true;
          setStatus({ key: requestKey, payment: detail });
          try {
            const order = await paymentService.getOrderPaymentStatus(detail.masterOrderId, controller.signal);
            if (order.paymentStatus === "PAID" && !controller.signal.aborted) {
              detail = await paymentService.getPaymentDetail(paymentId, controller.signal);
            }
          } catch {
            // The payment status remains pending if the reconciliation request fails.
          }
        }

        if (controller.signal.aborted) return;
        setStatus({ key: requestKey, payment: detail });
        if (detail.status === "SUCCESS") {
          const order = await orderService.getOrderDetail(detail.masterOrderId);
          if (!controller.signal.aborted && ["PAID", "PARTIALLY_COMPLETED", "COMPLETED"].includes(order.status)) {
            clearPurchasedItems(paymentId);
          }
        } else if (detail.status === "PENDING" && attempt < 4) {
          timer = setTimeout(() => void verify(attempt + 1), 2_000);
        }
      } catch {
        if (!controller.signal.aborted) {
          setStatus((current) => current?.key === requestKey && current.payment?.status === "SUCCESS"
            ? current
            : { key: requestKey });
        }
      }
    };

    void verify(0);
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [canVerify, customerId, leftGateway, orderId, paymentId, requestKey, token]);

  let title = "Đang xác minh thanh toán";
  let description = "DANASEA đang kiểm tra trạng thái giao dịch với hệ thống thanh toán.";
  let Icon = Clock;
  let iconClass = "bg-sky-50 text-sky-700";

  if (!canVerify || (verified && !payment)) {
    title = "Chưa thể xác minh giao dịch";
    description = "Quý khách có thể kiểm tra trạng thái trong mục Đơn hàng.";
    Icon = AlertCircle;
    iconClass = "bg-amber-50 text-amber-700";
  } else if (!customerId) {
    title = "Đăng nhập để kiểm tra thanh toán";
    description = "Vui lòng đăng nhập vào tài khoản đã dùng để đặt vé.";
  } else if (payment?.status === "SUCCESS") {
    title = "Thanh toán đã được xác nhận";
    description = "Xem tình trạng đơn hàng và vé của quý khách trong mục Đơn hàng.";
    Icon = CheckCircle2;
    iconClass = "bg-emerald-50 text-emerald-700";
  } else if (payment?.status === "FAILED") {
    title = "Thanh toán chưa hoàn tất";
    description = "Quý khách có thể xem trạng thái đơn hàng và chọn cách tiếp tục trong mục Đơn hàng.";
    Icon = AlertCircle;
    iconClass = "bg-amber-50 text-amber-700";
  } else if (payment?.status === "REFUNDED") {
    title = "Giao dịch đã được hoàn tiền";
    description = "Chi tiết hoàn tiền được hiển thị trong mục Đơn hàng.";
  } else if (leftGateway) {
    title = "Quý khách đã rời cổng thanh toán";
    description = "DANASEA đang chờ xác nhận. Vui lòng kiểm tra trạng thái đơn hàng trước khi thanh toán lại.";
  } else if (payment?.status === "PENDING") {
    title = "Đang chờ xác nhận thanh toán";
    description = "DANASEA sẽ cập nhật đơn hàng sau khi nhận được kết quả từ cổng thanh toán.";
  }

  return (
    <main className="min-h-[calc(100vh-80px)] bg-white pb-16 pt-24">
      <section className="border-b border-slate-200/80 bg-slate-50/70">
        <BookingStepper currentStep={3} />
      </section>
      <div className="mx-4 mt-8 max-w-lg sm:mx-auto rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
        <section aria-live="polite">
          <div className={`mb-5 flex h-12 w-12 items-center justify-center rounded-xl ${iconClass}`}>
            <Icon className="h-6 w-6" aria-hidden="true" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
          <p className="mt-2 text-sm leading-relaxed text-slate-600">{description}</p>
          {payment && (
            <dl className="mt-5 flex flex-wrap justify-between gap-3 border-t border-slate-200 pt-4 text-sm">
              <div>
                <dt className="text-slate-600">Cổng thanh toán</dt>
                <dd className="mt-1 font-semibold text-slate-900">{providerNames[payment.provider]}</dd>
              </div>
              <div>
                <dt className="text-slate-600">Số tiền giao dịch</dt>
                <dd className="mt-1 font-semibold tabular-nums text-slate-900">{payment.amount.toLocaleString("vi-VN")}₫</dd>
              </div>
            </dl>
          )}
        </section>
        <div className="mt-6 flex flex-wrap items-center gap-3">
          {!customerId && canVerify ? (
            <Link
              to="/auth"
              state={{ paymentReturnTo: pathname + search }}
              className="inline-flex items-center gap-2 rounded-xl bg-sky-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-sky-800"
            >
              Đăng nhập <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
          ) : (
            <Link
              to="/profile/orders"
              className="inline-flex items-center gap-2 rounded-xl bg-sky-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-sky-800"
            >
              Xem đơn hàng <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
          )}
          {customerId && verified && (!payment || payment.status === "PENDING") && (
            <button
              type="button"
              onClick={() => setRetry((value) => value + 1)}
              className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              Kiểm tra lại
            </button>
          )}
        </div>
      </div>
    </main>
  );
}
