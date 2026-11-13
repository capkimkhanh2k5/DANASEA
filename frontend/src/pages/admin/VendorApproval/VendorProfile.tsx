import { FileText, History, Info } from 'lucide-react';
import type { BadgeTier, VerificationStatus } from '../../../types';
import { AdminBadge } from '../../../components/admin/AdminUI';
import { AdminButton, AdminDisclosure } from '../../../components/admin/AdminWorkspace';
import { formatTaxCode, type VendorRecord, type VendorReviewEvent } from './vendorApprovalModel';

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

export function VendorProfile({ vendor, timeline, tier, onTierChange, onPreview, onApproveDoc, onRejectDoc, isSubmitting, demo }: {
  vendor: VendorRecord; timeline: VendorReviewEvent[]; tier: BadgeTier;
  onTierChange: (tier: BadgeTier) => void; onPreview: (urlOrName: string, name?: string) => void;
  onApproveDoc?: (docId: string) => Promise<void>;
  onRejectDoc?: (docId: string, reason: string) => Promise<void>;
  isSubmitting?: boolean;
  demo: boolean;
}) {
  return <div className="admin-vendors-profile">
    <section className="admin-detail-section admin-vendors-profile-summary" aria-label="Thông tin chính">
      <div className="admin-vendors-profile-heading"><img src={vendor.avatarUrl} alt="" width="56" height="56" /><div><span className="admin-vendors-mono">{vendor.id}</span>
        <div className="admin-vendors-profile-badges"><VendorStatus status={vendor.verificationStatus} /><AdminBadge tone="neutral">{vendor.badgeTier}</AdminBadge></div></div></div>
      <p>{vendor.address}</p>
      {vendor.verificationStatus === 'REJECTED' && vendor.rejectionReason && (
        <div style={{ margin: '12px 0', padding: '10px 14px', borderRadius: '8px', background: '#fff1f2', border: '1px solid #fecdd3', color: '#9f1239', fontSize: '12.5px' }}>
          <strong>Lý do từ chối:</strong> {vendor.rejectionReason}
        </div>
      )}
      <dl className="admin-detail-grid"><div><dt>Email</dt><dd>{vendor.email || 'Chưa cập nhật'}</dd></div><div><dt>Điện thoại</dt><dd>{vendor.phone || 'Chưa cập nhật'}</dd></div>
        <div><dt>Mã số thuế</dt><dd>{formatTaxCode(vendor.taxCode) === 'Chưa cập nhật' ? <span style={{ fontStyle: 'italic', color: '#64748b' }}>Chưa cập nhật</span> : <span className="admin-vendors-mono">{vendor.taxCode}</span>}</dd></div><div><dt>Đánh giá</dt><dd>{vendor.ratingAvg} / 5 · {vendor.ratingCount} đánh giá</dd></div></dl>
    </section>

    <section className="admin-detail-section" aria-labelledby="vendor-documents-heading">
      <div className="admin-vendors-section-heading">
        <h3 id="vendor-documents-heading">Hồ sơ pháp lý & Chứng chỉ</h3>
        <span>{vendor.documents && vendor.documents.length > 0 ? `${vendor.documents.length} chứng từ CSDL` : 'Chưa có hồ sơ'}</span>
      </div>
      {vendor.documents && vendor.documents.length > 0 ? (
        <div className="admin-documents">
          {vendor.documents.map(doc => {
            const isLicense = doc.docType === 'BUSINESS_LICENSE';
            const docTitle = isLicense
              ? 'Giấy phép Đăng ký Kinh doanh (GPKD)'
              : (doc.docType === 'SAFETY_CERT' ? 'Chứng nhận An toàn Giao thông Đường thủy' : doc.docType);
            const statusLabel = doc.status === 'APPROVED' ? 'Đã duyệt' : (doc.status === 'PENDING' ? 'Chờ kiểm tra' : 'Từ chối');
            const statusTone = doc.status === 'APPROVED' ? 'teal' : (doc.status === 'PENDING' ? 'amber' : 'rose');

            return (
              <div key={doc.id} className="admin-vendors-document" style={{ cursor: 'default' }}>
                <FileText size={22} aria-hidden="true" className="admin-vendors-doc-icon" />
                <div className="admin-vendors-doc-info">
                  <div className="admin-vendors-doc-top">
                    <strong>{docTitle}</strong>
                    <AdminBadge tone={statusTone as 'teal' | 'amber' | 'rose'}>{statusLabel}</AdminBadge>
                  </div>
                  <small>Mã số hồ sơ: #{doc.id.slice(0, 8)} · Loại: {doc.docType}</small>
                  {doc.reviewedBy && (
                    <span className="admin-vendors-doc-meta">
                      Người duyệt: {doc.reviewedBy} {doc.reviewedAt ? `(${date(doc.reviewedAt)})` : ''}
                    </span>
                  )}
                  <div style={{ marginTop: '10px', display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      className="admin-vendors-preview-link"
                      onClick={() => onPreview(doc.fileUrl, docTitle)}
                      style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
                    >
                      🔍 Xem ảnh scan gốc
                    </button>
                    {!demo && doc.status === 'PENDING' && onApproveDoc && (
                      <button
                        type="button"
                        disabled={isSubmitting}
                        onClick={() => onApproveDoc(doc.id)}
                        style={{ marginLeft: 'auto', background: '#059669', color: '#fff', padding: '4px 10px', borderRadius: '6px', fontSize: '11px', fontWeight: 600, border: 'none', cursor: 'pointer' }}
                      >
                        ✔ Duyệt chứng từ
                      </button>
                    )}
                    {!demo && doc.status === 'PENDING' && onRejectDoc && (
                      <button
                        type="button"
                        disabled={isSubmitting}
                        onClick={() => {
                          const reason = window.prompt('Nhập lý do từ chối / yêu cầu bổ sung chứng từ:');
                          if (reason) onRejectDoc(doc.id, reason);
                        }}
                        style={{ background: '#f43f5e', color: '#fff', padding: '4px 10px', borderRadius: '6px', fontSize: '11px', fontWeight: 600, border: 'none', cursor: 'pointer' }}
                      >
                        ✖ Từ chối
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div style={{ padding: '24px 16px', textAlign: 'center', background: '#f8fafc', borderRadius: 8, border: '1px dashed #cbd5e1' }}>
          <FileText size={28} style={{ color: '#94a3b8', margin: '0 auto 8px', display: 'block' }} aria-hidden="true" />
          <p style={{ margin: '0 0 4px', fontSize: 13, fontWeight: 600, color: '#475569' }}>
            Đối tác chưa tải lên hồ sơ pháp lý
          </p>
          <span style={{ fontSize: 12, color: '#64748b' }}>
            Chưa có Giấy phép kinh doanh (GPKD) hoặc Chứng nhận an toàn đường thủy được ghi nhận trên hệ thống.
          </span>
        </div>
      )}
    </section>

    <AdminDisclosure title="Pháp lý & tài khoản thụ hưởng" description="Mã số thuế, ngân hàng và thông tin người thụ hưởng">
      <dl className="admin-detail-grid"><div><dt>Mã số thuế</dt><dd>{formatTaxCode(vendor.taxCode) === 'Chưa cập nhật' ? <span style={{ fontStyle: 'italic', color: '#64748b' }}>Chưa cập nhật</span> : <span className="admin-vendors-mono">{vendor.taxCode}</span>}</dd></div><div><dt>Ngân hàng</dt><dd>{vendor.bankName || 'Chưa cập nhật'}</dd></div>
        <div><dt>Số tài khoản</dt><dd className="admin-vendors-mono">{vendor.bankAccountNumber || 'Chưa cập nhật'}</dd></div><div><dt>Chủ tài khoản</dt><dd>{vendor.bankAccountHolder || 'Chưa cập nhật'}</dd></div></dl>
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
