import { Building2 } from 'lucide-react';
import { AdminState } from '../../../components/admin/AdminUI';
import { formatNumber, formatVND, type TopVendorItem } from './dashboardModel';

interface TopVendorsCardProps {
  vendors?: TopVendorItem[] | null;
  loading: boolean;
  rangeLabel: string;
}

export function TopVendorsCard({ vendors, loading, rangeLabel }: TopVendorsCardProps) {
  const items = vendors ?? [];
  const empty = !loading && items.length === 0;

  return (
    <section className="admin-card admin-dashboard-ranking-block" aria-label="Top 5 đối tác theo doanh thu">
      <div className="admin-dashboard-block-header">
        <div>
          <h2 className="admin-dashboard-block-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Building2 size={17} strokeWidth={2.2} style={{ color: '#0B5D6F' }} />
            Top 5 đối tác theo doanh thu
          </h2>
          <p className="admin-dashboard-block-sub">{rangeLabel} · Đóng góp doanh thu cao nhất cho sàn</p>
        </div>
      </div>

      {loading ? (
        <AdminState compact kind="loading" title="Đang tải danh sách đối tác…" />
      ) : empty ? (
        <AdminState
          compact
          kind="empty"
          title="Chưa có phát sinh doanh thu đối tác"
          description="Chưa có đơn hàng hoàn thành nào từ đối tác trong khoảng thời gian đã chọn."
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
                      <span className="admin-ranking-sub">
                        {formatNumber(item.completedOrders)} đơn hoàn thành
                      </span>
                    </div>
                  </div>
                  <div className="admin-ranking-metric">
                    <strong className="admin-ranking-metric-num" style={{ color: '#0B5D6F' }}>
                      {formatVND(item.revenue)}
                    </strong>
                    <span className="admin-ranking-metric-unit">doanh thu</span>
                  </div>
                </div>

                <div className="admin-ranking-bar-track">
                  <div
                    className="admin-ranking-bar-fill"
                    style={{
                      width: `${item.sharePercent}%`,
                      backgroundColor: isTop1 ? '#0B5D6F' : '#2D8192',
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
