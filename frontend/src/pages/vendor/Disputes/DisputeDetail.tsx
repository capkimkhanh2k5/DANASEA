import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { disputeService } from '../../../services/disputeService';
import type { DisputeResponse } from '../../../types/dispute';
import { toast } from '../../../store/useToastStore';
import { ArrowLeft, Loader2, Send, CheckCircle2, AlertTriangle, ShieldCheck } from 'lucide-react';

export function DisputeDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [dispute, setDispute] = useState<DisputeResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [vendorResponseText, setVendorResponseText] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const fetchDetail = useCallback(async () => {
    if (!id) return;
    try {
      setIsLoading(true);
      const data = await disputeService.getVendorDisputeDetail(id);
      setDispute(data);
      if (data.vendorResponse) {
        setVendorResponseText(data.vendorResponse);
      }
    } catch (err) {
      console.error('[VendorDisputeDetail] Lỗi tải chi tiết khiếu nại:', err);
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  const handleSubmitResponse = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id || !vendorResponseText.trim()) return;

    try {
      setIsSubmitting(true);
      const updated = await disputeService.submitVendorResponse(id, {
        response: vendorResponseText.trim(),
      });
      setDispute(updated);
      toast.success('Gửi văn bản giải trình thành công!');
    } catch (err: unknown) {
      console.error('[VendorDisputeDetail] Lỗi gửi giải trình:', err);
      let msg = 'Không thể gửi giải trình.';
      if (err && typeof err === 'object' && 'response' in err) {
        const resp = (err as { response?: { data?: { message?: string } } }).response;
        if (resp?.data?.message) msg = resp.data.message;
      } else if (err instanceof Error) {
        msg = err.message;
      }
      toast.error('Lỗi: ' + msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case 'OPEN':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md font-mono tabular-nums bg-sky-50 text-sky-700 text-xs font-semibold border border-sky-200">
            <span className="w-1.5 h-1.5 rounded-full bg-sky-500 "></span>
            <span>Mới mở (OPEN)</span>
          </span>
        );
      case 'IN_PROGRESS':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md font-mono tabular-nums bg-amber-50 text-amber-700 text-xs font-semibold border border-amber-200">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
            <span>Đang xử lý</span>
          </span>
        );
      case 'UNDER_REVIEW':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md font-mono tabular-nums bg-indigo-50 text-indigo-700 text-xs font-semibold border border-indigo-200">
            <span className="w-1.5 h-1.5 rounded-full bg-indigo-500"></span>
            <span>Đang xử lý (Admin)</span>
          </span>
        );
      case 'RESOLVED':
      case 'RESOLVED_REFUND':
      case 'RESOLVED_PARTIAL':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md font-mono tabular-nums bg-amber-50 text-amber-700 text-xs font-semibold border border-amber-200">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
            <span>Chấp thuận hoàn tiền</span>
          </span>
        );
      case 'RESOLVED_REJECTED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md font-mono tabular-nums bg-emerald-50 text-emerald-700 text-xs font-semibold border border-emerald-200">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
            <span>Bác bỏ khiếu nại (Đối tác thắng)</span>
          </span>
        );
      case 'CLOSED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md font-mono tabular-nums bg-slate-100 text-slate-600 text-xs font-medium border border-slate-200">
            <span className="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
            <span>Đã đóng</span>
          </span>
        );
      default:
        return (
          <span className="px-2 py-0.5 rounded-md font-mono tabular-nums bg-slate-100 text-slate-800 text-xs">
            {status}
          </span>
        );
    }
  };

  const getReasonLabel = (reason?: string) => {
    switch (reason) {
      case 'SERVICE_NOT_AS_DESCRIBED':
        return 'Dịch vụ không đúng mô tả / cam kết';
      case 'VENDOR_NO_SHOW':
        return 'Nhà cung cấp vắng mặt / không đến đón';
      case 'SAFETY_CONCERN':
        return 'Vấn đề an toàn / thiếu trang bị cứu sinh';
      case 'PAYMENT_ISSUE':
        return 'Sự cố thanh toán / phụ thu sai quy định';
      default:
        return reason || 'Khác';
    }
  };

  if (isLoading) {
    return (
      <div className="w-full max-w-5xl mx-auto py-8 space-y-6 ">
        <div className="h-8 bg-slate-100 rounded-lg w-1/4"></div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="h-64 bg-slate-100 rounded-xl md:col-span-2 border border-slate-200"></div>
          <div className="h-64 bg-slate-100 rounded-xl border border-slate-200"></div>
        </div>
      </div>
    );
  }

  if (!dispute) {
    return (
      <div className="w-full p-8 text-center space-y-3">
        <AlertTriangle className="w-8 h-8 text-rose-500 mx-auto" />
        <p className="text-sm font-bold text-slate-800">Không tìm thấy hồ sơ khiếu nại.</p>
        <button
          onClick={() => navigate('/vendor/disputes')}
          className="px-4 py-2 bg-white hover:bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold cursor-pointer"
        >
          Quay lại danh sách
        </button>
      </div>
    );
  }

  return (
    <div className="w-full bg-surface min-h-screen">
      <div className="w-full px-6 py-4 space-y-4">
        {/* Top Utility Header */}
        <div className="flex items-center gap-3 pb-3 border-b border-slate-200/80">
          <button
            onClick={() => navigate('/vendor/disputes')}
            className="p-2 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 transition-colors cursor-pointer shadow-xs"
            aria-label="Quay lại"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold text-slate-900 tracking-tight font-display">
                Chi tiết vụ việc #DIS-{dispute.id.slice(0, 8).toUpperCase()}
              </h1>
              {getStatusBadge(dispute.status)}
            </div>
            <p className="text-xs text-slate-500">
              Hồ sơ khiếu nại cho đơn hàng con #VE-{dispute.subOrderId.slice(0, 8).toUpperCase()}
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 items-start">
          {/* LEFT COLUMN: Main Investigation Steps */}
          <div className="lg:col-span-8 space-y-3.5">
            {/* Step 1: Customer Claim */}
            <div className="bg-white rounded-xl border border-slate-200/80 shadow-xs p-5 space-y-3">
              <div className="flex items-center gap-2 border-b border-slate-100 pb-2.5">
                <span className="w-5 h-5 rounded-md bg-rose-50 text-rose-700 flex items-center justify-center font-bold text-xs border border-rose-200/60">
                  1
                </span>
                <h2 className="text-sm font-bold text-slate-900 font-display">Nội dung khiếu nại từ du khách</h2>
              </div>

              <div className="space-y-2 text-xs">
                <div>
                  <span className="text-slate-500 font-medium">Lý do khiếu nại:</span>
                  <p className="font-semibold text-rose-700 mt-0.5">{getReasonLabel(dispute.reason)}</p>
                </div>

                <div>
                  <span className="text-slate-500 font-medium">Mô tả sự việc:</span>
                  <div className="mt-1 p-3.5 rounded-lg bg-slate-50 border border-slate-200/80 text-slate-800 leading-relaxed whitespace-pre-line text-xs">
                    {dispute.description || 'Không có mô tả chi tiết.'}
                  </div>
                </div>

                {dispute.evidenceUrls && dispute.evidenceUrls.length > 0 && (
                  <div>
                    <span className="text-slate-500 font-medium">Hình ảnh / Bằng chứng từ khách:</span>
                    <div className="flex items-center gap-2 mt-1.5 flex-wrap">
                      {dispute.evidenceUrls.map((url, i) => (
                        <a
                          key={i}
                          href={url}
                          target="_blank"
                          rel="noreferrer"
                          className="w-16 h-16 rounded-lg overflow-hidden border border-slate-200 hover:opacity-80 transition-opacity"
                        >
                          <img src={url} alt="Bằng chứng" className="w-full h-full object-cover" />
                        </a>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Step 2: Vendor Response */}
            <div className="bg-white rounded-xl border border-slate-200/80 shadow-xs p-5 space-y-3">
              <div className="flex items-center gap-2 border-b border-slate-100 pb-2.5">
                <span className="w-5 h-5 rounded-md bg-slate-100 text-slate-800 flex items-center justify-center font-bold text-xs border border-slate-200">
                  2
                </span>
                <h2 className="text-sm font-bold text-slate-900 font-display">Văn bản giải trình từ Nhà cung cấp</h2>
              </div>

              {dispute.vendorResponse && (
                <div className="p-3.5 rounded-lg bg-slate-50 border border-slate-200/80 text-xs space-y-1">
                  <div className="flex items-center justify-between text-slate-900 font-bold">
                    <span>Giải trình đã gửi:</span>
                    {dispute.vendorRespondedAt && (
                      <span className="text-[11px] text-slate-500 font-mono font-normal">
                        {new Date(dispute.vendorRespondedAt).toLocaleString('vi-VN')}
                      </span>
                    )}
                  </div>
                  <p className="text-slate-700 leading-relaxed whitespace-pre-line">{dispute.vendorResponse}</p>
                </div>
              )}

              {dispute.status !== 'RESOLVED' && dispute.status !== 'CLOSED' && dispute.status !== 'RESOLVED_REFUND' && dispute.status !== 'RESOLVED_REJECTED' && dispute.status !== 'RESOLVED_PARTIAL' && (
                <form onSubmit={handleSubmitResponse} className="space-y-3 pt-1">
                  <label className="text-xs font-bold text-slate-700 block">
                    {dispute.vendorResponse ? 'Cập nhật lại văn bản giải trình:' : 'Nhập giải trình của cơ sở:'}
                  </label>
                  <textarea
                    rows={4}
                    value={vendorResponseText}
                    onChange={(e) => setVendorResponseText(e.target.value)}
                    placeholder="Nêu rõ diễn biến thực tế, nguyên nhân khách quan, nhật ký điều phối tàu hoặc bằng chứng trao đổi với khách..."
                    className="w-full p-3 rounded-lg border border-slate-200 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-slate-900 resize-none leading-relaxed"
                    required
                  />
                  <button
                    type="submit"
                    disabled={isSubmitting || !vendorResponseText.trim()}
                    className="px-4 py-2 bg-slate-900 hover:bg-black text-white rounded-lg text-xs font-semibold shadow-xs cursor-pointer disabled:opacity-50 flex items-center gap-1.5 transition-all"
                  >
                    {isSubmitting ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
                    <span>{isSubmitting ? 'Đang gửi...' : 'Gửi văn bản giải trình'}</span>
                  </button>
                </form>
              )}
            </div>

            {/* Step 3: Admin Resolution */}
            <div className="bg-white rounded-xl border border-slate-200/80 shadow-xs p-5 space-y-3">
              <div className="flex items-center gap-2 border-b border-slate-100 pb-2.5">
                <span className="w-5 h-5 rounded-md bg-emerald-50 text-emerald-700 flex items-center justify-center font-bold text-xs border border-emerald-200/60">
                  3
                </span>
                <h2 className="text-sm font-bold text-slate-900 font-display">Phán quyết &amp; Kết luận từ Sàn DANASEA</h2>
              </div>

              {dispute.adminNote ? (
                <div className="p-3.5 rounded-lg bg-emerald-50/70 border border-emerald-200/70 text-emerald-900 text-xs space-y-1">
                  <div className="flex items-center gap-1.5 font-bold">
                    <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                    <span>Kết luận chính thức của Điều phối viên / Cảng vụ:</span>
                  </div>
                  <p className="leading-relaxed">{dispute.adminNote}</p>
                  {dispute.refundPercentage !== undefined && dispute.refundPercentage !== null && (
                    <p className="font-semibold pt-1">
                      Mức hoàn tiền chỉ định: <strong className="font-mono">{dispute.refundPercentage}%</strong>
                    </p>
                  )}
                </div>
              ) : (
                <div className="p-4 rounded-lg bg-slate-50 border border-slate-200/80 text-center text-xs text-slate-500 space-y-1">
                  <p className="font-medium text-slate-700">Vụ việc đang được xem xét và xử lý bởi Ban quản lý DANASEA.</p>
                  <p className="text-[11px] text-slate-400">Thời gian xử lý trung bình: 24h - 48h làm việc.</p>
                </div>
              )}
            </div>
          </div>

          {/* RIGHT COLUMN: Summary Card */}
          <div className="lg:col-span-4">
            <div className="bg-white rounded-xl border border-slate-200/80 shadow-xs p-5 space-y-4">
              <h3 className="text-sm font-bold text-slate-900 border-b border-slate-100 pb-2 font-display">
                Tóm tắt hồ sơ tranh chấp
              </h3>

              <div className="space-y-2.5 text-xs">
                <div className="flex justify-between items-center py-0.5">
                  <span className="text-slate-500">Mã khiếu nại:</span>
                  <span className="font-mono font-bold text-slate-900">#DIS-{dispute.id.slice(0, 8).toUpperCase()}</span>
                </div>
                <div className="flex justify-between items-center py-0.5">
                  <span className="text-slate-500">Đơn hàng con:</span>
                  <span className="font-mono font-bold text-slate-800">#VE-{dispute.subOrderId.slice(0, 8).toUpperCase()}</span>
                </div>
                <div className="flex justify-between items-center py-0.5">
                  <span className="text-slate-500">Mã đơn tổng:</span>
                  <span className="font-mono text-slate-600">#ORD-{dispute.orderId.slice(0, 8).toUpperCase()}</span>
                </div>
                <div className="flex justify-between items-center py-0.5">
                  <span className="text-slate-500">Ngày tạo:</span>
                  <span className="font-mono text-slate-600">{new Date(dispute.createdAt).toLocaleString('vi-VN')}</span>
                </div>
                <div className="flex justify-between items-center py-0.5">
                  <span className="text-slate-500">Trạng thái:</span>
                  {getStatusBadge(dispute.status)}
                </div>
              </div>

              <div className="p-3 bg-slate-50 rounded-lg border border-slate-200/80 text-[11px] text-slate-600 space-y-1">
                <div className="flex items-center gap-1 font-bold text-slate-800">
                  <ShieldCheck className="w-3.5 h-3.5 text-slate-700" />
                  <span>Quy trình bảo vệ đôi bên</span>
                </div>
                <p className="leading-relaxed">Mọi khoản thanh toán của chuyến đi bị khiếu nại sẽ được giữ an toàn tại tài khoản ủy thác cho tới khi có phán quyết cuối cùng.</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
