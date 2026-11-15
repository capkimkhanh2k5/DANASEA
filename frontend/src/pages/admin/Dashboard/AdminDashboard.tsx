import { useMemo } from 'react';
import { Link, useOutletContext, useSearchParams } from 'react-router-dom';
import { ArrowRight, CircleHelp } from 'lucide-react';
import { AdminState } from '../../../components/admin/AdminUI';
import '../../../components/admin/admin.css';
import '../../../components/admin/adminInteractions.css';
import './Dashboard.css';
import { getDateRange, mergeDailyReports, shortDate, summarizeMetrics } from './dashboardModel';
import { KpiOverview } from './KpiOverview';
import { OrderOutcomeChart } from './OrderOutcomeChart';
import { RevenueChart } from './RevenueChart';
import { useAdminDashboard } from './useAdminDashboard';
import { WorkloadDonut } from './WorkloadDonut';
import { ExecutiveDigestBanner } from './ExecutiveDigestBanner';

export function AdminDashboard() {
  const [searchParams] = useSearchParams();
  const demo = Boolean(import.meta.env.DEV && searchParams.get('demo') === '1');
  const outletCtx = useOutletContext<{ days: 7 | 30; setDays: (d: 7 | 30) => void }>() ?? {
    days: 7 as const,
    setDays: () => { },
  };
  const days = outletCtx.days ?? 7;

  const { from, to } = getDateRange(days);
  const range = useMemo(() => ({ from, to }), [from, to]);
  const dashboard = useAdminDashboard(range, demo);

  const data = dashboard.data;
  const loading = dashboard.loading;
  const error = dashboard.error;

  const points = mergeDailyReports(data?.bookings ?? [], data?.revenue ?? [], range);
  const metrics = summarizeMetrics(points);
  const counts = data?.operations;

  const refresh = () => {
    dashboard.reload();
  };

  const _rangeLabel = `${shortDate(range.from)} – ${shortDate(range.to)}/${range.to.slice(0, 4)}`;
  const bookingUnavailable = loading || !data?.bookings;
  const revenueUnavailable = loading || !data?.revenue;

  return (
    <main className="admin-dashboard-container">
      {/* Global Connection / Auth Error Banner */}
      {error ? (
        <section className="admin-card">
          <AdminState
            kind="error"
            title="Không thể tải dữ liệu điều khiển"
            description={error}
            action={
              <div style={{ display: 'inline-flex', gap: '8px', flexWrap: 'wrap' }}>
                <button onClick={refresh} className="admin-button">
                  Thử lại
                </button>
                {import.meta.env.DEV && !demo && (
                  <Link to="/admin?demo=1" className="admin-button">
                    Xem giao diện với dữ liệu demo
                    <ArrowRight size={13} />
                  </Link>
                )}
              </div>
            }
          />
        </section>
      ) : (
        <>
          {data && data.errors && data.errors.length > 0 && (
            <div className="admin-data-banner" role="alert">
              <CircleHelp size={16} />
              <span>Chưa tải được: {data.errors.join(', ')}. Các phần đã tải vẫn hiển thị.</span>
              <button className="admin-button admin-button--text" onClick={refresh}>
                Thử lại
              </button>
            </div>
          )}

          {/* 4 Box Tóm lược kinh doanh độc lập (Dynamic theo 7/30 ngày) */}
          <ExecutiveDigestBanner days={days} metrics={metrics} loading={loading} />

          {/* =========================================================================
              HÀNG 1: Block 1 (KPI Overview ~38%) + Block 2 (Revenue Trend ~62%)
              ========================================================================= */}
          <div className="admin-dashboard-row-1">
            {/* Block 1: Bốn KPI nhỏ gọn (2x2 grid) */}
            <KpiOverview
              metrics={metrics}
              loading={loading}
              bookingUnavailable={bookingUnavailable}
              revenueUnavailable={revenueUnavailable}
            />

            {/* Block 2: Xu hướng doanh thu (Line/Area Chart, không đường hoa hồng) */}
            <RevenueChart
              points={points}
              loading={loading}
              error={!data?.revenue}
              onRetry={refresh}
            />
          </div>

          {/* =========================================================================
              HÀNG 2: Kết quả đơn hàng (~58%) + Công việc chờ xử lý (~42%)
              ========================================================================= */}
          <div className="admin-dashboard-row-2">
            {/* Block 3: Kết quả đơn hàng (Stacked Bar Chart: Hoàn thành & Hủy) */}
            <OrderOutcomeChart
              points={points}
              loading={loading}
              error={!data?.bookings}
              onRetry={refresh}
            />

            {/* Block 4: Công việc chờ xử lý (Donut 4 nhóm, tương tác click & hover) */}
            <WorkloadDonut
              counts={counts}
              loading={loading}
              demo={demo}
            />
          </div>
        </>
      )}
    </main>
  );
}

export default AdminDashboard;
