import React, { useState, useEffect } from 'react';
import {
  CircleDollarSign,
  ArrowUpRight,
  BadgePercent,
  Store,
} from 'lucide-react';
import { AnimatedKpiCounter } from './AnimatedKpiCounter';
import { adminAiService } from '../../../services/adminAiService';
import type { PlatformDashboardMetrics } from '../../../types/adminAi';
import './Dashboard.css';

function formatVnd(amount: number | null | undefined): string {
  if (amount === null || amount === undefined || isNaN(amount)) return '0 đ';
  return `${amount.toLocaleString('vi-VN')} đ`;
}

interface ExecutiveDigestBannerProps {
  days: 7 | 30;
  metrics: {
    gmv: number;
    revenue: number;
    commission: number;
    refunds?: number;
  };
  loading?: boolean;
  unavailable?: boolean;
}

export const ExecutiveDigestBanner: React.FC<ExecutiveDigestBannerProps> = ({
  days,
  metrics,
  loading = false,
  unavailable = false,
}) => {
  const [digestData, setDigestData] = useState<PlatformDashboardMetrics | null>(null);
  const [digestLoading, setDigestLoading] = useState<boolean>(true);

  useEffect(() => {
    let active = true;
    setDigestLoading(true);
    adminAiService
      .getExecutiveDigest(days === 7 ? 'LAST_7_DAYS' : 'LAST_30_DAYS')
      .then(data => {
        if (active) {
          setDigestData(data);
          setDigestLoading(false);
        }
      })
      .catch(() => {
        if (active) {
          setDigestData(null);
          setDigestLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [days]);

  // Financial numbers: SSOT from metrics (API reports filtered strictly by selected time range)
  const gmv = metrics.gmv;
  const commission = metrics.commission;
  const payout = metrics.revenue - metrics.commission - (metrics.refunds || 0);

  const isFinancialUnavailable = loading || unavailable;

  const activeVendors = digestData?.activeVendors;
  const activeServices = digestData?.activeServices;
  const isEcosystemUnavailable = digestLoading || activeVendors == null || activeServices == null;

  return (
    <section aria-label="Tóm lược kinh doanh nền tảng" className="admin-digest-cards-grid">
      {/* Box 1: GMV */}
      <article className="admin-digest-card">
        <div className="admin-digest-card-head">
          <div className="admin-digest-card-title">
            <CircleDollarSign size={16} style={{ color: '#0284c7' }} />
            <span>Tổng giá trị giao dịch</span>
          </div>
        </div>
        <div className="admin-digest-card-value">
          <AnimatedKpiCounter
            value={gmv}
            formatter={formatVnd}
            unavailable={isFinancialUnavailable}
          />
        </div>
        <div className="admin-digest-card-sub">
          {isFinancialUnavailable ? 'Chờ dữ liệu báo cáo' : (gmv > 0 ? 'Doanh số toàn sàn trong kỳ' : 'Không có giao dịch trong kỳ')}
        </div>
      </article>

      {/* Box 2: Payout */}
      <article className="admin-digest-card">
        <div className="admin-digest-card-head">
          <div className="admin-digest-card-title">
            <ArrowUpRight size={16} style={{ color: '#059669' }} />
            <span>Tiền chi trả đối tác</span>
          </div>
        </div>
        <div className="admin-digest-card-value">
          <AnimatedKpiCounter
            value={payout}
            formatter={formatVnd}
            unavailable={isFinancialUnavailable}
          />
        </div>
        <div className="admin-digest-card-sub">
          {isFinancialUnavailable ? 'Chờ dữ liệu báo cáo' : 'Doanh thu thực nhận của đối tác'}
        </div>
      </article>

      {/* Box 3: Commission */}
      <article className="admin-digest-card">
        <div className="admin-digest-card-head">
          <div className="admin-digest-card-title">
            <BadgePercent size={16} style={{ color: '#0284c7' }} />
            <span>Hoa hồng thực thu</span>
          </div>
        </div>
        <div className="admin-digest-card-value" style={{ color: '#0284c7' }}>
          <AnimatedKpiCounter
            value={commission}
            formatter={formatVnd}
            unavailable={isFinancialUnavailable}
          />
        </div>
        <div className="admin-digest-card-sub">
          {isFinancialUnavailable ? 'Chờ dữ liệu báo cáo' : 'Thu nhập nền tảng trong kỳ'}
        </div>
      </article>

      {/* Box 4: Ecosystem */}
      <article className="admin-digest-card">
        <div className="admin-digest-card-head">
          <div className="admin-digest-card-title">
            <Store size={16} style={{ color: '#475569' }} />
            <span>Hệ sinh thái đối tác</span>
          </div>
        </div>
        <div className="admin-digest-card-value">
          <AnimatedKpiCounter
            value={activeVendors ?? 0}
            formatter={(n) => `${n}`}
            unavailable={isEcosystemUnavailable}
          /> <span className="admin-digest-unit">đối tác</span>
          <span className="admin-digest-separator">/</span>
          <AnimatedKpiCounter
            value={activeServices ?? 0}
            formatter={(n) => `${n}`}
            unavailable={isEcosystemUnavailable}
          /> <span className="admin-digest-unit">dịch vụ</span>
        </div>
        <div className="admin-digest-card-sub">
          {isEcosystemUnavailable ? 'Đang cập nhật danh mục' : 'Đang hoạt động trên hệ thống'}
        </div>
      </article>
    </section>
  );
};
