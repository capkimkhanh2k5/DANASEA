import { Link } from 'react-router-dom';
import { AlertTriangle, Award, CheckCircle2 } from 'lucide-react';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import { BADGE_TIER_VALUES, getBadgeTierLabel, getBadgeTierTone } from '../../../utils/badgeTier';
import type { BadgeRiskAlert, VendorBadgeDistribution } from './dashboardModel';

interface BadgeQualityCardProps {
  distribution?: VendorBadgeDistribution | null;
  atRiskAlerts?: BadgeRiskAlert[];
  loading: boolean;
}

export function BadgeQualityCard({ distribution, atRiskAlerts = [], loading }: BadgeQualityCardProps) {
  const totalVendors = distribution
    ? distribution.TOP_RATED + distribution.VERIFIED + distribution.NONE
    : 0;

  return (
    <section className="admin-card admin-dashboard-ranking-block" aria-label="Phân bổ huy hiệu và cảnh báo chất lượng đối tác">
      <div className="admin-dashboard-block-header">
        <div>
          <h2 className="admin-dashboard-block-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Award size={17} strokeWidth={2.2} style={{ color: '#0B5D6F' }} />
            Chất lượng &amp; Phân bổ Huy hiệu Đối tác
          </h2>
          <p className="admin-dashboard-block-sub">
            Theo dõi cơ cấu 3 hạng uy tín và cảnh báo sớm đối tác hàng đầu có nguy cơ rớt hạng
          </p>
        </div>
        <Link to="/admin/vendor-approval" className="admin-button admin-button--text" style={{ fontSize: '12px' }}>
          Quản lý đối tác
        </Link>
      </div>

      {loading ? (
        <AdminState compact kind="loading" title="Đang tải phân bổ huy hiệu đối tác…" />
      ) : !distribution ? (
        <AdminState
          compact
          kind="empty"
          title="Chưa có dữ liệu phân bổ huy hiệu"
          description="Hệ thống chưa tải được thống kê hạng uy tín đối tác từ máy chủ."
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: '10px' }}>
            {BADGE_TIER_VALUES.slice().reverse().map((tier) => {
              const count = distribution[tier];
              const pct = totalVendors > 0 ? Math.round((count / totalVendors) * 100) : 0;
              return (
                <div
                  key={tier}
                  style={{
                    padding: '12px',
                    borderRadius: '10px',
                    border: '1px solid #e2e8f0',
                    backgroundColor: '#f8fafc',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '6px',
                  }}
                >
                  <AdminBadge tone={getBadgeTierTone(tier)}>{getBadgeTierLabel(tier)}</AdminBadge>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px' }}>
                    <strong style={{ fontSize: '20px', color: '#0f172a', fontVariantNumeric: 'tabular-nums' }}>
                      {count}
                    </strong>
                    <span style={{ fontSize: '12px', color: '#64748b' }}>
                      đối tác ({pct}%)
                    </span>
                  </div>
                </div>
              );
            })}
          </div>

          <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
              <span style={{ fontSize: '12.5px', fontWeight: 700, color: '#334155', display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                <AlertTriangle size={14} style={{ color: atRiskAlerts.length > 0 ? '#d97706' : '#64748b' }} />
                Cảnh báo nguy cơ rớt hạng ({atRiskAlerts.length})
              </span>
              <span style={{ fontSize: '11.5px', color: '#64748b' }}>Ngưỡng: &lt; 4.65★ hoặc hủy &gt; 15%</span>
            </div>

            {atRiskAlerts.length === 0 ? (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  padding: '10px 12px',
                  borderRadius: '8px',
                  backgroundColor: '#f0fdf4',
                  border: '1px solid #bbf7d0',
                  color: '#166534',
                  fontSize: '12.5px',
                }}
              >
                <CheckCircle2 size={15} />
                <span>Tất cả Đối tác hàng đầu đang duy trì chất lượng ổn định.</span>
              </div>
            ) : (
              <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {atRiskAlerts.map((alert) => (
                  <li
                    key={`${alert.vendorId}-${alert.issueType}`}
                    style={{
                      padding: '10px 12px',
                      borderRadius: '8px',
                      backgroundColor: '#fffbeb',
                      border: '1px solid #fde68a',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '3px',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
                      <strong style={{ fontSize: '13px', color: '#92400e' }}>{alert.businessName}</strong>
                      <AdminBadge tone="amber">Nguy cơ rớt hạng</AdminBadge>
                    </div>
                    <span style={{ fontSize: '12px', color: '#78350f' }}>{alert.message}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
