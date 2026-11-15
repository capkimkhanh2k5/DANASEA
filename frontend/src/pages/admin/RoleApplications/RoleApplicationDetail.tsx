import { useState } from 'react';
import { Building2, CreditCard, User, AlertCircle, CheckCircle2, ShieldCheck, XCircle } from 'lucide-react';
import type { PartnerApplicationResponse } from '../../../types/partnerApplication';
import { AdminBadge } from '../../../components/admin/AdminUI';
import { AdminButton, AdminDisclosure } from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import { formatDate, getStatusTone, getStatusLabel } from './roleApplicationsModel';

interface RoleApplicationDetailProps {
  application: PartnerApplicationResponse;
  onApprove: (id: string) => Promise<void>;
  onReject: (id: string, reason: string) => Promise<void>;
  isSubmitting: boolean;
}

export function RoleApplicationDetail({
  application,
  onApprove,
  onReject,
  isSubmitting,
}: RoleApplicationDetailProps) {
  const [isApproveOpen, setIsApproveOpen] = useState(false);
  const [isRejectOpen, setIsRejectOpen] = useState(false);
  const [rejectionReason, setRejectionReason] = useState('');
  const [reasonError, setReasonError] = useState<string | null>(null);

  const handleOpenReject = () => {
    setRejectionReason('');
    setReasonError(null);
    setIsRejectOpen(true);
  };

  const handleConfirmApprove = async () => {
    await onApprove(application.id);
    setIsApproveOpen(false);
  };

  const handleConfirmReject = async () => {
    const trimmed = rejectionReason.trim();
    if (!trimmed) {
      setReasonError('Vui lòng nhập lý do từ chối để thông báo cho đối tác.');
      return;
    }
    if (trimmed.length < 5) {
      setReasonError('Lý do từ chối cần tối thiểu 5 ký tự để đảm bảo tính minh bạch.');
      return;
    }
    setReasonError(null);
    await onReject(application.id, trimmed);
    setIsRejectOpen(false);
  };

  return (
    <div className="admin-role-application-detail space-y-6">
      {/* Overview Card */}
      <section className="admin-detail-section" aria-label="Tổng quan đơn xét duyệt">
        <div className="flex items-start justify-between gap-4 pb-4 border-b border-slate-100">
          <div className="space-y-1">
            <span className="text-[11px] font-mono text-slate-500 uppercase tracking-wider">
              Mã đơn: {application.id}
            </span>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              {application.businessName}
            </h2>
            <p className="text-xs text-slate-600 flex items-center gap-1.5">
              <User size={14} className="text-slate-400 shrink-0" aria-hidden="true" />
              <span>Mã người dùng: </span>
              <span className="font-mono text-slate-800">{application.userId}</span>
            </p>
          </div>
          <AdminBadge tone={getStatusTone(application.status)}>
            {getStatusLabel(application.status)}
          </AdminBadge>
        </div>

        {/* Reason banner if rejected */}
        {application.status === 'REJECTED' && application.rejectionReason && (
          <div className="mt-4 p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-900 text-xs flex items-start gap-2.5" role="alert">
            <XCircle size={16} className="text-rose-600 shrink-0 mt-0.5" aria-hidden="true" />
            <div className="space-y-1">
              <strong className="font-bold text-rose-950">Lý do từ chối đã gửi:</strong>
              <p className="text-rose-800 leading-relaxed">{application.rejectionReason}</p>
            </div>
          </div>
        )}

        {/* Status banner if approved */}
        {application.status === 'APPROVED' && (
          <div className="mt-4 p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs flex items-start gap-2.5" role="status">
            <CheckCircle2 size={16} className="text-emerald-600 shrink-0 mt-0.5" aria-hidden="true" />
            <div className="space-y-0.5">
              <strong className="font-bold text-emerald-950">Đơn đã được phê duyệt thành công</strong>
              <p className="text-emerald-800 leading-relaxed">
                Tài khoản khách hàng đã được cấp quyền VENDOR. Đối tác cần hoàn thiện hồ sơ pháp lý tại Kênh đối tác trước khi đăng dịch vụ.
              </p>
            </div>
          </div>
        )}
      </section>

      {/* 6 Core Fields: Business & Banking Information */}
      <section className="admin-detail-section" aria-labelledby="business-info-heading">
        <div className="flex items-center gap-2 mb-3">
          <Building2 size={16} className="text-slate-600" aria-hidden="true" />
          <h3 id="business-info-heading" className="text-sm font-bold text-slate-900">
            Thông tin pháp nhân đăng ký
          </h3>
        </div>
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
          <div className="p-3 rounded-lg bg-slate-50 border border-slate-100">
            <dt className="text-slate-500 font-medium mb-1">Tên doanh nghiệp / Hộ kinh doanh</dt>
            <dd className="font-semibold text-slate-900">{application.businessName}</dd>
          </div>
          <div className="p-3 rounded-lg bg-slate-50 border border-slate-100">
            <dt className="text-slate-500 font-medium mb-1">Mã số thuế</dt>
            <dd className="font-mono font-bold text-slate-900">{application.taxCode || 'Chưa cung cấp'}</dd>
          </div>
          <div className="sm:col-span-2 p-3 rounded-lg bg-slate-50 border border-slate-100">
            <dt className="text-slate-500 font-medium mb-1">Địa chỉ trụ sở / Kinh doanh</dt>
            <dd className="font-medium text-slate-800 leading-relaxed">{application.address || 'Chưa cung cấp'}</dd>
          </div>
        </dl>
      </section>

      <section className="admin-detail-section" aria-labelledby="banking-info-heading">
        <div className="flex items-center gap-2 mb-3">
          <CreditCard size={16} className="text-slate-600" aria-hidden="true" />
          <h3 id="banking-info-heading" className="text-sm font-bold text-slate-900">
            Tài khoản ngân hàng đối soát
          </h3>
        </div>
        <dl className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
          <div className="p-3 rounded-lg bg-slate-50 border border-slate-100">
            <dt className="text-slate-500 font-medium mb-1">Ngân hàng thụ hưởng</dt>
            <dd className="font-semibold text-slate-900">{application.bankName || 'Chưa cung cấp'}</dd>
          </div>
          <div className="p-3 rounded-lg bg-slate-50 border border-slate-100">
            <dt className="text-slate-500 font-medium mb-1">Số tài khoản</dt>
            <dd className="font-mono font-bold text-slate-900">{application.bankAccountNumber || 'Chưa cung cấp'}</dd>
          </div>
          <div className="p-3 rounded-lg bg-slate-50 border border-slate-100">
            <dt className="text-slate-500 font-medium mb-1">Tên chủ tài khoản</dt>
            <dd className="font-semibold text-slate-900 uppercase">{application.bankAccountHolder || 'Chưa cung cấp'}</dd>
          </div>
        </dl>
      </section>

      {/* Metadata Disclosure */}
      <AdminDisclosure title="Lịch sử thẩm định & thời gian" description="Mã kiểm toán và mốc thời gian hệ thống">
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <div>
            <dt className="text-slate-500">Thời điểm gửi đơn ban đầu</dt>
            <dd className="font-medium text-slate-800">{formatDate(application.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Cập nhật gần nhất</dt>
            <dd className="font-medium text-slate-800">{formatDate(application.updatedAt)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Quản trị viên xét duyệt</dt>
            <dd className="font-mono text-slate-800">{application.reviewedBy || 'Chưa có'}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Thời điểm hoàn tất xét duyệt</dt>
            <dd className="font-medium text-slate-800">{formatDate(application.reviewedAt)}</dd>
          </div>
        </dl>
      </AdminDisclosure>

      {/* Action Decision Area */}
      {application.status === 'PENDING' && (
        <section className="pt-4 border-t border-slate-200" aria-label="Thao tác xét duyệt">
          <div className="flex flex-col sm:flex-row items-center justify-end gap-3">
            <AdminButton
              variant="danger"
              disabled={isSubmitting}
              onClick={handleOpenReject}
              className="w-full sm:w-auto"
            >
              Từ chối đơn
            </AdminButton>
            <AdminButton
              variant="primary"
              disabled={isSubmitting}
              onClick={() => setIsApproveOpen(true)}
              className="w-full sm:w-auto"
            >
              Phê duyệt chuyển VENDOR
            </AdminButton>
          </div>
        </section>
      )}

      {/* Approve Confirmation Dialog */}
      <AdminDialog
        open={isApproveOpen}
        onClose={() => setIsApproveOpen(false)}
        title="Xác nhận duyệt chuyển vai trò"
        description={`Bạn có chắc chắn muốn phê duyệt đơn đối tác của "${application.businessName}"?`}
        footer={
          <>
            <AdminButton onClick={() => setIsApproveOpen(false)}>Hủy</AdminButton>
            <AdminButton variant="primary" loading={isSubmitting} onClick={handleConfirmApprove}>
              Xác nhận phê duyệt
            </AdminButton>
          </>
        }
      >
        <div className="text-xs text-slate-600 space-y-2 py-2">
          <div className="p-3 rounded-lg bg-sky-50 border border-sky-100 text-sky-900 flex items-start gap-2">
            <ShieldCheck size={16} className="text-sky-600 shrink-0 mt-0.5" aria-hidden="true" />
            <div>
              <p className="font-semibold">Hệ quả nghiệp vụ:</p>
              <ul className="list-disc list-inside mt-1 space-y-0.5 text-sky-800">
                <li>Tài khoản người dùng sẽ được nâng cấp thành <strong>ROLE_VENDOR</strong>.</li>
                <li>Phiên đăng nhập cũ của khách hàng sẽ bị vô hiệu hóa để bảo đảm tính an toàn.</li>
                <li>Đối tác sau đó phải bổ sung Giấy phép kinh doanh &amp; Chứng chỉ cứu sinh an toàn (Quy trình 2) trước khi mở bán tour.</li>
              </ul>
            </div>
          </div>
        </div>
      </AdminDialog>

      {/* Reject Reason Dialog */}
      <AdminDialog
        open={isRejectOpen}
        onClose={() => setIsRejectOpen(false)}
        title="Từ chối đơn đăng ký đối tác"
        description={`Nhập lý do từ chối cho "${application.businessName}". Lý do này sẽ được hiển thị trực tiếp cho đối tác.`}
        footer={
          <>
            <AdminButton onClick={() => setIsRejectOpen(false)}>Quay lại</AdminButton>
            <AdminButton variant="danger" loading={isSubmitting} onClick={handleConfirmReject}>
              Xác nhận từ chối
            </AdminButton>
          </>
        }
      >
        <div className="space-y-3 py-2 text-xs">
          <label htmlFor="rejection-reason-input" className="block font-semibold text-slate-700">
            Lý do từ chối bắt buộc <span className="text-rose-500">*</span>
          </label>
          <textarea
            id="rejection-reason-input"
            rows={4}
            value={rejectionReason}
            onChange={(e) => {
              setRejectionReason(e.target.value);
              if (reasonError) setReasonError(null);
            }}
            placeholder="Ví dụ: Mã số thuế không tra cứu được trên cổng thông tin quốc gia, hoặc số tài khoản ngân hàng không khớp tên doanh nghiệp..."
            className={`w-full p-3 rounded-xl border text-xs text-slate-900 placeholder:text-slate-400 outline-none transition-all ${
              reasonError ? 'border-rose-400 focus:border-rose-500 bg-rose-50/20' : 'border-slate-300 focus:border-[#0284c7]'
            }`}
            aria-invalid={reasonError ? 'true' : 'false'}
            aria-describedby={reasonError ? 'rejection-error-msg' : undefined}
          />
          {reasonError && (
            <p id="rejection-error-msg" className="text-rose-600 text-[11px] font-semibold flex items-center gap-1" role="alert">
              <AlertCircle size={13} aria-hidden="true" />
              {reasonError}
            </p>
          )}
          <p className="text-[11px] text-slate-500">
            Khách hàng sẽ nhận được lý do này và có thể chỉnh sửa thông tin để nộp lại đơn xét duyệt.
          </p>
        </div>
      </AdminDialog>
    </div>
  );
}
