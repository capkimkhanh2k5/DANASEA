import { FileText, History, Info } from 'lucide-react';
import type { BadgeTier, VerificationStatus } from '../../../types';
import { AdminBadge } from '../../../components/admin/AdminUI';
import { AdminButton, AdminDisclosure } from '../../../components/admin/AdminWorkspace';
import type { VendorRecord, VendorReviewEvent } from './vendorApprovalModel';

function date(value?: string) {
  if (!value) return 'Chưa có dữ liệu';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
}

export function VendorStatus({ status }: { status: VerificationStatus }) {
  return <AdminBadge tone={status === 'PENDING' ? 'amber' : status === 'REJECTED' ? 'rose' : 'teal'}>
    {status === 'PENDING' ? 'Chờ duyệt' : status === 'REJECTED' ? 'Từ chối' : 'Đã duyệt'}
  </AdminBadge>;
}

export function VendorProfile({ vendor, timeline, tier, onTierChange, onPreview, demo }: {
  vendor: VendorRecord; timeline: VendorReviewEvent[]; tier: BadgeTier;
  onTierChange: (tier: BadgeTier) => void; onPreview: (name: string) => void; demo: boolean;
}) {
  return <div className="admin-vendors-profile">
    <section className="admin-detail-section admin-vendors-profile-summary" aria-label="Thông tin chính">
      <div className="admin-vendors-profile-heading"><img src={vendor.avatarUrl} alt="" width="56" height="56" /><div><span className="admin-vendors-mono">{vendor.id}</span>
        <div className="admin-vendors-profile-badges"><VendorStatus status={vendor.verificationStatus} /><AdminBadge tone="neutral">{vendor.badgeTier}</AdminBadge></div></div></div>
      <p>{vendor.address}</p>
      <dl className="admin-detail-grid"><div><dt>Email</dt><dd>{vendor.email || 'Chưa cập nhật'}</dd></div><div><dt>Điện thoại</dt><dd>{vendor.phone || 'Chưa cập nhật'}</dd></div>
        <div><dt>Mã số thuế</dt><dd className="admin-vendors-mono">{vendor.taxCode}</dd></div><div><dt>Đánh giá</dt><dd>{vendor.ratingAvg} / 5 · {vendor.ratingCount} đánh giá</dd></div></dl>
    </section>

    <section className="admin-detail-section" aria-labelledby="vendor-documents-heading"><div className="admin-vendors-section-heading"><h3 id="vendor-documents-heading">Tài liệu minh họa</h3><span>2 bản xem trước mẫu</span></div>
      <p className="admin-detail-note">Hai tên tệp cố định để xem thử giao diện, không phải chứng từ đã được đối tác tải lên hoặc xác thực.</p>
      <div className="admin-documents">{[
        {
          name: 'giay_phep_kinh_doanh_2024.pdf',
          detail: '2.8 MB · Mã mẫu: #GP-DANANG-2024',
          subMeta: 'Cấp bởi: Sở Kế hoạch & Đầu tư Đà Nẵng · Hạn: 12/2026',
          status: vendor.verificationStatus === 'REJECTED' ? 'Cần bổ sung' : 'Đã xác minh (mẫu)',
          statusTone: vendor.verificationStatus === 'REJECTED' ? 'rose' : 'teal',
          reason: vendor.verificationStatus === 'REJECTED' ? 'Khu vực bến thủy chưa được công bố hoạt động hợp chuẩn.' : undefined,
        },
        {
          name: 'chung_chi_cuu_ho_padi_rescue.pdf',
          detail: '4.1 MB · PADI Rescue Standard (mẫu)',
          subMeta: 'Tiêu chuẩn: PADI Rescue Diver & First Aid · Hạn: 08/2025',
          status: vendor.verificationStatus === 'PENDING' ? 'Chờ kiểm tra' : vendor.verificationStatus === 'REJECTED' ? 'Cần bổ sung' : 'Đã xác minh (mẫu)',
          statusTone: vendor.verificationStatus === 'PENDING' ? 'amber' : vendor.verificationStatus === 'REJECTED' ? 'rose' : 'teal',
          reason: vendor.verificationStatus === 'PENDING' ? (vendor.safetyCertificateStatus || 'Chờ đối soát danh sách cứu hộ viên trực ban theo QCVN.') : undefined,
        },
      ].map(document => <button type="button" key={document.name} onClick={() => onPreview(document.name)} className="admin-vendors-document" aria-haspopup="dialog">
        <FileText size={22} aria-hidden="true" className="admin-vendors-doc-icon" />
        <span className="admin-vendors-doc-info">
          <div className="admin-vendors-doc-top">
            <strong>{document.name}</strong>
            <AdminBadge tone={document.statusTone as 'teal' | 'amber' | 'rose'}>{document.status}</AdminBadge>
          </div>
          <small>{document.detail}</small>
          <span className="admin-vendors-doc-meta">{document.subMeta}</span>
          {document.reason && <span className="admin-vendors-doc-reason">Lý do chưa đạt: {document.reason}</span>}
        </span>
        <span className="admin-vendors-preview-link">Xem mẫu</span>
      </button>)}</div>
    </section>

    <AdminDisclosure title="Pháp lý & tài khoản thụ hưởng" description="Mã số thuế, ngân hàng và thông tin người thụ hưởng">
      <dl className="admin-detail-grid"><div><dt>Mã số thuế</dt><dd>{vendor.taxCode}</dd></div><div><dt>Ngân hàng</dt><dd>{vendor.bankName}</dd></div>
        <div><dt>Số tài khoản</dt><dd className="admin-vendors-mono">{vendor.bankAccountNumber}</dd></div><div><dt>Chủ tài khoản</dt><dd>{vendor.bankAccountHolder}</dd></div></dl>
    </AdminDisclosure>

    <AdminDisclosure title="Thông tin bổ sung & dữ liệu gốc" description="Định danh, ngày đăng ký và thông tin vận hành mẫu">
      <dl className="admin-detail-grid"><div><dt>Mã người dùng</dt><dd>{vendor.userId}</dd></div><div><dt>Ngày tạo</dt><dd>{date(vendor.createdAt)}</dd></div>
        <div><dt>Ngày đăng ký</dt><dd>{date(vendor.registrationDate)}</dd></div><div><dt>Cập nhật gần nhất</dt><dd>{date(vendor.updatedAt)}</dd></div>
        <div><dt>Dịch vụ đang hoạt động (mẫu)</dt><dd>{vendor.activeServicesCount ?? 'Chưa có dữ liệu'}</dd></div>
        <div><dt>Doanh thu (mẫu)</dt><dd>{vendor.totalRevenue === undefined ? 'Chưa có dữ liệu' : `${vendor.totalRevenue.toLocaleString('vi-VN')} ₫`}</dd></div>
        <div><dt>Người duyệt trong dữ liệu</dt><dd>{vendor.verifiedBy || 'Chưa có dữ liệu'}</dd></div><div><dt>Thời điểm duyệt trong dữ liệu</dt><dd>{date(vendor.verifiedAt)}</dd></div>
        <div><dt>Mô tả chứng nhận trong dữ liệu mẫu</dt><dd>{vendor.safetyCertificateStatus || 'Chưa có dữ liệu'}</dd></div></dl>
      <p className="admin-detail-note">Các mô tả và trạng thái gốc chỉ là dữ liệu mẫu, không chứng minh tài liệu còn hiệu lực.</p>
    </AdminDisclosure>

    <section className="admin-detail-section" aria-labelledby="vendor-review-heading"><h3 id="vendor-review-heading">Quyết định & huy hiệu</h3>
      <label className="admin-field"><span>Hạng huy hiệu khi phê duyệt</span><select disabled={!demo} value={tier} onChange={event => onTierChange(event.target.value as BadgeTier)}>
        {['BRONZE', 'SILVER', 'GOLD', 'PLATINUM'].map(value => <option key={value}>{value}</option>)}</select></label>
      <div className="admin-vendors-supplement"><AdminButton disabled aria-describedby="vendor-supplement-note">Yêu cầu bổ sung</AdminButton>
        <p id="vendor-supplement-note"><Info size={15} aria-hidden="true" />Chưa có chức năng gửi yêu cầu bổ sung. Bạn có thể ghi chú khi thử quyết định; không gửi đến đối tác.</p></div>
    </section>

    <section className="admin-detail-section" aria-labelledby="vendor-timeline-heading"><div className="admin-vendors-section-heading"><h3 id="vendor-timeline-heading"><History size={18} aria-hidden="true" />Lịch sử thao tác cục bộ</h3></div>
      <p className="admin-detail-note">Chỉ ghi lại thao tác demo trong phiên này. Không phải nhật ký thẩm định của hệ thống.</p>
      {timeline.length === 0 ? <p className="admin-vendors-timeline-empty">Chưa có thao tác trong phiên. Thông tin người duyệt ban đầu nằm trong phần dữ liệu gốc.</p>
        : <ol className="admin-timeline">{[...timeline].reverse().map((event, index) => <li key={`${event.at}-${index}`}><div><strong>{event.kind === 'approve' ? `Phê duyệt demo · ${event.tier}` : 'Từ chối demo'}</strong>
          <time dateTime={event.at}>{date(event.at)}</time></div><span>Admin xem thử · Trạng thái trước: {event.previousStatus}</span>
          <p>{event.note || 'Không có ghi chú.'}</p></li>)}</ol>}
    </section>
  </div>;
}
