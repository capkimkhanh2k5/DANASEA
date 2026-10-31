import { CheckCircle2, CreditCard, RotateCcw, Wallet } from 'lucide-react';
import { AnimatedKpiCounter } from './AnimatedKpiCounter';
import { formatNumber, formatVND } from './dashboardModel';

interface KpiOverviewProps {
  metrics: {
    bookings: number;
    completed: number;
    cancelled: number;
    revenue: number;
    commission: number;
    refunds: number;
  };
  loading: boolean;
  bookingUnavailable: boolean;
  revenueUnavailable: boolean;
}

export function KpiOverview({
  metrics,
  loading,
  bookingUnavailable,
  revenueUnavailable,
}: KpiOverviewProps) {
  const completionRate = metrics.bookings > 0 ? ((metrics.completed / metrics.bookings) * 100).toFixed(1) : '0';
  const cancellationRate = metrics.bookings > 0 ? ((metrics.cancelled / metrics.bookings) * 100).toFixed(1) : '0';

  const cards = [
    {
      id: 'kpi-completed',
      label: 'Đơn hoàn thành',
      icon: CheckCircle2,
      iconColor: '#0B5D6F',
      value: metrics.completed,
      formatter: formatNumber,
      unavailable: bookingUnavailable,
      foot: bookingUnavailable ? 'Chờ dữ liệu báo cáo' : `${completionRate}% trên tổng ${formatNumber(metrics.bookings)} đơn`,
    },
    {
      id: 'kpi-cancelled',
      label: 'Đơn hủy',
      icon: RotateCcw,
      iconColor: '#D9534F',
      value: metrics.cancelled,
      formatter: formatNumber,
      unavailable: bookingUnavailable,
      foot: bookingUnavailable ? 'Chờ dữ liệu báo cáo' : `${cancellationRate}% tỷ lệ hủy trong kỳ`,
    },
    {
      id: 'kpi-commission',
      label: 'Hoa hồng sàn',
      icon: Wallet,
      iconColor: '#0B5D6F',
      value: metrics.commission,
      formatter: formatVND,
      unavailable: revenueUnavailable,
      foot: revenueUnavailable ? 'Chờ dữ liệu báo cáo' : `10% doanh thu (${formatVND(metrics.revenue)})`,
    },
    {
      id: 'kpi-refunds',
      label: 'Tiền hoàn',
      icon: CreditCard,
      iconColor: '#64748B',
      value: metrics.refunds,
      formatter: formatVND,
      unavailable: revenueUnavailable,
      foot: revenueUnavailable
        ? 'Chờ dữ liệu báo cáo'
        : metrics.refunds > 0
          ? 'Đã đối soát hoàn trả khách'
          : 'Không phát sinh hoàn tiền',
    },
  ];

  return (
    <section className="admin-card admin-dashboard-kpi-block" aria-label="Chỉ số hiệu quả vận hành">
      <div className="admin-dashboard-block-header">
        <h2 className="admin-dashboard-block-title">Hiệu quả vận hành</h2>
      </div>
      <div className="admin-kpi-grid-2x2" aria-busy={loading}>
        {cards.map(card => {
          const Icon = card.icon;
          return (
            <article className="admin-kpi-compact" key={card.id}>
              <div className="admin-kpi-compact-header">
                <span className="admin-kpi-compact-label">{card.label}</span>
                <Icon size={16} strokeWidth={2} style={{ color: card.iconColor }} aria-hidden="true" />
              </div>
              <strong className="admin-kpi-compact-value">
                <AnimatedKpiCounter
                  value={card.value}
                  formatter={card.formatter}
                  unavailable={card.unavailable}
                />
              </strong>
              <p className="admin-kpi-compact-foot" title={card.foot}>
                <span className="admin-kpi-compact-dot" style={{ backgroundColor: card.iconColor }} aria-hidden="true" />
                {card.foot}
              </p>
            </article>
          );
        })}
      </div>
    </section>
  );
}
