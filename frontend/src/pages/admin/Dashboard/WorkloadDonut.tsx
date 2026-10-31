import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock3 } from 'lucide-react';
import { AdminState } from '../../../components/admin/AdminUI';
import { formatNumber, type OperationCounts, workloadGroups } from './dashboardModel';

interface WorkloadDonutProps {
  counts?: OperationCounts;
  loading: boolean;
  demo?: boolean;
}

export function WorkloadDonut({ counts, loading, demo = false }: WorkloadDonutProps) {
  const navigate = useNavigate();
  const [hoveredKey, setHoveredKey] = useState<string | null>(null);

  const href = (path: string) => `${path}${demo ? '?demo=1' : ''}`;

  // Exactly 4 groups
  const groups = workloadGroups.map(g => ({
    ...g,
    count: counts ? (counts[g.key] ?? 0) : 0,
    isUnknown: !counts || counts[g.key] === null,
  }));

  const totalCount = groups.reduce((sum, g) => sum + g.count, 0);
  const anyUnknown = !counts || groups.some(g => g.isUnknown);
  const allZero = !anyUnknown && totalCount === 0;

  // Donut SVG parameters - ~190px diameter
  const cx = 95;
  const cy = 95;
  const radius = 68;
  const strokeWidth = 24;
  const circumference = 2 * Math.PI * radius;

  const segments = groups.reduce<Array<(typeof groups)[number] & {
    fraction: number;
    strokeDasharray: string;
    strokeDashoffset: number;
    percent: string;
  }>>((acc, g) => {
    const fraction = totalCount > 0 ? g.count / totalCount : 0;
    const offsetFraction = acc.reduce((sum, item) => sum + item.fraction, 0);
    const strokeDasharray = `${fraction * circumference} ${circumference}`;
    const strokeDashoffset = -offsetFraction * circumference;
    const percent = totalCount > 0 ? ((g.count / totalCount) * 100).toFixed(1) : '0';

    acc.push({
      ...g,
      fraction,
      strokeDasharray,
      strokeDashoffset,
      percent,
    });
    return acc;
  }, []);

  const activeSegment = segments.find(s => s.key === hoveredKey);

  const handleNavigate = (path: string) => {
    navigate(href(path));
  };

  return (
    <section className="admin-card admin-dashboard-workload-block" aria-label="Công việc chờ xử lý">
      <div className="admin-dashboard-block-header">
        <h2 className="admin-dashboard-block-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Clock3 size={17} strokeWidth={2.2} style={{ color: '#0B5D6F' }} />
          Công việc chờ xử lý
        </h2>
      </div>

      {loading ? (
        <AdminState compact kind="loading" title="Đang kiểm tra công việc tồn đọng…" />
      ) : allZero ? (
        <AdminState
          compact
          kind="empty"
          title="Không có công việc đang chờ"
          description="Tất cả hồ sơ, dịch vụ, quyết toán và khiếu nại đã được xử lý xong."
        />
      ) : (
        <div className="admin-donut-wrapper">
          {/* SVG Donut Visual */}
          <div className="admin-donut-chart-container" onPointerLeave={() => setHoveredKey(null)}>
            {activeSegment && (
              <div className="admin-donut-tooltip" role="tooltip">
                <span className="admin-donut-tooltip-title">{activeSegment.label}</span>
                <strong>
                  {formatNumber(activeSegment.count)} mục · {activeSegment.percent}%
                </strong>
                <small>Nhấp để mở module xử lý</small>
              </div>
            )}

            <svg
              className="admin-donut-svg"
              viewBox="0 0 190 190"
              role="img"
              aria-label={`Biểu đồ tròn công việc chờ xử lý: tổng ${totalCount} mục`}
            >
              {/* Background circle track */}
              <circle
                cx={cx}
                cy={cy}
                r={radius}
                fill="none"
                stroke="#F1F5F9"
                strokeWidth={strokeWidth}
              />

              {/* Segments group rotated to start at top (12 o'clock) */}
              <g transform={`rotate(-90 ${cx} ${cy})`}>
                {segments.map(seg => {
                  if (seg.count <= 0) return null;
                  const isHovered = hoveredKey === seg.key;

                  return (
                    <circle
                      key={seg.key}
                      className="admin-donut-segment"
                      cx={cx}
                      cy={cy}
                      r={radius}
                      fill="none"
                      stroke={seg.color}
                      strokeWidth={isHovered ? strokeWidth + 4 : strokeWidth}
                      strokeDasharray={seg.strokeDasharray}
                      strokeDashoffset={seg.strokeDashoffset}
                      tabIndex={0}
                      role="button"
                      aria-label={`${seg.label}: ${seg.count} mục (${seg.percent}%). Nhấn Enter để mở.`}
                      onMouseEnter={() => setHoveredKey(seg.key)}
                      onFocus={() => setHoveredKey(seg.key)}
                      onBlur={() => setHoveredKey(null)}
                      onClick={() => handleNavigate(seg.path)}
                      onKeyDown={e => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          handleNavigate(seg.path);
                        }
                      }}
                      style={{
                        cursor: 'pointer',
                        transition: 'stroke-width 0.2s ease, opacity 0.2s ease',
                        opacity: hoveredKey && !isHovered ? 0.65 : 1,
                      }}
                    />
                  );
                })}
              </g>

              {/* Center counter */}
              <text
                x={cx}
                y={cy - 6}
                textAnchor="middle"
                className="admin-donut-center-value"
                fontFamily="inherit"
              >
                {anyUnknown ? `${totalCount}+` : totalCount}
              </text>
              <text
                x={cx}
                y={cy + 15}
                textAnchor="middle"
                className="admin-donut-center-label"
                fontFamily="inherit"
              >
                mục chờ
              </text>
            </svg>
          </div>

          {/* Interactive Legend List - Single Vertical Column (Right) */}
          <div className="admin-donut-legend-column" role="list" aria-label="Chi tiết 4 nhóm công việc chờ xử lý">
            {segments.map(seg => {
              const isHovered = hoveredKey === seg.key;

              return (
                <button
                  key={seg.key}
                  type="button"
                  role="listitem"
                  className={`admin-donut-legend-row ${isHovered ? 'admin-donut-legend-row--active' : ''}`}
                  onMouseEnter={() => setHoveredKey(seg.key)}
                  onMouseLeave={() => setHoveredKey(null)}
                  onClick={() => handleNavigate(seg.path)}
                  title={`Đi đến module ${seg.label} (${seg.count} mục chờ)`}
                >
                  <div className="admin-donut-legend-row-left">
                    <span className="admin-legend-dot" style={{ backgroundColor: seg.color }} aria-hidden="true" />
                    <span className="admin-donut-legend-name">{seg.label}</span>
                  </div>
                  <div className="admin-donut-legend-row-right">
                    <strong className="admin-donut-legend-count">
                      {seg.isUnknown ? '—' : `${formatNumber(seg.count)}`}
                    </strong>
                    <span className="admin-donut-legend-percent">{seg.percent}%</span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </section>
  );
}
