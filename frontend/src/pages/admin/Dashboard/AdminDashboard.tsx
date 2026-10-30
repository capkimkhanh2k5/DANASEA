import { useMemo } from 'react';
import { Link, useOutletContext, useSearchParams } from 'react-router-dom';
import { ArrowRight, CircleHelp } from 'lucide-react';
import { AdminState } from '../../../components/admin/AdminUI';
import '../../../components/admin/admin.css';
import '../../../components/admin/adminInteractions.css';
import './Dashboard.css';
import { getDateRange, mergeDailyReports, shortDate, summarizeMetrics } from './dashboardModel';
import { HotServicesCard } from './HotServicesCard';
import { KpiOverview } from './KpiOverview';
import { OrderOutcomeChart } from './OrderOutcomeChart';
import { RevenueChart } from './RevenueChart';
import { TopVendorsCard } from './TopVendorsCard';
import { useAdminDashboard } from './useAdminDashboard';
import { WorkloadDonut } from './WorkloadDonut';

export function AdminDashboard() {
  const [searchParams] = useSearchParams();
  const demo = Boolean(import.meta.env.DEV && searchParams.get('demo') === '1');
  const outletCtx = useOutletContext<{ days: 7 | 30; setDays: (d: 7 | 30) => void }>() ?? {
    days: 7 as const,
    setDays: () => {},
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

  const rangeLabel = `${shortDate(range.from)} – ${shortDate(range.to)}/${range.to.slice(0, 4)}`;
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
              HÀNG 2: Block 3 (Order Outcome ~60%) + Block 4 (Hot Services ~40%)
              ========================================================================= */}
          <div className="admin-dashboard-row-2">
            {/* Block 3: Kết quả đơn hàng (Stacked Bar Chart: Hoàn thành & Hủy) */}
            <OrderOutcomeChart
              points={points}
              loading={loading}
              error={!data?.bookings}
              onRetry={refresh}
            />

            {/* Block 4: Dịch vụ đặt nhiều nhất (Top <= 5 trải nghiệm nổi bật) */}
            <HotServicesCard
              services={data?.hotServices}
              loading={loading}
              rangeLabel={rangeLabel}
            />
          </div>

          {/* =========================================================================
              HÀNG 3: Block 5 (Workload Donut ~40%) + Block 6 (Top 5 Vendors ~60%)
              ========================================================================= */}
          <div className="admin-dashboard-row-3">
            {/* Block 5: Công việc chờ xử lý (Donut 4 nhóm, tương tác click & hover, không lọc 7/30d) */}
            <WorkloadDonut
              counts={counts}
              loading={loading}
              demo={demo}
            />

            {/* Block 6: Top 5 đối tác theo doanh thu (Xếp hạng doanh thu trong kỳ) */}
            <TopVendorsCard
              vendors={data?.topVendors}
              loading={loading}
              rangeLabel={rangeLabel}
            />
          </div>

          <p className="admin-description" style={{ margin: '4px 0 0', fontSize: '12px' }}>
            {demo
              ? 'Dữ liệu mẫu độc lập. KPI, biểu đồ và xếp hạng sử dụng cùng bộ dữ liệu chuẩn hóa theo ngày. Donut phản ánh tồn đọng hiện tại.'
              : `Nguồn: API quản trị DANASEA${dashboard.loadedAt ? ` · Cập nhật lúc ${new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit' }).format(dashboard.loadedAt)}` : ''}.`}
          </p>
        </>
      )}
    </main>
  );
}

export default AdminDashboard;
