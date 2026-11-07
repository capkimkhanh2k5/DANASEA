import { useEffect, useState, type FormEvent } from 'react';
import { AdminButton } from '../../../components/admin/AdminWorkspace';
import { adminOrderService, getAdminOrderError } from '../../../services/adminOrderService';
import type { RefundDetailResponse, SpringPage } from '../../../types/adminOrder';

const providerLabels: Record<string, string> = { VNPAY: 'VNPay', PAYPAL: 'PayPal', PAYOS: 'payOS', ZALOPAY: 'ZaloPay' };
const manualReview = 'MANUAL_REFUND_REVIEW_REQUIRED';
const formatVnd = (amount: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);

function RefundConfirmation({ refund, onComplete }: {
  refund: RefundDetailResponse;
  onComplete: (updated: RefundDetailResponse) => void;
}) {
  const [reference, setReference] = useState('');
  const [amount, setAmount] = useState('');
  const [verified, setVerified] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const currency = refund.providerCurrency;
  const normalizedAmount = amount.replace(',', '.');
  const exactAmount = (currency === 'VND' ? /^[1-9]\d{0,14}$/ : /^\d{1,15}(?:\.\d{1,2})?$/).test(normalizedAmount)
    && Number(normalizedAmount) === refund.providerAmount;
  const valid = Boolean(currency) && reference.trim().length > 0 && exactAmount && verified;

  const confirm = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!valid || !currency || submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const updated = await adminOrderService.completeManualRefund(refund.id, {
        providerRefundId: reference.trim(),
        providerAmount: Number(normalizedAmount),
        providerCurrency: currency,
      });
      onComplete(updated);
    } catch (failure) {
      setError(getAdminOrderError(failure, 'Không thể xác nhận hoàn tiền.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={(event) => void confirm(event)} className="mt-3 space-y-3">
      <p className="admin-detail-note">
        Admin tự chuyển tiền bên ngoài DANASEA. Chỉ xác nhận sau khi đã đối chiếu mã giao dịch và số tiền thực hoàn.
      </p>
      <p className="text-sm">Số tiền cần hoàn <strong>{refund.providerAmount?.toLocaleString('vi-VN')} {currency}</strong></p>
      <fieldset disabled={submitting} className="space-y-3">
        <label className="block text-sm">
          Mã giao dịch hoàn tiền
          <input className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2"
            value={reference} onChange={(event) => setReference(event.target.value)} required maxLength={150} />
        </label>
        <label className="block text-sm">
          Số tiền thực hoàn ({currency})
          <input className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2"
            value={amount} onChange={(event) => setAmount(event.target.value)} required inputMode={currency === 'VND' ? 'numeric' : 'decimal'} maxLength={18}
            placeholder={currency === 'VND' ? 'Ví dụ 50000' : 'Ví dụ 19.31'} />
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={verified} onChange={(event) => setVerified(event.target.checked)} />
          Đã đối chiếu giao dịch hoàn tiền
        </label>
      </fieldset>
      {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
      <AdminButton type="submit" variant="primary" disabled={!valid} loading={submitting}>
        Xác nhận đã hoàn tiền
      </AdminButton>
    </form>
  );
}

export function ManualRefundPanel({ orderId }: { orderId?: string }) {
  const [refunds, setRefunds] = useState<SpringPage<RefundDetailResponse>>();
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    adminOrderService.getRefunds(orderId, page).then((data) => {
      if (active) setRefunds(data);
    }).catch((failure) => {
      if (active) setError(getAdminOrderError(failure, 'Không thể tải danh sách hoàn tiền.'));
    });
    return () => { active = false; };
  }, [orderId, page, reload]);

  const changePage = (nextPage: number) => {
    setRefunds(undefined);
    setError('');
    setPage(nextPage);
  };
  const requests = refunds?.content ?? [];

  return (
    <div className="mt-4 space-y-3 border-t border-slate-200 pt-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="font-semibold">Hoàn tiền thủ công</h4>
        <AdminButton onClick={() => {
          setError('');
          setRefunds(undefined);
          setReload((value) => value + 1);
        }}>Làm mới hoàn tiền</AdminButton>
      </div>
      {error ? <p role="alert">{error}</p> : !refunds ? <p role="status">Đang tải yêu cầu hoàn tiền…</p> : (
        <>
          {requests.length === 0 && <p className="admin-detail-note">Trang này chưa có yêu cầu hoàn tiền.</p>}
          {requests.map((refund) => (
            <article key={refund.id} className="rounded-xl border border-slate-200 p-4">
              <h5 className="font-semibold">{providerLabels[refund.provider ?? ''] || refund.provider || 'Chưa xác định cổng'} · {formatVnd(refund.amount)}</h5>
              <dl className="admin-detail-grid">
                <div><dt>Mã yêu cầu hoàn</dt><dd className="break-all">{refund.id}</dd></div>
                <div><dt>Giao dịch gốc</dt><dd className="break-all">{refund.providerTransactionId || 'Chưa có mã'}</dd></div>
              </dl>
              {refund.status === 'PENDING' && refund.lastError === manualReview && !refund.gatewayRequestedAt && refund.providerAmount != null && refund.providerCurrency ? (
                <RefundConfirmation refund={refund} onComplete={(updated) =>
                  setRefunds((current) => current && {
                    ...current, content: current.content.map((item) => item.id === updated.id ? updated : item),
                  })
                } />
              ) : refund.status === 'PROCESSED' ? (
                <p role="status"><span>Đã xác nhận hoàn tiền</span> <strong className="break-all">{refund.providerRefundId}</strong></p>
              ) : refund.status === 'PENDING' ? (
                <p className="admin-detail-note">{refund.gatewayRequestedAt
                  ? 'Đang đối soát lệnh hoàn đã gửi trước đây. Chờ kết quả để tránh chuyển trùng.'
                  : 'Đang chờ đối chiếu giao dịch và số tiền cần hoàn.'}</p>
              ) : (
                <p className="admin-detail-note">Lệnh hoàn thất bại. Kiểm tra kết quả đối soát trước khi xử lý tiếp.</p>
              )}
            </article>
          ))}
          {(page > 0 || !refunds.last) && (
            <div className="flex gap-2">
              <AdminButton disabled={page === 0} onClick={() => changePage(page - 1)}>Trang trước</AdminButton>
              <AdminButton disabled={refunds.last} onClick={() => changePage(page + 1)}>Trang sau</AdminButton>
            </div>
          )}
        </>
      )}
    </div>
  );
}
