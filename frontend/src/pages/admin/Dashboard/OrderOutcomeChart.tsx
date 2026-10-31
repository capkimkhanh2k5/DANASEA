import { useEffect, useRef, useState } from 'react';
import { BarChart3 } from 'lucide-react';
import gsap from 'gsap';
import { AdminState } from '../../../components/admin/AdminUI';
import { chartDomain, formatNumber, shortDate, type DailyMetric } from './dashboardModel';

interface OrderOutcomeChartProps {
  points: DailyMetric[];
  loading: boolean;
  error: boolean;
  onRetry: () => void;
}

export function OrderOutcomeChart({ points, loading, error, onRetry }: OrderOutcomeChartProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [activeDate, setActiveDate] = useState<string | null>(null);

  // Stacked totals per day
  const totals = points.map(p => p.completed + p.cancelled);
  const totalCompleted = points.reduce((sum, p) => sum + p.completed, 0);
  const totalCancelled = points.reduce((sum, p) => sum + p.cancelled, 0);
  const allZero = totals.every(t => t === 0);

  const { min, max } = chartDomain(totals);
  const left = 35;
  const top = 18;
  const width = 505;
  const height = 138;
  const step = width / (points.length || 1);
  const y = (val: number) => top + height - ((val - min) / (max - min || 1)) * height;
  const baseline = y(0);

  const plot = points.map((p, index) => {
    const x = left + step * (index + 0.5);
    const barWidth = Math.min(24, Math.max(8, step * 0.52));
    const completedHeight = ((p.completed) / (max - min || 1)) * height;
    const cancelledHeight = ((p.cancelled) / (max - min || 1)) * height;
    const completedY = baseline - completedHeight;
    const cancelledY = completedY - cancelledHeight;

    return {
      point: p,
      x,
      barWidth,
      completedHeight,
      cancelledHeight,
      completedY,
      cancelledY,
    };
  });

  const active = plot.find(p => p.point.date === activeDate);

  useEffect(() => {
    if (loading || error || allZero || !svgRef.current) return;
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    const ctx = gsap.context(() => {
      const bars = svgRef.current?.querySelectorAll<SVGRectElement>('.admin-stacked-bar');
      if (bars && bars.length > 0) {
        bars.forEach((bar, i) => {
          const targetY = parseFloat(bar.getAttribute('data-target-y') || `${baseline}`);
          const targetHeight = parseFloat(bar.getAttribute('data-target-height') || '0');
          gsap.fromTo(
            bar,
            { attr: { y: baseline, height: 0 }, opacity: 0.4 },
            {
              attr: { y: targetY, height: targetHeight },
              opacity: 1,
              duration: 0.75,
              ease: 'power2.out',
              delay: (i % points.length) * (points.length <= 7 ? 0.05 : 0.015),
            }
          );
        });
      }
    }, svgRef);

    return () => ctx.revert();
  }, [points, loading, error, allZero, baseline]);

  return (
    <section className="admin-card admin-dashboard-chart-block" aria-label="Biểu đồ kết quả đơn hàng">
      <div className="admin-chart-header">
        <div>
          <h2 className="admin-dashboard-block-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <BarChart3 size={17} strokeWidth={2.2} style={{ color: '#0B5D6F' }} />
            Kết quả đơn hàng
          </h2>
          <p className="admin-dashboard-block-sub">Phân bổ đơn hoàn thành và đơn hủy theo ngày</p>
        </div>
        <div className="admin-chart-total-highlight">
          <div className="admin-chart-legend-row">
            <span className="admin-legend-item">
              <span className="admin-legend-dot" style={{ backgroundColor: '#0B5D6F' }} />
              Hoàn thành: <strong>{formatNumber(totalCompleted)}</strong>
            </span>
            <span className="admin-legend-item">
              <span className="admin-legend-dot" style={{ backgroundColor: '#D9534F' }} />
              Hủy: <strong>{formatNumber(totalCancelled)}</strong>
            </span>
          </div>
        </div>
      </div>

      {loading ? (
        <AdminState compact kind="loading" title="Đang tải dữ liệu đơn hàng…" />
      ) : error ? (
        <AdminState
          compact
          kind="error"
          title="Không tải được báo cáo đơn hàng"
          description="Vui lòng kiểm tra kết nối và thử lại."
          action={<button className="admin-button" onClick={onRetry}>Thử lại</button>}
        />
      ) : allZero ? (
        <AdminState
          compact
          kind="empty"
          title="Chưa có đơn hàng trong kỳ"
          description="Không phát sinh đơn hoàn thành hoặc hủy trong khoảng thời gian đã chọn."
        />
      ) : (
        <div className="admin-chart-plot" onPointerLeave={() => setActiveDate(null)}>
          {active && (
            <div
              className="admin-chart-tooltip"
              role="tooltip"
              style={{ left: `${Math.max(18, Math.min(82, (active.x / 560) * 100))}%` }}
            >
              <div className="admin-chart-tooltip-date">
                Ngày {shortDate(active.point.date)}/{active.point.date.slice(0, 4)}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', marginTop: '4px' }}>
                <span style={{ color: '#0B5D6F', fontWeight: 600 }}>
                  Hoàn thành: {active.point.completed} đơn
                </span>
                <span style={{ color: '#D9534F', fontWeight: 600 }}>
                  Hủy: {active.point.cancelled} đơn
                </span>
                <small style={{ color: '#64748B', borderTop: '1px solid #E2E8F0', paddingTop: '2px', marginTop: '2px' }}>
                  Tổng: {active.point.completed + active.point.cancelled} đơn
                </small>
              </div>
            </div>
          )}
          <svg
            ref={svgRef}
            viewBox="0 0 560 190"
            role="group"
            aria-label={`Biểu đồ kết quả đơn hàng ${points.length} ngày`}
          >
            {/* Grid lines and Y-axis labels */}
            {[0, 1, 2, 3, 4].map(index => {
              const val = min + ((max - min || 1) * index) / 4;
              const yPos = y(val);
              return (
                <g key={index}>
                  <line
                    x1={left}
                    x2={left + width}
                    y1={yPos}
                    y2={yPos}
                    stroke="#E2E8F0"
                    strokeDasharray={index === 0 ? undefined : '3 4'}
                  />
                  <text
                    x={left - 8}
                    y={yPos + 4}
                    textAnchor="end"
                    fill="#64748B"
                    fontSize="11"
                    fontFamily="inherit"
                  >
                    {Math.round(val)}
                  </text>
                </g>
              );
            })}

            {/* Stacked Bars */}
            {plot.map(({ point, x, barWidth, completedHeight, cancelledHeight, completedY, cancelledY }, index) => {
              const isActive = activeDate === point.date;
              return (
                <g
                  key={point.date}
                  tabIndex={0}
                  className="admin-chart-point"
                  role="img"
                  aria-label={`${point.date}: ${point.completed} hoàn thành, ${point.cancelled} hủy`}
                  onFocus={() => setActiveDate(point.date)}
                  onBlur={() => setActiveDate(null)}
                  onPointerEnter={() => setActiveDate(point.date)}
                  onPointerDown={() => setActiveDate(point.date)}
                >
                  {/* Invisible hit target covering column */}
                  <rect
                    className="admin-point-target"
                    x={x - step / 2}
                    y={top}
                    width={step}
                    height={height}
                    fill="transparent"
                  />

                  {/* Completed segment (bottom) */}
                  {completedHeight > 0 && (
                    <rect
                      className="admin-stacked-bar"
                      data-target-y={completedY}
                      data-target-height={completedHeight}
                      x={x - barWidth / 2}
                      y={completedY}
                      width={barWidth}
                      height={completedHeight}
                      rx={cancelledHeight > 0 ? 0 : 3}
                      fill="#0B5D6F"
                      opacity={isActive ? 1 : 0.88}
                      pointerEvents="none"
                    />
                  )}

                  {/* Cancelled segment (top) */}
                  {cancelledHeight > 0 && (
                    <rect
                      className="admin-stacked-bar"
                      data-target-y={cancelledY}
                      data-target-height={cancelledHeight}
                      x={x - barWidth / 2}
                      y={cancelledY}
                      width={barWidth}
                      height={cancelledHeight}
                      rx="3"
                      fill="#D9534F"
                      opacity={isActive ? 1 : 0.88}
                      pointerEvents="none"
                    />
                  )}

                  {/* X-axis date labels */}
                  {(points.length <= 7 || index % 5 === 0 || index === points.length - 1) && (
                    <text
                      x={x}
                      y={178}
                      textAnchor="middle"
                      fill="#64748B"
                      fontSize="11"
                      fontFamily="inherit"
                    >
                      {shortDate(point.date)}
                    </text>
                  )}
                </g>
              );
            })}
          </svg>
        </div>
      )}
    </section>
  );
}
