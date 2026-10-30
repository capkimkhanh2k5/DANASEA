import { useEffect, useId, useRef, useState } from 'react';
import { ChartColumnIncreasing, TrendingUp } from 'lucide-react';
import gsap from 'gsap';
import { AdminState } from '../../../components/admin/AdminUI';
import { chartDomain, formatNumber, formatVND, shortDate, type DailyMetric } from './dashboardModel';

export function AnalyticsChart({ points, metric, loading, error, onRetry }: {
  points: DailyMetric[]; metric: 'bookings' | 'revenue'; loading: boolean; error: boolean; onRetry: () => void;
}) {
  const gradientId = useId();
  const svgRef = useRef<SVGSVGElement>(null);
  const [activeDate, setActiveDate] = useState<string | null>(null);
  const isRevenue = metric === 'revenue';
  const title = isRevenue ? 'Xu hướng tiền thu' : 'Xu hướng đặt chỗ';
  const Icon = isRevenue ? TrendingUp : ChartColumnIncreasing;
  const values = points.map(point => point[metric]);
  const total = values.reduce((sum, value) => sum + value, 0);
  const empty = values.every(value => value === 0);
  const format = isRevenue ? formatVND : formatNumber;
  const { min, max } = chartDomain(values);
  const left = 47, top = 20, width = 491, height = 145;
  const step = width / (points.length || 1);
  const y = (value: number) => top + height - ((value - min) / (max - min)) * height;
  const y0 = y(0);
  const plot = points.map((point, index) => ({ point, x: left + step * (index + .5), y: y(point[metric]) }));
  const line = plot.map((point, index) => `${index ? 'L' : 'M'}${point.x},${point.y}`).join(' ');
  const area = plot.length ? `${line} L${plot.at(-1)!.x},${y(0)} L${plot[0].x},${y(0)} Z` : '';
  const active = plot.find(point => point.point.date === activeDate);
  const axisLabel = (value: number) => isRevenue && Math.abs(value) >= 1e6 ? `${formatNumber(value / 1e6)} tr` : isRevenue && Math.abs(value) >= 1000 ? `${formatNumber(value / 1000)}k` : formatNumber(value);

  useEffect(() => {
    if (loading || error || empty || !svgRef.current) return;
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    const ctx = gsap.context(() => {
      if (isRevenue) {
        const lineEl = svgRef.current?.querySelector<SVGPathElement>('.admin-chart-line');
        const areaEl = svgRef.current?.querySelector<SVGPathElement>('.admin-chart-area');
        const circles = svgRef.current?.querySelectorAll<SVGCircleElement>('.admin-chart-circle');

        if (lineEl) {
          try {
            const length = lineEl.getTotalLength();
            lineEl.style.strokeDasharray = `${length}`;
            lineEl.style.strokeDashoffset = `${length}`;
            gsap.to(lineEl, {
              strokeDashoffset: 0,
              duration: 1.0,
              ease: 'power2.out',
            });
          } catch {
            // Fallback if SVG measurement is not ready
          }
        }

        if (areaEl) {
          gsap.fromTo(
            areaEl,
            { opacity: 0 },
            { opacity: 1, duration: 0.9, ease: 'power1.out', delay: 0.15 }
          );
        }

        if (circles && circles.length > 0) {
          gsap.fromTo(
            circles,
            { scale: 0, opacity: 0, transformOrigin: 'center center' },
            {
              scale: 1,
              opacity: 1,
              duration: 0.4,
              ease: 'back.out(2)',
              stagger: points.length <= 7 ? 0.05 : 0.018,
              delay: 0.3,
            }
          );
        }
      } else {
        const bars = svgRef.current?.querySelectorAll<SVGRectElement>('.admin-chart-bar');
        if (bars && bars.length > 0) {
          bars.forEach((bar, i) => {
            const targetY = plot[i]?.y ?? y0;
            const targetHeight = Math.abs(y0 - targetY);
            gsap.fromTo(
              bar,
              { attr: { y: y0, height: 0 }, opacity: 0.3 },
              {
                attr: { y: targetY, height: targetHeight },
                opacity: 1,
                duration: 0.85,
                ease: 'power2.out',
                delay: i * (points.length <= 7 ? 0.06 : 0.018),
              }
            );
          });
        }
      }
    }, svgRef);

    return () => ctx.revert();
  }, [points, isRevenue, loading, error, empty, y0, plot]);

  return <section className="admin-card admin-chart" aria-label={title}>
    <div className="admin-chart-header"><div><h3><Icon size={16} />{title}</h3><p>{isRevenue ? 'Tiền thu đã ghi nhận · VNĐ' : 'Đơn tạo mới · theo ngày'}</p></div>
      <div className="admin-chart-total">{loading || error ? '—' : format(total)}<small>Tổng trong kỳ</small></div>
    </div>
    {loading ? <AdminState compact kind="loading" title="Đang tải biểu đồ…" />
      : error ? <AdminState compact kind="error" title="Không tải được báo cáo" description="Vui lòng kiểm tra kết nối và thử lại." action={<button className="admin-button" onClick={onRetry}>Thử lại</button>} />
      : empty ? <AdminState compact kind="empty" title={isRevenue ? 'Chưa phát sinh tiền thu trong kỳ' : 'Chưa có đơn đặt chỗ trong kỳ'} description="Thử chọn khoảng thời gian khác để xem xu hướng." />
      : <>
        <div className="admin-chart-plot" onPointerLeave={() => setActiveDate(null)}>
          {active && <div className="admin-chart-tooltip" role="tooltip" style={{ left: `${Math.max(20, Math.min(80, active.x / 560 * 100))}%` }}>
            Ngày {shortDate(active.point.date)}/{active.point.date.slice(0, 4)}<strong>{format(active.point[metric])}{!isRevenue && ' đơn'}</strong>
            <small>{isRevenue ? `Hoa hồng: ${formatVND(active.point.commission)}` : `Hoàn thành: ${active.point.completed} · Hủy: ${active.point.cancelled}`}</small>
          </div>}
          <svg ref={svgRef} viewBox="0 0 560 199" role="group" aria-label={`${title}, ${points.length} ngày. Dùng Tab để xem từng ngày hoặc mở bảng số liệu.`}>
            <defs><linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#087f8c" stopOpacity=".23" /><stop offset="100%" stopColor="#087f8c" stopOpacity=".01" /></linearGradient></defs>
            {[0, 1, 2, 3, 4].map(index => {
              const value = min + ((max - min) * index / 4);
              return <g key={index}><line x1={left} x2={left + width} y1={y(value)} y2={y(value)} stroke="#e8eef1" strokeDasharray={value === 0 ? undefined : '3 4'} /><text x={left - 10} y={y(value) + 3} textAnchor="end" fill="#6a7f8b" fontSize="11">{axisLabel(value)}</text></g>;
            })}
            {isRevenue && <><path className="admin-chart-area" d={area} fill={`url(#${gradientId})`} /><path className="admin-chart-line" d={line} fill="none" stroke="#087f8c" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" /></>}
            {plot.map(({ point, x, y: pointY }, index) => <g key={point.date} tabIndex={0} className="admin-chart-point" role="img"
              aria-label={`${point.date}: ${format(point[metric])}${isRevenue ? '' : ' đơn'}`}
              onFocus={() => setActiveDate(point.date)} onBlur={() => setActiveDate(null)} onPointerEnter={() => setActiveDate(point.date)} onPointerDown={() => setActiveDate(point.date)}>
              <rect className="admin-point-target" x={x - step / 2} y={top} width={step} height={height} fill="transparent" />
              {!isRevenue && <rect className="admin-chart-bar" x={x - Math.min(13, step * .28)} y={Math.min(y(0), pointY)} width={Math.min(26, step * .56)} height={Math.abs(y(0) - pointY)} rx="3" fill={activeDate === point.date || index === plot.length - 1 ? '#00646f' : '#86bdc1'} pointerEvents="none" />}
              {isRevenue && <circle className="admin-chart-circle" cx={x} cy={pointY} r={activeDate === point.date ? 4.5 : points.length === 7 ? 3 : 0} fill="white" stroke="#087f8c" strokeWidth="2" pointerEvents="none" />}
              {(points.length <= 7 || index % 5 === 0 || index === points.length - 1) && <text x={x} y={187} textAnchor="middle" fill="#6a7f8b" fontSize="11">{shortDate(point.date)}</text>}
            </g>)}
          </svg>
        </div>
        <div className="admin-chart-legend"><span><i />{isRevenue ? 'Tiền thu' : 'Đơn đặt chỗ'}</span><span>{isRevenue ? 'Không trừ hoàn tiền và hoa hồng' : 'Trạng thái đơn tại thời điểm báo cáo'}</span></div>
        <details className="admin-chart-details"><summary>Xem bảng số liệu</summary><div><table className="admin-data-table"><caption className="sr-only">{title} theo ngày</caption><thead><tr><th scope="col">Ngày</th><th scope="col">{isRevenue ? 'Hoa hồng' : 'Hoàn thành'}</th><th scope="col">{isRevenue ? 'Tiền thu' : 'Số đơn'}</th></tr></thead><tbody>{points.map(point => <tr key={point.date}><td>{shortDate(point.date)}/{point.date.slice(0, 4)}</td><td>{isRevenue ? formatVND(point.commission) : formatNumber(point.completed)}</td><td>{format(point[metric])}</td></tr>)}</tbody></table></div></details>
      </>}
  </section>;
}
