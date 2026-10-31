import { useEffect, useId, useRef, useState } from 'react';
import { TrendingUp } from 'lucide-react';
import gsap from 'gsap';
import { AdminState } from '../../../components/admin/AdminUI';
import { chartDomain, formatNumber, formatVND, shortDate, type DailyMetric } from './dashboardModel';

interface RevenueChartProps {
  points: DailyMetric[];
  loading: boolean;
  error: boolean;
  onRetry: () => void;
}

export function RevenueChart({ points, loading, error, onRetry }: RevenueChartProps) {
  const gradientId = useId();
  const svgRef = useRef<SVGSVGElement>(null);
  const [activeDate, setActiveDate] = useState<string | null>(null);

  const values = points.map(p => p.revenue);
  const totalRevenue = values.reduce((sum, v) => sum + v, 0);
  const empty = values.every(v => v === 0);

  const { min, max } = chartDomain(values);
  const left = 55;
  const top = 18;
  const width = 485;
  const height = 138;
  const step = width / (points.length || 1);
  const y = (value: number) => top + height - ((value - min) / (max - min || 1)) * height;

  const plot = points.map((point, index) => ({
    point,
    x: left + step * (index + 0.5),
    y: y(point.revenue),
  }));

  const line = plot.map((p, index) => `${index ? 'L' : 'M'}${p.x},${p.y}`).join(' ');
  const area = plot.length ? `${line} L${plot[plot.length - 1].x},${top + height} L${plot[0].x},${top + height} Z` : '';
  const active = plot.find(p => p.point.date === activeDate);

  const axisLabel = (value: number) => {
    if (Math.abs(value) >= 1e6) return `${formatNumber(value / 1e6)} tr`;
    if (Math.abs(value) >= 1000) return `${formatNumber(value / 1000)}k`;
    return formatNumber(value);
  };

  useEffect(() => {
    if (loading || error || empty || !svgRef.current) return;
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    const ctx = gsap.context(() => {
      const lineEl = svgRef.current?.querySelector<SVGPathElement>('.admin-revenue-line');
      const areaEl = svgRef.current?.querySelector<SVGPathElement>('.admin-revenue-area');
      const circles = svgRef.current?.querySelectorAll<SVGCircleElement>('.admin-revenue-circle');

      if (lineEl) {
        try {
          const length = lineEl.getTotalLength();
          lineEl.style.strokeDasharray = `${length}`;
          lineEl.style.strokeDashoffset = `${length}`;
          gsap.to(lineEl, {
            strokeDashoffset: 0,
            duration: 0.9,
            ease: 'power2.out',
          });
        } catch {
          // measurement fallback
        }
      }

      if (areaEl) {
        gsap.fromTo(areaEl, { opacity: 0 }, { opacity: 1, duration: 0.8, ease: 'power1.out', delay: 0.1 });
      }

      if (circles && circles.length > 0) {
        gsap.fromTo(
          circles,
          { scale: 0, opacity: 0, transformOrigin: 'center center' },
          {
            scale: 1,
            opacity: 1,
            duration: 0.35,
            ease: 'back.out(2)',
            stagger: points.length <= 7 ? 0.05 : 0.015,
            delay: 0.25,
          }
        );
      }
    }, svgRef);

    return () => ctx.revert();
  }, [points, loading, error, empty]);

  return (
    <section className="admin-card admin-dashboard-chart-block" aria-label="Biểu đồ xu hướng doanh thu">
      <div className="admin-chart-header">
        <div>
          <h2 className="admin-dashboard-block-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <TrendingUp size={17} strokeWidth={2.2} style={{ color: '#0B5D6F' }} />
            Xu hướng doanh thu
          </h2>
          <p className="admin-dashboard-block-sub">Tiền thu ghi nhận theo ngày · VNĐ</p>
        </div>
        <div className="admin-chart-total-highlight">
          <span className="admin-chart-total-label">Tổng doanh thu trong kỳ</span>
          <strong className="admin-chart-total-value">
            {loading || error ? '—' : formatVND(totalRevenue)}
          </strong>
        </div>
      </div>

      {loading ? (
        <AdminState compact kind="loading" title="Đang tải dữ liệu doanh thu…" />
      ) : error ? (
        <AdminState
          compact
          kind="error"
          title="Không tải được báo cáo doanh thu"
          description="Vui lòng kiểm tra kết nối và thử lại."
          action={<button className="admin-button" onClick={onRetry}>Thử lại</button>}
        />
      ) : empty ? (
        <AdminState
          compact
          kind="empty"
          title="Chưa phát sinh doanh thu trong kỳ"
          description="Không có giao dịch ghi nhận trong khoảng thời gian đã chọn."
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
              <strong className="admin-chart-tooltip-main">
                {formatVND(active.point.revenue)}
              </strong>
            </div>
          )}
          <svg
            ref={svgRef}
            viewBox="0 0 560 190"
            role="group"
            aria-label={`Biểu đồ xu hướng doanh thu ${points.length} ngày`}
          >
            <defs>
              <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#0B5D6F" stopOpacity="0.16" />
                <stop offset="100%" stopColor="#0B5D6F" stopOpacity="0.0" />
              </linearGradient>
            </defs>

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
                    {axisLabel(val)}
                  </text>
                </g>
              );
            })}

            {/* Area and Line */}
            <path className="admin-revenue-area" d={area} fill={`url(#${gradientId})`} />
            <path
              className="admin-revenue-line"
              d={line}
              fill="none"
              stroke="#0B5D6F"
              strokeWidth="2.5"
              strokeLinejoin="round"
              strokeLinecap="round"
            />

            {/* Points & Interactive hits */}
            {plot.map(({ point, x: pointX, y: pointY }, index) => (
              <g
                key={point.date}
                tabIndex={0}
                className="admin-chart-point"
                role="img"
                aria-label={`${point.date}: ${formatVND(point.revenue)}`}
                onFocus={() => setActiveDate(point.date)}
                onBlur={() => setActiveDate(null)}
                onPointerEnter={() => setActiveDate(point.date)}
                onPointerDown={() => setActiveDate(point.date)}
              >
                <rect
                  className="admin-point-target"
                  x={pointX - step / 2}
                  y={top}
                  width={step}
                  height={height}
                  fill="transparent"
                />
                <circle
                  className="admin-revenue-circle"
                  cx={pointX}
                  cy={pointY}
                  r={activeDate === point.date ? 5 : points.length <= 7 ? 3.5 : 2.5}
                  fill="white"
                  stroke="#0B5D6F"
                  strokeWidth={activeDate === point.date ? 2.5 : 1.8}
                  pointerEvents="none"
                />
                {(points.length <= 7 || index % 5 === 0 || index === points.length - 1) && (
                  <text
                    x={pointX}
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
            ))}
          </svg>
        </div>
      )}
    </section>
  );
}
