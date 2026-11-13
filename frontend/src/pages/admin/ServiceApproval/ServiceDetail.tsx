import { useEffect, useState } from 'react';
import { Anchor, Compass, FileCheck, FileText, History, ShieldCheck, Waves, Wind } from 'lucide-react';
import type { ServiceStatus } from '../../../types';
import { AdminBadge } from '../../../components/admin/AdminUI';
import { AdminDisclosure } from '../../../components/admin/AdminWorkspace';
import type { ServiceRecord, ServiceReviewEvent } from './serviceApprovalModel';
import { adminServiceApprovalService } from '../../../services/adminServiceApprovalService';
import type { SafetyDocumentResponse } from '../../../types/adminServiceApproval';

function formatDateTime(value?: string) {
  if (!value) return 'Chưa có dữ liệu';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
}

export function ServiceStatusBadge({ status }: { status: ServiceStatus | string }) {
  const tone =
    status === 'ACTIVE' || status === 'APPROVED' || status === 'PUBLISHED'
      ? 'teal'
      : status === 'PENDING_APPROVAL' || status === 'PENDING_REVIEW'
      ? 'amber'
      : status === 'REJECTED'
      ? 'rose'
      : 'neutral';

  const label =
    status === 'ACTIVE' || status === 'PUBLISHED'
      ? 'Đang mở bán'
      : status === 'APPROVED'
      ? 'Đã duyệt'
      : status === 'PENDING_APPROVAL' || status === 'PENDING_REVIEW'
      ? 'Chờ duyệt'
      : status === 'REJECTED'
      ? 'Bị từ chối'
      : status === 'PAUSED'
      ? 'Tạm ngưng'
      : status === 'DRAFT'
      ? 'Bản nháp'
      : status === 'HIDDEN'
      ? 'Đang ẩn'
      : status;

  return <AdminBadge tone={tone}>{label}</AdminBadge>;
}

export function ServiceDetail({
  service,
  timeline,
  demo,
  adminNote,
  onAdminNoteChange,
}: {
  service: ServiceRecord;
  timeline: ServiceReviewEvent[];
  demo: boolean;
  adminNote: string;
  onAdminNoteChange: (note: string) => void;
}) {
  const price = service.price ?? service.basePrice ?? 0;
  const [safetyDocs, setSafetyDocs] = useState<SafetyDocumentResponse[]>([]);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [docActionId, setDocActionId] = useState<string | null>(null);

  useEffect(() => {
    if (demo || !service?.id) return;
    let isMounted = true;
    setLoadingDocs(true);
    adminServiceApprovalService.getSafetyDocuments(service.id)
      .then(docs => {
        if (isMounted) setSafetyDocs(docs);
      })
      .catch(() => {
        if (isMounted) setSafetyDocs([]);
      })
      .finally(() => {
        if (isMounted) setLoadingDocs(false);
      });
    return () => { isMounted = false; };
  }, [service?.id, demo]);

  const handleApproveDoc = async (docId: string) => {
    try {
      setDocActionId(docId);
      const updated = await adminServiceApprovalService.approveSafetyDocument(service.id, docId);
      setSafetyDocs(prev => prev.map(d => d.id === docId ? updated : d));
    } catch (e) {
      console.error(e);
    } finally {
      setDocActionId(null);
    }
  };

  const handleRejectDoc = async (docId: string) => {
    const reason = window.prompt('Nhập lý do từ chối chứng chỉ an toàn:') || '';
    if (!reason.trim()) return;
    try {
      setDocActionId(docId);
      const updated = await adminServiceApprovalService.rejectSafetyDocument(service.id, docId, reason.trim());
      setSafetyDocs(prev => prev.map(d => d.id === docId ? updated : d));
    } catch (e) {
      console.error(e);
    } finally {
      setDocActionId(null);
    }
  };

  return (
    <div className="admin-services-detail">
      {/* 1. Header & Media Section */}
      <section className="admin-detail-section admin-services-hero-section" aria-label="Thông tin chính dịch vụ">
        {service.thumbnailUrl && (
          <div className="admin-services-hero-media">
            <img
              src={service.thumbnailUrl}
              alt={service.name}
              className="admin-services-hero-image"
            />
            <div className="admin-services-hero-badges">
              <ServiceStatusBadge status={service.status} />
            </div>
          </div>
        )}

        <div className="admin-services-title-block">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap', marginBottom: '6px' }}>
            <span className="admin-services-category-label">
              <Compass size={14} aria-hidden="true" /> {service.categoryName || 'Dịch vụ trải nghiệm biển'}
            </span>
            {service.weatherSensitive && (
              <AdminBadge tone="neutral">
                <Waves size={13} aria-hidden="true" /> Nhạy cảm thời tiết
              </AdminBadge>
            )}
          </div>
          <h3 className="admin-services-main-title">{service.name}</h3>
          {service.nameEn && <p className="admin-services-en-title">{service.nameEn}</p>}
        </div>

        <div className="admin-services-price-card">
          <div>
            <span className="admin-services-price-label">Đơn giá niêm yết</span>
            <div className="admin-services-price-value">
              <strong>{price.toLocaleString('vi-VN')} ₫</strong>
              <small>/ 01 khách</small>
            </div>
          </div>
          <div className="admin-services-vendor-quick">
            <span className="admin-services-vendor-label">Đơn vị vận hành</span>
            <strong>{service.vendorName || 'Chưa liên kết đối tác'}</strong>
          </div>
        </div>
      </section>

      {/* 2. Maritime Safety Evaluation Card */}
      <section className="admin-detail-section" aria-labelledby="service-safety-heading">
        <div className="admin-services-section-heading">
          <h4 id="service-safety-heading">
            <ShieldCheck size={18} aria-hidden="true" />
            Rà soát điều kiện vận hành biển (khai báo)
          </h4>
          <span className="admin-services-safety-status">
            <span className="admin-services-status-dot" aria-hidden="true" />
            Đạt điều kiện mở bán
          </span>
        </div>
        <p className="admin-detail-note">
          Thông số sóng, gió và điều kiện biển do đơn vị vận hành khai báo; chứng từ an toàn được kiểm tra thực tế theo quy chuẩn Cảng vụ.
        </p>

        <div className="admin-services-safety-grid">
          <div className="admin-services-safety-cell">
            <div className="admin-services-safety-cell-header">
              <Waves size={16} aria-hidden="true" />
              <span>Ngưỡng sóng khai báo</span>
            </div>
            <strong>
              {service.maxWaveM != null && service.maxWaveM > 0
                ? `≤ ${service.maxWaveM} mét`
                : 'Không giới hạn'}
            </strong>
            <small>
              {service.maxWaveM != null && service.maxWaveM > 0
                ? 'Ngưỡng khuyến cáo tạm dừng tour'
                : 'Chưa thiết lập giới hạn sóng'}
            </small>
          </div>

          <div className="admin-services-safety-cell">
            <div className="admin-services-safety-cell-header">
              <Wind size={16} aria-hidden="true" />
              <span>Ngưỡng gió vận hành</span>
            </div>
            <strong>
              {service.minWindKmh != null && service.minWindKmh > 0
                ? `≤ ${service.minWindKmh} km/h`
                : 'Không giới hạn'}
            </strong>
            <small>
              {service.minWindKmh != null && service.minWindKmh > 0
                ? 'Theo dõi điều kiện thời tiết thực tế'
                : 'Chưa thiết lập giới hạn gió'}
            </small>
          </div>
        </div>

        <div className="admin-services-safety-notice">
          <Anchor size={16} aria-hidden="true" />
          <span>
            {service.weatherSensitive
              ? 'Dịch vụ nhạy cảm thời tiết biển Đà Nẵng. Cần theo dõi diễn biến sóng gió trước mỗi ca khởi hành.'
              : 'Dịch vụ ven bờ theo điều kiện hoạt động khai báo.'}
          </span>
        </div>

        {/* Safety Documents attached */}
        <div style={{ marginTop: '16px', borderTop: '1px solid #e2e8f0', paddingTop: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
            <strong style={{ fontSize: '14px', color: '#1e293b', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <FileCheck size={16} /> Chứng chỉ an toàn biển ({safetyDocs.length})
            </strong>
          </div>
          {loadingDocs ? (
            <p className="admin-detail-note">Đang tải danh sách chứng chỉ an toàn...</p>
          ) : safetyDocs.length === 0 ? (
            <p className="admin-detail-note">
              {demo ? 'Dịch vụ mẫu – chưa liên kết tệp chứng nhận an toàn thực tế.' : 'Chưa có chứng từ an toàn nào được tải lên.'}
            </p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {safetyDocs.map((doc, idx) => (
                <div
                  key={doc.id || idx}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '10px 14px',
                    borderRadius: '8px',
                    border: '1px solid #e2e8f0',
                    background: '#f8fafc',
                    fontSize: '13px'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <FileText size={16} color="#0284c7" />
                    <div>
                      <a
                        href={doc.fileUrl}
                        target="_blank"
                        rel="noreferrer"
                        style={{ color: '#0284c7', fontWeight: 600, textDecoration: 'underline' }}
                      >
                        Chứng từ an toàn #{idx + 1}
                      </a>
                      {doc.rejectionReason && (
                        <div style={{ color: '#e11d48', fontSize: '12px' }}>
                          Lý do từ chối: {doc.rejectionReason}
                        </div>
                      )}
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <AdminBadge tone={doc.status === 'APPROVED' ? 'teal' : doc.status === 'REJECTED' ? 'rose' : 'amber'}>
                      {doc.status === 'APPROVED' ? 'Đã duyệt' : doc.status === 'REJECTED' ? 'Bị từ chối' : 'Chờ duyệt'}
                    </AdminBadge>
                    {doc.status === 'PENDING' && !demo && (
                      <div style={{ display: 'flex', gap: '4px' }}>
                        <button
                          type="button"
                          disabled={docActionId === doc.id}
                          onClick={() => handleApproveDoc(doc.id)}
                          style={{
                            fontSize: '12px',
                            padding: '4px 8px',
                            background: '#0284c7',
                            color: '#fff',
                            borderRadius: '4px',
                            border: 'none',
                            cursor: 'pointer'
                          }}
                        >
                          Duyệt
                        </button>
                        <button
                          type="button"
                          disabled={docActionId === doc.id}
                          onClick={() => handleRejectDoc(doc.id)}
                          style={{
                            fontSize: '12px',
                            padding: '4px 8px',
                            background: '#fff',
                            color: '#e11d48',
                            borderRadius: '4px',
                            border: '1px solid #e11d48',
                            cursor: 'pointer'
                          }}
                        >
                          Từ chối
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* 3. Operational Specs Grid */}
      <section className="admin-detail-section" aria-labelledby="service-specs-heading">
        <div className="admin-services-section-heading">
          <h4 id="service-specs-heading">Thông số vận hành & Định mức</h4>
        </div>
        <dl className="admin-detail-grid">
          <div>
            <dt>Thời lượng dịch vụ</dt>
            <dd>{service.durationMinutes} phút</dd>
          </div>
          <div>
            <dt>Định mức phục vụ</dt>
            <dd>
              {service.capacityPerSlot != null && service.capacityPerSlot > 0
                ? `Tối đa ${service.capacityPerSlot} khách/ca`
                : 'Theo cấu hình ca'}
            </dd>
          </div>
          <div>
            <dt>Khu vực bến xuất phát</dt>
            <dd>{service.locationName || 'Chưa cập nhật'}</dd>
          </div>
          <div>
            <dt>Tọa độ GPS hải trình</dt>
            <dd className="admin-services-mono">
              {service.latitude}° N, {service.longitude}° E
            </dd>
          </div>
          <div>
            <dt>Địa chỉ cụ thể</dt>
            <dd>{service.address || 'Chưa cập nhật'}</dd>
          </div>
          <div>
            <dt>Đánh giá & Quan tâm</dt>
            <dd>
              {service.avgRating ? `${service.avgRating} / 5 (${service.ratingCount} đánh giá)` : 'Chưa có đánh giá'} ·{' '}
              {service.viewCount || 0} lượt xem
            </dd>
          </div>
          <div>
            <dt>Ngày nộp hồ sơ</dt>
            <dd>{formatDateTime(service.createdAt)}</dd>
          </div>
          <div>
            <dt>Cập nhật gần nhất</dt>
            <dd>{formatDateTime(service.updatedAt)}</dd>
          </div>
        </dl>
      </section>

      {/* 4. Waiver Content Disclosure */}
      <AdminDisclosure
        title="Cam kết Miễn trừ Trách nhiệm (Waiver)"
        description="Nội dung điều khoản khách du lịch ký xác nhận an toàn trước khi khởi hành"
        defaultOpen={true}
      >
        <div className="admin-services-waiver-box">
          <p>{service.waiverContent || 'Chưa cập nhật cam kết miễn trừ trách nhiệm cho dịch vụ này.'}</p>
        </div>
      </AdminDisclosure>

      {/* 5. Detailed Description Disclosure */}
      <AdminDisclosure
        title="Mô tả chi tiết tour & Lộ trình trải nghiệm"
        description="Nội dung giới thiệu dịch vụ xuất bản tới du khách"
        defaultOpen={false}
      >
        <div className="admin-services-description-box">
          <h5>Tiếng Việt</h5>
          <p>{service.description || 'Chưa có mô tả chi tiết.'}</p>
          {service.descriptionEn && (
            <>
              <h5>English</h5>
              <p>{service.descriptionEn}</p>
            </>
          )}
        </div>
      </AdminDisclosure>

      {/* 6. Previous Rejection Reason if any */}
      {service.rejectionReason && (
        <section className="admin-detail-section admin-services-rejected-note" aria-label="Lý do từ chối trước đó">
          <h4>Lý do từ chối trong hồ sơ:</h4>
          <p>{service.rejectionReason}</p>
        </section>
      )}

      {/* 7. Reviewer Administrative Note */}
      <section className="admin-detail-section" aria-labelledby="service-note-heading">
        <h4 id="service-note-heading">Ghi chú rà soát của Quản trị viên</h4>
        <label className="admin-field">
          <span>Nội dung ghi chú rà soát</span>
          <textarea
            value={adminNote}
            onChange={e => onAdminNoteChange(e.target.value)}
            rows={3}
            placeholder="Ghi chú điều kiện vận hành, phương án bảo hộ hoặc lưu ý duyệt..."
          />
        </label>
        <p className="admin-detail-note">
          {demo ? 'Ghi chú này được lưu trong phiên xem thử demo hiện tại.' : 'Ghi chú rà soát nội bộ phục vụ biên bản thẩm định.'}
        </p>
      </section>

      {/* 8. Local Session Timeline */}
      <section className="admin-detail-section" aria-labelledby="service-timeline-heading">
        <div className="admin-services-section-heading">
          <h4 id="service-timeline-heading">
            <History size={18} aria-hidden="true" />
            Lịch sử thao tác cục bộ
          </h4>
        </div>
        <p className="admin-detail-note">
          Chỉ ghi lại thao tác kiểm duyệt trong phiên làm việc này. Dữ liệu sẽ khôi phục khi tải lại trang.
        </p>
        {timeline.length === 0 ? (
          <p className="admin-services-timeline-empty">
            Chưa có thao tác trong phiên. Chọn phê duyệt, tạm ngưng hoặc từ chối để thử nghiệm quy trình.
          </p>
        ) : (
          <ol className="admin-timeline">
            {[...timeline].reverse().map((event, index) => (
              <li key={`${event.at}-${index}`}>
                <div>
                  <strong>
                    {event.kind === 'approve'
                      ? 'Phê duyệt xuất bản (demo)'
                      : event.kind === 'pause'
                      ? 'Tạm ngưng mở bán (demo)'
                      : 'Từ chối duyệt (demo)'}
                  </strong>
                  <time dateTime={event.at}>{formatDateTime(event.at)}</time>
                </div>
                <span>
                  Admin xem thử · Trạng thái trước: <em>{event.previousStatus}</em>
                </span>
                {event.reason && (
                  <p className="admin-services-timeline-reason">
                    <strong>Lý do từ chối:</strong> {event.reason}
                  </p>
                )}
                {event.note && <p>{event.note}</p>}
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}
