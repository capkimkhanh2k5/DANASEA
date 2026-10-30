import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowRight, ArrowUpRight, Building2, CalendarDays, CheckCircle2, CircleHelp, Clock3, CreditCard, FlaskConical, RefreshCw, RotateCcw, ScrollText, ShieldCheck, Wallet } from 'lucide-react';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import '../../../components/admin/admin.css';
import '../../../components/admin/adminInteractions.css';
import { ActionQueue } from './ActionQueue';
import { AnalyticsChart } from './AnalyticsChart';
import { AnimatedKpiCounter } from './AnimatedKpiCounter';
import { formatNumber, formatVND, getDateRange, mergeDailyReports, shortDate, summarizeMetrics, type DashboardData } from './dashboardModel';
import { useAdminDashboard } from './useAdminDashboard';
import { useAuthStore } from '../../../store/useAuthStore';

const emptyDemo: DashboardData = {
  bookings: [], revenue: [], recentOrders: [], errors: [],
  operations: { vendors: 0, services: 0, refunds: 0, disputes: 0, settlements: 0 },
};

export function AdminDashboard() {
  const [searchParams] = useSearchParams();
  const token = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const userRole = useAuthStore(state => state.user?.role);
  const isRealAdmin = Boolean(isAuthenticated && token && !token.startsWith('mock_jwt_token_') && userRole === 'ADMIN');
  const demo = Boolean((import.meta.env.DEV && searchParams.get('demo') === '1') || !isRealAdmin);
  const href = (path: string) => path + (demo ? '?demo=1' : '');
  const [days, setDays] = useState<7 | 30>(7);
  const [previewState, setPreviewState] = useState('normal');
  const { from, to } = getDateRange(days);
  const range = useMemo(() => ({ from, to }), [from, to]);
  const dashboard = useAdminDashboard(range, demo);
  const data = demo && previewState === 'empty' ? emptyDemo : dashboard.data;
  const loading = dashboard.loading || (demo && previewState === 'loading');
  const error = demo && previewState === 'error' ? 'Mô phỏng mất kết nối. Chọn “Bình thường” hoặc thử lại để tiếp tục.' : dashboard.error;
  const points = mergeDailyReports(data?.bookings ?? [], data?.revenue ?? [], range);
  const metrics = summarizeMetrics(points);
  const counts = data?.operations;
  const totalPending = counts ? Object.values(counts).reduce<number>((sum, count) => sum + (count ?? 0), 0) : 0;
  const missingCounts = !counts || Object.values(counts).some(count => count === null);
  const allCountsUnknown = !counts || Object.values(counts).every(count => count === null);
  const approvals = counts?.vendors != null && counts?.services != null ? counts.vendors + counts.services : null;
  const customerAttention = counts?.refunds != null && counts?.disputes != null ? counts.refunds + counts.disputes : null;
  const refresh = () => { setPreviewState('normal'); dashboard.reload(); };
  const rangeLabel = `${shortDate(range.from)} – ${shortDate(range.to)}/${range.to.slice(0, 4)}`;
  const bookingUnavailable = loading || !data?.bookings;
  const revenueUnavailable = loading || !data?.revenue;
  const completionRate = metrics.bookings > 0 ? ((metrics.completed / metrics.bookings) * 100).toFixed(1) : '0';
  const cancellationRate = metrics.bookings > 0 ? ((metrics.cancelled / metrics.bookings) * 100).toFixed(1) : '0';

  const kpis = [
    {
      label: 'Hoa hồng thực thu',
      numericValue: metrics.commission,
      formatter: formatVND,
      unavailable: revenueUnavailable,
      icon: Wallet,
      foot: revenueUnavailable ? 'Chờ dữ liệu báo cáo' : `10% doanh thu sàn từ ${formatVND(metrics.revenue)}`,
    },
    {
      label: 'Đơn hoàn thành',
      numericValue: metrics.completed,
      formatter: formatNumber,
      unavailable: bookingUnavailable,
      icon: CheckCircle2,
      foot: bookingUnavailable ? 'Chờ dữ liệu báo cáo' : `${completionRate}% trên tổng ${formatNumber(metrics.bookings)} đơn`,
    },
    {
      label: 'Đơn đã hủy',
      numericValue: metrics.cancelled,
      formatter: formatNumber,
      unavailable: bookingUnavailable,
      icon: RotateCcw,
      foot: bookingUnavailable ? 'Chờ dữ liệu báo cáo' : `${cancellationRate}% tỷ lệ hủy trong kỳ`,
    },
    {
      label: 'Tiền hoàn trong kỳ',
      numericValue: metrics.refunds,
      formatter: formatVND,
      unavailable: revenueUnavailable,
      icon: CreditCard,
      foot: revenueUnavailable ? 'Chờ dữ liệu báo cáo' : (metrics.refunds > 0 ? 'Đã đối soát hoàn trả cho khách' : 'Không phát sinh tiền hoàn'),
    },
  ];

  return <main className="admin-dashboard">
    <div className="admin-dashboard-toolbar" style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '10px', marginTop: '2px', marginBottom: '8px' }}>
      <button className="admin-button" onClick={refresh} disabled={loading} aria-label="Làm mới bảng điều khiển"><RefreshCw size={15} className={loading ? 'admin-spin' : ''} />Làm mới</button>
      <Link to={href('/admin/orders')} className="admin-button admin-button--primary">Quản lý đơn hàng<ArrowUpRight size={15} /></Link>
    </div>

    {demo && <div className="admin-data-banner"><FlaskConical size={17} /><span><strong>DEMO MODE</strong> · Dữ liệu mẫu, không kết nối Backend.</span>
      <Link to="/admin">Xem dữ liệu hệ thống</Link><label className="admin-demo-controls">Trạng thái xem thử<select aria-label="Trạng thái dữ liệu demo" value={previewState} onChange={event => setPreviewState(event.target.value)}><option value="normal">Bình thường</option><option value="loading">Đang tải</option><option value="empty">Dữ liệu trống</option><option value="error">Lỗi kết nối</option></select></label>
    </div>}

    {error ? <section className="admin-card"><AdminState kind="error" title={dashboard.needsSession ? 'Dữ liệu hệ thống cần phiên đăng nhập' : 'Chưa thể tải dữ liệu'} description={error}
      action={<div className="admin-page-actions">{dashboard.needsSession ? <Link to="/auth" className="admin-button admin-button--primary">Đăng nhập Admin</Link> : <button onClick={refresh} className="admin-button">Thử lại</button>}{import.meta.env.DEV && !demo && <Link to="/admin?demo=1" className="admin-button">Xem giao diện với dữ liệu demo<ArrowRight size={13} /></Link>}</div>} /></section>
      : <>
        {/* Khối 1 — Phân tích hoạt động kinh doanh */}
        <section className="admin-analytics-section" aria-labelledby="admin-analytics-heading">
          <div className="admin-section-heading">
            <div>
              <h2 id="admin-analytics-heading">Phân tích hoạt động kinh doanh <AdminBadge tone={demo ? 'amber' : 'teal'}>{demo ? 'Dữ liệu demo' : 'Báo cáo hệ thống'}</AdminBadge></h2>
              <p>Diễn biến đặt chỗ và tiền thu · {rangeLabel}</p>
            </div>
            <div className="admin-range" role="group" aria-label="Khoảng thời gian biểu đồ">
              {([7, 30] as const).map(value => <button key={value} aria-pressed={days === value} onClick={() => setDays(value)}>{value} ngày qua</button>)}
            </div>
          </div>
          <div className="admin-charts-grid">
            <AnalyticsChart points={points} metric="bookings" loading={loading} error={!data?.bookings} onRetry={refresh} />
            <AnalyticsChart points={points} metric="revenue" loading={loading} error={!data?.revenue} onRetry={refresh} />
          </div>
        </section>

        {/* Khối 2 — Hiệu quả kinh doanh */}
        <section className="admin-performance-section" aria-labelledby="admin-performance-heading">
          <div className="admin-section-heading">
            <div>
              <h2 id="admin-performance-heading">Hiệu quả kinh doanh</h2>
              <p><CalendarDays size={11} style={{ display: 'inline', verticalAlign: '-2px', marginRight: 4 }} />Chỉ số chi tiết theo kỳ · {rangeLabel} · Giờ Việt Nam</p>
            </div>
          </div>
          <div className="admin-kpi-grid" aria-label="Chỉ số hiệu quả kinh doanh" aria-busy={loading}>
            {kpis.map((kpi, index) => <article className="admin-card admin-kpi admin-kpi--staggered" key={kpi.label} style={{ animationDelay: `${index * 60}ms` }}>
              <div className="admin-kpi-top"><span>{kpi.label}</span><span><kpi.icon size={15} strokeWidth={1.7} /></span></div>
              <strong className="admin-kpi-value"><AnimatedKpiCounter value={kpi.numericValue} formatter={kpi.formatter} unavailable={kpi.unavailable} /></strong>
              <p className="admin-kpi-foot"><span className="admin-kpi-foot-indicator" aria-hidden="true" />{kpi.foot}</p>
            </article>)}
          </div>
        </section>

        {data && data.errors.length > 0 && <div className="admin-data-banner" role="alert"><CircleHelp size={16} /><span>Chưa tải được: {data.errors.join(', ')}. Các phần đã tải vẫn hiển thị.</span><button className="admin-button admin-button--text" onClick={refresh}>Thử lại</button></div>}

        {/* Khối 3 — Công việc cần xử lý */}
        <div className="admin-operations-grid">
          <ActionQueue demo={demo} counts={counts} loading={loading} onRetry={refresh} />
          <aside className="admin-side-stack" aria-label="Thông tin vận hành">
            <section className="admin-card admin-operations-summary">
              <div className="admin-panel-header"><div className="admin-panel-title"><h2>Tình hình vận hành</h2><Clock3 size={17} /></div><p>Ảnh chụp theo trạng thái hiện tại</p></div>
              <div className="admin-backlog-total"><strong>{loading || allCountsUnknown ? '—' : `${formatNumber(totalPending)}${missingCounts ? '+' : ''}`}</strong><span>mục đang chờ xử lý<br /><small>trên 5 nhóm nghiệp vụ</small></span></div>
              <div className="admin-overview-rows">
                <Link to={href('/admin/disputes')} className="admin-overview-row" title="Đi đến quản lý Khiếu nại và Hoàn tiền" aria-label={`P1 Cần chú ý: ${loading || customerAttention == null ? 'Chờ dữ liệu' : formatNumber(customerAttention)} mục`}>
                  <span>P1 · Cần chú ý ngay</span>
                  <strong>{loading || customerAttention == null ? '—' : `${formatNumber(customerAttention)} mục`}</strong>
                  <ArrowUpRight size={13} aria-hidden="true" />
                </Link>
                <Link to={href('/admin/vendor-approval')} className="admin-overview-row" title="Đi đến thẩm định Đối tác và Dịch vụ" aria-label={`P2 Kiểm duyệt: ${loading || approvals == null ? 'Chờ dữ liệu' : formatNumber(approvals)} mục`}>
                  <span>P2 · Kiểm duyệt hồ sơ</span>
                  <strong>{loading || approvals == null ? '—' : `${formatNumber(approvals)} mục`}</strong>
                  <ArrowUpRight size={13} aria-hidden="true" />
                </Link>
                <Link to={href('/admin/payouts')} className="admin-overview-row" title="Đi đến đối soát Quyết toán" aria-label={`P3 Quyết toán: ${loading || counts?.settlements == null ? 'Chờ dữ liệu' : formatNumber(counts.settlements)} kỳ`}>
                  <span>P3 · Kỳ quyết toán</span>
                  <strong>{loading || counts?.settlements == null ? '—' : `${formatNumber(counts.settlements)} kỳ`}</strong>
                  <ArrowUpRight size={13} aria-hidden="true" />
                </Link>
              </div>
            </section>
            <section className="admin-card admin-shortcuts"><div className="admin-panel-header"><div className="admin-panel-title"><h2>Truy cập nhanh</h2><ArrowUpRight size={16} /></div></div>
              <Link to={href('/admin/vendor-approval')}><Building2 size={17} /><span>Hồ sơ đối tác</span><ArrowRight size={14} /></Link>
              <Link to={href('/admin/settings')}><ShieldCheck size={17} /><span>Cấu hình & an toàn biển</span><ArrowRight size={14} /></Link>
              <Link to={href('/admin/logs')}><ScrollText size={17} /><span>Nhật ký hệ thống</span><ArrowRight size={14} /></Link>
            </section>
          </aside>
        </div>

        <p className="admin-description" style={{ margin: 0 }}>{demo ? 'Dữ liệu mẫu độc lập. KPI và biểu đồ sử dụng cùng bộ dữ liệu theo ngày.' : `Nguồn: API quản trị DANASEA${dashboard.loadedAt ? ` · Cập nhật lúc ${new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit' }).format(dashboard.loadedAt)}` : ''}.`}</p>
      </>}
  </main>;
}

export default AdminDashboard;
