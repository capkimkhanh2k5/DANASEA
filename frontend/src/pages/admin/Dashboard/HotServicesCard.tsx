import { Award } from 'lucide-react';
import { AdminState } from '../../../components/admin/AdminUI';
import { formatNumber, formatVND, type TopServingVendorItem } from './dashboardModel';

interface HotServicesCardProps {
  vendors?: TopServingVendorItem[] | null;
  services?: unknown; // backward compatibility
  loading: boolean;
  rangeLabel: string;
}

export function HotServicesCard({ vendors, loading, rangeLabel }: HotServicesCardProps) {
  const items = vendors ?? [];
  const empty = !loading && items.length === 0;

  return (
    <section className="admin-card admin-dashboard-ranking-block" aria-label="Đối tác có nhiều lượt phục vụ nhất">
      <div className="admin-dashboard-block-header">
        <div>
          <h2 className="admin-dashboard-block-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Award size={17} strokeWidth={2.2} style={{ color: '#D97706' }} />
            Đối tác có nhiều lượt phục vụ nhất
          </h2>
          <p className="admin-dashboard-block-sub">{rangeLabel} · Top đối tác theo số đơn hoàn tất</p>
        </div>
      </div>

      {loading ? (
        <AdminState compact kind="loading" title="Đang tải dữ liệu đối tác…" />
      ) : empty ? (
        <AdminState
          compact
          kind="empty"
          title="Chưa có lượt phục vụ hoàn tất trong kỳ"
          description="Chưa có bản ghi đơn hoàn thành nào từ các đối tác trong khoảng thời gian đã chọn."
        />
      ) : (
        <div className="admin-ranking-list">
          {items.map((item, index) => {
            const rank = index + 1;
            const isTop1 = rank === 1;

            return (
              <div className="admin-ranking-row" key={item.vendorId}>
                <div className="admin-ranking-row-top">
                  <div className="admin-ranking-identity">
                    <span
                      className={`admin-rank-badge ${isTop1 ? 'admin-rank-badge--first' : ''}`}
                      aria-label={`Thứ hạng ${rank}`}
                    >
                      #{rank}
                    </span>
                    <div className="admin-ranking-names">
                      <strong className="admin-ranking-title" title={item.vendorName}>
                        {item.vendorName}
                      </strong>
                      <span className="admin-ranking-sub" title={`Doanh thu ghi nhận: ${formatVND(item.revenue)}`}>
                        Doanh thu: {formatVND(item.revenue)}
                      </span>
                    </div>
                  </div>
                  <div className="admin-ranking-metric">
                    <strong className="admin-ranking-metric-num">
                      {formatNumber(item.completedOrders)}
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
