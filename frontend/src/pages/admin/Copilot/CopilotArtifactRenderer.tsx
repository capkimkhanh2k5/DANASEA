import React, { useState } from 'react';
import {
  Database,
  BarChart3,
  Building2,
  AlertTriangle,
  Scale,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  CloudRain,
  UserX,
  ShieldAlert,
  Info,
} from 'lucide-react';
import type { CopilotArtifact } from '../../../types/adminAi';
import {
  formatVND,
  formatCompactVND,
  formatPercent,
  formatNumber,
  extractRevenueChartData,
  extractVendorDiagnosticsData,
  extractCancellationBreakdownData,
  extractPlatformMetricsData,
  extractQueueOverviewData,
  extractDisputeBriefData,
} from './copilotArtifactHelpers';

interface ArtifactRendererProps {
  artifacts?: CopilotArtifact[];
}

export const CopilotArtifactRenderer: React.FC<ArtifactRendererProps> = ({ artifacts }) => {
  if (!artifacts || !Array.isArray(artifacts) || artifacts.length === 0) {
    return null;
  }

  return (
    <div className="admin-copilot-artifacts" role="region" aria-label="Dữ liệu hệ thống">
      {artifacts.map((artifact, index) => {
        const key = `art-${artifact.tool || artifact.type}-${index}`;
        const type = artifact.type || '';
        const tool = artifact.tool || '';

        if (type === 'ADMIN_REVENUE_CHART_CARD' || tool === 'admin_revenue_timeseries') {
          return <RevenueChartCard key={key} artifact={artifact} />;
        }
        if (type === 'ADMIN_CRITICAL_VENDORS_TABLE' || tool === 'admin_vendor_diagnostics') {
          return <VendorDiagnosticsTableCard key={key} artifact={artifact} />;
        }
        if (type === 'ADMIN_CANCELLATION_BREAKDOWN_CARD' || tool === 'admin_booking_cancellation_breakdown') {
          return <CancellationBreakdownCard key={key} artifact={artifact} />;
        }
        if (
          type === 'ADMIN_KPI_SUMMARY_CARD' ||
          tool === 'admin_platform_dashboard' ||
          tool === 'admin_risk_and_support_queue_overview'
        ) {
          return <PlatformMetricsCard key={key} artifact={artifact} />;
        }
        if (type === 'ADMIN_DISPUTE_BRIEF_CARD' || tool === 'admin_dispute_summarize') {
          return <DisputeBriefCard key={key} artifact={artifact} />;
        }

        return <SafeFallbackCard key={key} artifact={artifact} />;
      })}
    </div>
  );
};

/* =========================================================================
   1. REVENUE TIME-SERIES CHART CARD
   ========================================================================= */
const RevenueChartCard: React.FC<{ artifact: CopilotArtifact }> = ({ artifact }) => {
  const items = extractRevenueChartData(artifact.data);
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);

  if (!items || items.length === 0) {
    return (
      <div className="copilot-artifact-card">
        <div className="copilot-artifact-header">
          <span className="copilot-artifact-badge">
            <Database size={12} />
            Dữ liệu hệ thống
          </span>
          <span className="copilot-artifact-title">Doanh thu theo chu kỳ</span>
        </div>
        <div className="copilot-artifact-empty">Không có dữ liệu doanh thu trong khoảng thời gian này.</div>
      </div>
    );
  }

  // Exact aggregates from verified data points
  const totalGmv = items.reduce((sum, i) => sum + i.gmv, 0);
  const totalCommission = items.reduce((sum, i) => sum + i.commission, 0);
  const totalPayout = items.reduce((sum, i) => sum + i.netVendorPayout, 0);
  const totalRefunds = items.reduce((sum, i) => sum + i.refunds, 0);

  const firstPeriod = items[0]?.period || '';
  const lastPeriod = items[items.length - 1]?.period || '';

  // SVG Chart dimensions - Expanded 330px height, optimized resolution
  const maxVal = Math.max(...items.map((i) => Math.max(i.gmv, i.commission)), 10_000);
  const svgWidth = 840;
  const svgHeight = 330;
  const padLeft = 65;
  const padRight = 20;
  const padTop = 20;
  const padBottom = 16;
  const plotWidth = svgWidth - padLeft - padRight;
  const plotHeight = svgHeight - padTop - padBottom;
  const step = plotWidth / items.length;

  const yTicks = [0, maxVal * 0.25, maxVal * 0.5, maxVal * 0.75, maxVal];

  return (
    <div className="copilot-artifact-card">
      <div className="copilot-artifact-header">
        <div className="copilot-artifact-header-left">
          <span className="copilot-artifact-badge">
            <BarChart3 size={12} />
            Dữ liệu hệ thống
          </span>
          <span className="copilot-artifact-title">Doanh thu & Hoa hồng theo chu kỳ</span>
        </div>
        <div className="copilot-artifact-period">
          Kỳ thực tế: <strong>{firstPeriod}</strong> → <strong>{lastPeriod}</strong> ({items.length} kỳ)
        </div>
      </div>

      {/* Aggregate KPI Grid */}
      <div className="copilot-artifact-stat-grid">
        <div className="copilot-artifact-stat-item">
          <span className="copilot-artifact-stat-label">Tổng GMV</span>
          <span className="copilot-artifact-stat-value copilot-artifact-stat-value--primary">
            {formatVND(totalGmv)}
          </span>
        </div>
        <div className="copilot-artifact-stat-item">
          <span className="copilot-artifact-stat-label">Hoa hồng sàn</span>
          <span className="copilot-artifact-stat-value copilot-artifact-stat-value--sky">
            {formatVND(totalCommission)}
          </span>
        </div>
        <div className="copilot-artifact-stat-item">
          <span className="copilot-artifact-stat-label">Quyết toán đối tác</span>
          <span className="copilot-artifact-stat-value">{formatVND(totalPayout)}</span>
        </div>
        <div className="copilot-artifact-stat-item">
          <span className="copilot-artifact-stat-label">Hoàn tiền</span>
          <span className="copilot-artifact-stat-value copilot-artifact-stat-value--danger">
            {formatVND(totalRefunds)}
          </span>
        </div>
      </div>

      {/* SVG Bar Chart */}
      <div className="copilot-chart-wrapper">
        <svg
          viewBox={`0 0 ${svgWidth} ${svgHeight}`}
          className="copilot-chart-svg"
          preserveAspectRatio="none"
          onMouseLeave={() => setHoveredIdx(null)}
        >
          {/* Grid lines and Y labels */}
          {yTicks.map((val, idx) => {
            const y = padTop + plotHeight - (val / maxVal) * plotHeight;
            return (
              <g key={idx}>
                <line
                  x1={padLeft}
                  y1={y}
                  x2={svgWidth - padRight}
                  y2={y}
                  stroke="#e2e8f0"
                  strokeDasharray="2,2"
                  strokeWidth="1"
                />
                <text
                  x={padLeft - 8}
                  y={y + 3.5}
                  textAnchor="end"
                  fontSize="11"
                  fill="#94a3b8"
                  fontFamily="sans-serif"
                >
                  {formatCompactVND(val)}
                </text>
              </g>
            );
          })}

          {/* Background column highlight on hover */}
          {items.map((_, idx) => {
            const isHovered = hoveredIdx === idx;
            if (!isHovered) return null;
            return (
              <rect
                key={`hl-${idx}`}
                x={padLeft + idx * step}
                y={padTop}
                width={step}
                height={plotHeight}
                fill="#e2e8f0"
                opacity="0.5"
                rx="2"
              />
            );
          })}

          {/* Columns & Hit Areas */}
          {items.map((item, idx) => {
            const barWidth = Math.min(Math.max(step * 0.38, 3), 16);
            const xGmv = padLeft + idx * step + step * 0.5 - barWidth - 1;
            const xCom = padLeft + idx * step + step * 0.5 + 1;

            const hGmv = Math.max((item.gmv / maxVal) * plotHeight, item.gmv > 0 ? 3 : 0);
            const yGmv = padTop + plotHeight - hGmv;

            const hCom = Math.max((item.commission / maxVal) * plotHeight, item.commission > 0 ? 3 : 0);
            const yCom = padTop + plotHeight - hCom;

            const isHovered = hoveredIdx === idx;

            return (
              <g key={idx}>
                {/* GMV Bar */}
                {hGmv > 0 && (
                  <rect
                    x={xGmv}
                    y={yGmv}
                    width={barWidth}
                    height={hGmv}
                    rx="2"
                    fill={isHovered ? '#084d5c' : '#0B5D6F'}
                  />
                )}

                {/* Commission Bar */}
                {hCom > 0 && (
                  <rect
                    x={xCom}
                    y={yCom}
                    width={barWidth}
                    height={hCom}
                    rx="2"
                    fill={isHovered ? '#0369a1' : '#0284c7'}
                  />
                )}

                {/* Transparent full-slice hit area for effortless hover */}
                <rect
                  x={padLeft + idx * step}
                  y={padTop}
                  width={step}
                  height={plotHeight}
                  fill="transparent"
                  style={{ cursor: 'pointer' }}
                  onMouseEnter={() => setHoveredIdx(idx)}
                />
              </g>
            );
          })}
        </svg>

        {/* Hover inspection tooltip */}
        {hoveredIdx !== null && items[hoveredIdx] && (
          <div
            className={`copilot-chart-tooltip ${
              hoveredIdx > items.length * 0.65
                ? 'copilot-chart-tooltip--left'
                : 'copilot-chart-tooltip--right'
            }`}
          >
            <div className="copilot-chart-tooltip-period">
              Kỳ: <strong>{items[hoveredIdx].period}</strong>
            </div>
            <div className="copilot-chart-tooltip-row">
              <span className="copilot-chart-legend-dot copilot-chart-legend-dot--gmv" />
              <span>GMV: <strong>{formatVND(items[hoveredIdx].gmv)}</strong></span>
            </div>
            <div className="copilot-chart-tooltip-row">
              <span className="copilot-chart-legend-dot copilot-chart-legend-dot--com" />
              <span>Hoa hồng: <strong>{formatVND(items[hoveredIdx].commission)}</strong></span>
            </div>
            <div className="copilot-chart-tooltip-row">
              <span className="copilot-chart-legend-dot copilot-chart-legend-dot--payout" />
              <span>Payout: <strong>{formatVND(items[hoveredIdx].netVendorPayout)}</strong></span>
            </div>
            {items[hoveredIdx].refunds > 0 && (
              <div className="copilot-chart-tooltip-row" style={{ color: '#dc2626' }}>
                <span>Hoàn tiền: <strong>{formatVND(items[hoveredIdx].refunds)}</strong></span>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Legend */}
      <div className="copilot-chart-legend">
        <div className="copilot-chart-legend-item">
          <span className="copilot-chart-legend-dot copilot-chart-legend-dot--gmv" />
          <span>GMV ({formatVND(totalGmv)})</span>
        </div>
        <div className="copilot-chart-legend-item">
          <span className="copilot-chart-legend-dot copilot-chart-legend-dot--com" />
          <span>Hoa hồng sàn ({formatVND(totalCommission)})</span>
        </div>
      </div>
    </div>
  );
};

/* =========================================================================
   2. VENDOR DIAGNOSTICS & CRITICAL TABLE
   ========================================================================= */
const VendorDiagnosticsTableCard: React.FC<{ artifact: CopilotArtifact }> = ({ artifact }) => {
  const vendors = extractVendorDiagnosticsData(artifact.data);

  if (!vendors || vendors.length === 0) {
    return (
      <div className="copilot-artifact-card">
        <div className="copilot-artifact-header">
          <span className="copilot-artifact-badge">
            <Building2 size={12} />
            Dữ liệu hệ thống
          </span>
          <span className="copilot-artifact-title">Chẩn đoán hoạt động đối tác</span>
        </div>
        <div className="copilot-artifact-empty">Không có dữ liệu đối tác trong kỳ tra cứu.</div>
      </div>
    );
  }

  return (
    <div className="copilot-artifact-card">
      <div className="copilot-artifact-header">
        <div className="copilot-artifact-header-left">
          <span className="copilot-artifact-badge">
            <Building2 size={12} />
            Dữ liệu hệ thống
          </span>
          <span className="copilot-artifact-title">Chẩn đoán sức khỏe & Xếp hạng đối tác</span>
        </div>
        <div className="copilot-artifact-period">{vendors.length} đối tác ghi nhận</div>
      </div>

      <div className="copilot-table-container">
        <table className="copilot-data-table">
          <thead>
            <tr>
              <th>Đối tác</th>
              <th style={{ textAlign: 'right' }}>GMV</th>
              <th style={{ textAlign: 'center' }}>Số đơn</th>
              <th style={{ textAlign: 'center' }}>Tỷ lệ hủy</th>
              <th style={{ textAlign: 'center' }}>Lấp đầy slot</th>
              <th style={{ textAlign: 'center' }}>Đánh giá</th>
              <th style={{ textAlign: 'center' }}>Trạng thái</th>
              <th>Vấn đề ghi nhận</th>
            </tr>
          </thead>
          <tbody>
            {vendors.map((v, idx) => {
              const isCritical = v.severity === 'CRITICAL';
              const isWarning = v.severity === 'WARNING';

              return (
                <tr key={v.vendorId || idx} className={isCritical ? 'copilot-table-row--critical' : ''}>
                  <td>
                    <div className="copilot-table-vendor-name">{v.businessName}</div>
                    {v.vendorId && (
                      <div className="copilot-table-vendor-id" title={v.vendorId}>
                        ID: #{v.vendorId.slice(0, 8)}
                      </div>
                    )}
                  </td>
                  <td style={{ textAlign: 'right', fontWeight: 600 }}>{formatVND(v.gmv)}</td>
                  <td style={{ textAlign: 'center' }}>{formatNumber(v.totalBookings)}</td>
                  <td
                    style={{
                      textAlign: 'center',
                      fontWeight: 600,
                      color: isCritical || v.cancellationRate > 0.15 ? '#dc2626' : 'inherit',
                    }}
                  >
                    {formatPercent(v.cancellationRate)}
                  </td>
                  <td style={{ textAlign: 'center' }}>{formatPercent(v.slotOccupancyRate)}</td>
                  <td style={{ textAlign: 'center' }}>
                    {v.ratingAvg > 0 ? (
                      <span>
                        ★ {v.ratingAvg.toFixed(1)} <small>({v.ratingCount})</small>
                      </span>
                    ) : (
                      <span style={{ color: '#94a3b8' }}>Chưa có</span>
                    )}
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    {isCritical ? (
                      <span className="copilot-badge-severity copilot-badge-severity--critical">
                        Báo động đỏ
                      </span>
                    ) : isWarning ? (
                      <span className="copilot-badge-severity copilot-badge-severity--warning">
                        Cần lưu ý
                      </span>
                    ) : (
                      <span className="copilot-badge-severity copilot-badge-severity--healthy">
                        Ổn định
                      </span>
                    )}
                  </td>
                  <td>
                    {v.issueTypes && v.issueTypes.length > 0 ? (
                      <div className="copilot-issue-tag-group">
                        {v.issueTypes.map((issue, iIdx) => (
                          <span key={iIdx} className="copilot-issue-tag">
                            {issue}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <span style={{ color: '#94a3b8', fontSize: '11px' }}>Không có vi phạm</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

/* =========================================================================
   3. CANCELLATION BREAKDOWN CARD
   ========================================================================= */
const CancellationBreakdownCard: React.FC<{ artifact: CopilotArtifact }> = ({ artifact }) => {
  const data = extractCancellationBreakdownData(artifact.data);

  if (!data) {
    return (
      <div className="copilot-artifact-card">
        <div className="copilot-artifact-header">
          <span className="copilot-artifact-badge">
            <AlertTriangle size={12} />
            Dữ liệu hệ thống
          </span>
          <span className="copilot-artifact-title">Phân tích cấu trúc hủy đơn</span>
        </div>
        <div className="copilot-artifact-empty">Không có dữ liệu hủy đơn.</div>
      </div>
    );
  }

  const total = data.totalBookings;
  const cancelled = data.totalCancelled;
  const cancelRate = total > 0 ? ((cancelled / total) * 100).toFixed(1) : '0';

  const breakdownRows = [
    {
      label: 'Thời tiết biển nguy hiểm',
      count: data.weatherCancellations,
      icon: CloudRain,
      color: '#0284c7',
    },
    {
      label: 'Lỗi do phía đối tác',
      count: data.vendorFaultCancellations,
      icon: UserX,
      color: '#dc2626',
    },
    {
      label: 'Khách hàng tự yêu cầu hủy',
      count: data.customerCancellations,
      icon: Info,
      color: '#d97706',
    },
    {
      label: 'Tranh chấp khiếu nại bồi hoàn',
      count: data.disputeCancellations,
      icon: ShieldAlert,
      color: '#9333ea',
    },
    {
      label: 'Can thiệp từ Quản trị viên sàn',
      count: data.adminOverrideCancellations,
      icon: Scale,
      color: '#475569',
    },
  ];

  return (
    <div className="copilot-artifact-card">
      <div className="copilot-artifact-header">
        <div className="copilot-artifact-header-left">
          <span className="copilot-artifact-badge">
            <AlertTriangle size={12} />
            Dữ liệu hệ thống
          </span>
          <span className="copilot-artifact-title">Phân tích cấu trúc lý do hủy đơn</span>
        </div>
        <div className="copilot-artifact-period">
          Tỷ lệ hủy sàn: <strong>{cancelRate}%</strong>
        </div>
      </div>

      <div className="copilot-artifact-stat-grid">
        <div className="copilot-artifact-stat-item">
          <span className="copilot-artifact-stat-label">Tổng lượt đặt</span>
          <span className="copilot-artifact-stat-value">{formatNumber(data.totalBookings)}</span>
        </div>
        <div className="copilot-artifact-stat-item">
          <span className="copilot-artifact-stat-label">Hoàn thành tốt</span>
          <span className="copilot-artifact-stat-value copilot-artifact-stat-value--primary">
            {formatNumber(data.completedBookings)}
          </span>
        </div>
        <div className="copilot-artifact-stat-item">
          <span className="copilot-artifact-stat-label">Tổng đơn đã hủy</span>
          <span className="copilot-artifact-stat-value copilot-artifact-stat-value--danger">
            {formatNumber(data.totalCancelled)}
          </span>
        </div>
      </div>

      {/* Breakdown Rows */}
      <div className="copilot-breakdown-list">
        {breakdownRows.map((row, idx) => {
          const pct = cancelled > 0 ? ((row.count / cancelled) * 100).toFixed(1) : '0';
          const Icon = row.icon;

          return (
            <div key={idx} className="copilot-breakdown-row">
              <div className="copilot-breakdown-row-label">
                <Icon size={13} style={{ color: row.color, flexShrink: 0 }} />
                <span>{row.label}</span>
              </div>

              <div className="copilot-breakdown-row-bar-box">
                <div
                  className="copilot-breakdown-row-bar"
                  style={{
                    width: `${Math.min(Number(pct), 100)}%`,
                    backgroundColor: row.color,
                  }}
                />
              </div>

              <div className="copilot-breakdown-row-count">
                <strong>{formatNumber(row.count)}</strong> đơn{' '}
                <span className="copilot-breakdown-row-pct">({pct}%)</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

/* =========================================================================
   4. PLATFORM METRICS OR QUEUE OVERVIEW CARD
   ========================================================================= */
const PlatformMetricsCard: React.FC<{ artifact: CopilotArtifact }> = ({ artifact }) => {
  const metrics = extractPlatformMetricsData(artifact.data);
  const queue = extractQueueOverviewData(artifact.data);

  if (metrics) {
    return (
      <div className="copilot-artifact-card">
        <div className="copilot-artifact-header">
          <span className="copilot-artifact-badge">
            <Database size={12} />
            Dữ liệu hệ thống
          </span>
          <span className="copilot-artifact-title">Tổng quan vận hành sàn</span>
        </div>

        <div className="copilot-artifact-stat-grid">
          <div className="copilot-artifact-stat-item">
            <span className="copilot-artifact-stat-label">Tổng GMV sàn</span>
            <span className="copilot-artifact-stat-value copilot-artifact-stat-value--primary">
              {formatVND(metrics.totalGmv)}
            </span>
          </div>
          <div className="copilot-artifact-stat-item">
            <span className="copilot-artifact-stat-label">Hoa hồng sàn</span>
            <span className="copilot-artifact-stat-value copilot-artifact-stat-value--sky">
              {formatVND(metrics.platformCommission)}
            </span>
          </div>
          <div className="copilot-artifact-stat-item">
            <span className="copilot-artifact-stat-label">Net Payout đối tác</span>
            <span className="copilot-artifact-stat-value">{formatVND(metrics.netPayout)}</span>
          </div>
          <div className="copilot-artifact-stat-item">
            <span className="copilot-artifact-stat-label">Tổng lượt đặt</span>
            <span className="copilot-artifact-stat-value">{formatNumber(metrics.totalBookings)}</span>
          </div>
          <div className="copilot-artifact-stat-item">
            <span className="copilot-artifact-stat-label">Đối tác hoạt động</span>
            <span className="copilot-artifact-stat-value">{formatNumber(metrics.activeVendors)}</span>
          </div>
          <div className="copilot-artifact-stat-item">
            <span className="copilot-artifact-stat-label">Dịch vụ mở bán</span>
            <span className="copilot-artifact-stat-value">{formatNumber(metrics.activeServices)}</span>
          </div>
          <div className="copilot-artifact-stat-item">
            <span className="copilot-artifact-stat-label">Đối tác báo động</span>
            <span
              className={`copilot-artifact-stat-value ${
                metrics.criticalVendorsCount > 0 ? 'copilot-artifact-stat-value--danger' : ''
              }`}
            >
              {formatNumber(metrics.criticalVendorsCount)}
            </span>
          </div>
        </div>

        {metrics.operationalAlerts && metrics.operationalAlerts.length > 0 && (
          <div className="copilot-artifact-alerts">
            <div className="copilot-artifact-alerts-title">
              <AlertTriangle size={13} /> Cảnh báo vận hành cần xử lý:
            </div>
            <ul className="copilot-artifact-alerts-list">
              {metrics.operationalAlerts.map((alert, idx) => (
                <li key={idx}>{alert}</li>
              ))}
            </ul>
          </div>
        )}
      </div>
    );
  }

  if (queue) {
    return (
      <div className="copilot-artifact-card">
        <div className="copilot-artifact-header">
          <span className="copilot-artifact-badge">
            <Database size={12} />
            Dữ liệu hệ thống
          </span>
          <span className="copilot-artifact-title">Hàng đợi kiểm duyệt & Hỗ trợ</span>
        </div>

        <div className="copilot-artifact-stat-grid">
          <div className="copilot-artifact-stat-item">
            <span className="copilot-artifact-stat-label">Ca rủi ro cần xem xét</span>
            <span className="copilot-artifact-stat-value">{formatNumber(queue.pendingRiskCasesCount)}</span>
          </div>
          <div className="copilot-artifact-stat-item">
            <span className="copilot-artifact-stat-label">Yêu cầu hỗ trợ chờ duyệt</span>
            <span className="copilot-artifact-stat-value">{formatNumber(queue.pendingSupportRequestsCount)}</span>
          </div>
          <div className="copilot-artifact-stat-item">
            <span className="copilot-artifact-stat-label">Khiếu nại đang mở</span>
            <span className="copilot-artifact-stat-value copilot-artifact-stat-value--danger">
              {formatNumber(queue.openDisputesCount)}
            </span>
          </div>
        </div>
      </div>
    );
  }

  return <SafeFallbackCard artifact={artifact} />;
};

/* =========================================================================
   5. DISPUTE BRIEF CARD
   ========================================================================= */
const DisputeBriefCard: React.FC<{ artifact: CopilotArtifact }> = ({ artifact }) => {
  const dispute = extractDisputeBriefData(artifact.data);

  if (!dispute) {
    return <SafeFallbackCard artifact={artifact} />;
  }

  return (
    <div className="copilot-artifact-card">
      <div className="copilot-artifact-header">
        <div className="copilot-artifact-header-left">
          <span className="copilot-artifact-badge">
            <Scale size={12} />
            Dữ liệu hệ thống
          </span>
          <span className="copilot-artifact-title">Tóm tắt hồ sơ khiếu nại</span>
        </div>
        <div className="copilot-artifact-period">Mã: #{dispute.disputeId.slice(0, 8)}</div>
      </div>

      <div className="copilot-dispute-info-grid">
        <div>
          <span className="copilot-info-label">Khách hàng:</span>{' '}
          <strong>{dispute.customerName || '—'}</strong>
        </div>
        <div>
          <span className="copilot-info-label">Đối tác:</span>{' '}
          <strong>{dispute.vendorBusinessName || '—'}</strong>
        </div>
        <div>
          <span className="copilot-info-label">Dịch vụ:</span>{' '}
          <strong>{dispute.serviceTitle || '—'}</strong>
        </div>
        <div>
          <span className="copilot-info-label">Lý do tranh chấp:</span>{' '}
          <span>{dispute.disputeReason || '—'}</span>
        </div>
      </div>

      {dispute.objectiveSystemFacts && dispute.objectiveSystemFacts.length > 0 && (
        <div className="copilot-dispute-facts">
          <div className="copilot-dispute-facts-title">Bằng chứng hệ thống xác thực:</div>
          <ul className="copilot-dispute-facts-list">
            {dispute.objectiveSystemFacts.map((fact, idx) => (
              <li key={idx}>
                <CheckCircle2 size={13} className="copilot-icon-check" />
                <span>{fact}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {dispute.recommendedResolution && (
        <div className="copilot-dispute-recommendation">
          <div className="copilot-dispute-rec-header">
            <span>Khuyến nghị trọng tài:</span>
            <span className="copilot-badge-rec">
              {dispute.recommendedResolution === 'RESOLVED_REFUND'
                ? `Hoàn tiền ${dispute.recommendedRefundPercent ?? 100}%`
                : dispute.recommendedResolution === 'RESOLVED_REJECTED'
                ? 'Từ chối hoàn tiền'
                : dispute.recommendedResolution}
            </span>
          </div>
          {dispute.recommendationReason && (
            <p className="copilot-dispute-rec-reason">{dispute.recommendationReason}</p>
          )}
        </div>
      )}
    </div>
  );
};

/* =========================================================================
   6. SAFE FALLBACK CARD (Never breaks, no raw JSON dump by default)
   ========================================================================= */
const SafeFallbackCard: React.FC<{ artifact: CopilotArtifact }> = ({ artifact }) => {
  const [showDetails, setShowDetails] = useState(false);

  return (
    <div className="copilot-artifact-card copilot-artifact-card--fallback">
      <div className="copilot-artifact-header">
        <span className="copilot-artifact-badge">
          <Database size={12} />
          Dữ liệu hệ thống
        </span>
        <span className="copilot-artifact-title">
          Kết quả công cụ: <code>{artifact.tool || artifact.type || 'Chưa định danh'}</code>
        </span>
        <button
          type="button"
          onClick={() => setShowDetails((prev) => !prev)}
          className="copilot-artifact-toggle-btn"
        >
          {showDetails ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
          <span>{showDetails ? 'Thu gọn' : 'Xem thông số kỹ thuật'}</span>
        </button>
      </div>

      {showDetails && (
        <pre className="copilot-artifact-raw-box">
          {JSON.stringify(artifact.data, null, 2)}
        </pre>
      )}
    </div>
  );
};
