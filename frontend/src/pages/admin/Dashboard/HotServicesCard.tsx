import { Flame } from 'lucide-react';
import { AdminState } from '../../../components/admin/AdminUI';
import { formatNumber, type HotServiceItem } from './dashboardModel';

interface HotServicesCardProps {
  services?: HotServiceItem[] | null;
  loading: boolean;
  rangeLabel: string;
}

export function HotServicesCard({ services, loading, rangeLabel }: HotServicesCardProps) {
  const items = services ?? [];
  const empty = !loading && items.length === 0;

  return (
    <section className="admin-card admin-dashboard-ranking-block" aria-label="Dịch vụ được đặt nhiều">
      <div className="admin-dashboard-block-header">
        <div>
          <h2 className="admin-dashboard-block-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Flame size={17} strokeWidth={2.2} style={{ color: '#D97706' }} />
            Dịch vụ được đặt nhiều
          </h2>
          <p className="admin-dashboard-block-sub">{rangeLabel} · Top trải nghiệm có lượt đặt cao nhất</p>
        </div>
      </div>

      {loading ? (
        <AdminState compact kind="loading" title="Đang tải danh sách dịch vụ…" />
      ) : empty ? (
        <AdminState
          compact
          kind="empty"
          title="Chưa có lượt đặt dịch vụ trong kỳ"
          description="Chưa có bản ghi đặt chỗ nào được tạo trong khoảng thời gian đã chọn."
        />
      ) : (
        <div className="admin-ranking-list">
          {items.map((item, index) => {
            const rank = index + 1;
            const isTop1 = rank === 1;

            return (
              <div className="admin-ranking-row" key={item.serviceId}>
                <div className="admin-ranking-row-top">
                  <div className="admin-ranking-identity">
                    <span
                      className={`admin-rank-badge ${isTop1 ? 'admin-rank-badge--first' : ''}`}
                      aria-label={`Thứ hạng ${rank}`}
                    >
                      #{rank}
                    </span>
                    <div className="admin-ranking-names">
                      <strong className="admin-ranking-title" title={item.serviceName}>
                        {item.serviceName}
                      </strong>
                      {item.vendorName && (
                        <span className="admin-ranking-sub" title={item.vendorName}>
                          {item.vendorName}
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="admin-ranking-metric">
                    <strong className="admin-ranking-metric-num">
                      {formatNumber(item.bookingCount)}
                    </strong>
                    <span className="admin-ranking-metric-unit">lượt</span>
                  </div>
                </div>

                <div className="admin-ranking-bar-track">
                  <div
                    className="admin-ranking-bar-fill"
                    style={{
                      width: `${item.sharePercent}%`,
                      backgroundColor: isTop1 ? '#0B5D6F' : '#338192',
                    }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
