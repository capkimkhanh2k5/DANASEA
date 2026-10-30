import { Anchor, Compass, History, ShieldCheck, Waves, Wind } from 'lucide-react';
import type { ServiceStatus } from '../../../types';
import { AdminBadge } from '../../../components/admin/AdminUI';
import { AdminDisclosure } from '../../../components/admin/AdminWorkspace';
import type { ServiceRecord, ServiceReviewEvent } from './serviceApprovalModel';

function formatDateTime(value?: string) {
  if (!value) return 'Chưa có dữ liệu';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
}

export function ServiceStatusBadge({ status }: { status: ServiceStatus }) {
  const tone =
    status === 'ACTIVE' || status === 'APPROVED'
      ? 'teal'
      : status === 'PENDING_APPROVAL'
      ? 'amber'
      : status === 'REJECTED'
      ? 'rose'
      : 'neutral';

  const label =
    status === 'ACTIVE'
      ? 'Đang mở bán'
      : status === 'APPROVED'
      ? 'Đã duyệt'
      : status === 'PENDING_APPROVAL'
      ? 'Chờ thẩm định'
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
  demo: _demo,
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
            Đạt điều kiện mở bán (dữ liệu mẫu)
          </span>
        </div>
        <p className="admin-detail-note">
          Thông số sóng, gió và điều kiện biển do đơn vị vận hành khai báo trong hồ sơ mẫu; không thay thế kiểm định của cơ quan chức năng.
        </p>

        <div className="admin-services-safety-grid">
          <div className="admin-services-safety-cell">
            <div className="admin-services-safety-cell-header">
              <Waves size={16} aria-hidden="true" />
              <span>Ngưỡng sóng khai báo</span>
            </div>
            <strong>≤ {service.maxWaveM} mét</strong>
            <small>Ngưỡng khuyến cáo tạm dừng tour</small>
          </div>

          <div className="admin-services-safety-cell">
            <div className="admin-services-safety-cell-header">
              <Wind size={16} aria-hidden="true" />
              <span>Ngưỡng gió vận hành</span>
            </div>
            <strong>≤ {service.minWindKmh} km/h</strong>
            <small>Theo dõi điều kiện thời tiết thực tế</small>
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
            <dd>Tối đa {service.capacityPerSlot} khách/ca</dd>
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
          Ghi chú này được lưu trong phiên xem thử demo hiện tại.
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
