import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Building2, CircleHelp, ListFilter, RotateCcw, Scale, ShieldCheck, Wallet } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import { formatNumber, operationGroups, type OperationCounts } from './dashboardModel';

const icons = { disputes: Scale, refunds: RotateCcw, vendors: Building2, services: ShieldCheck, settlements: Wallet };
const actions = { disputes: 'Phân xử', refunds: 'Theo dõi đơn', vendors: 'Thẩm định hồ sơ', services: 'Kiểm duyệt', settlements: 'Đối soát' };
const tabs = [
  { key: 'all', label: 'Tất cả' }, { key: 'vendors', label: 'Đối tác' },
  { key: 'services', label: 'Dịch vụ' }, { key: 'refunds', label: 'Đơn hàng' }, { key: 'finance', label: 'Tài chính' },
];

export function ActionQueue({ counts, loading, onRetry, demo = false }: { counts?: OperationCounts; loading: boolean; onRetry: () => void; demo?: boolean }) {
  const href = (path: string) => `${path}${demo ? '?demo=1' : ''}`;
  const [filter, setFilter] = useState('all');
  const groups = operationGroups.filter(group => filter === 'all' || filter === group.key || (filter === 'finance' && ['settlements', 'disputes'].includes(group.key)));
  const visible = groups.filter(group => counts?.[group.key] === null || (counts?.[group.key] ?? 0) > 0);
  const knownTotal = counts ? Object.values(counts).reduce<number>((sum, count) => sum + (count ?? 0), 0) : 0;
  const unknown = counts && Object.values(counts).some(count => count === null);
  const allFailed = counts && Object.values(counts).every(count => count === null);
  const customerAttention = counts?.disputes != null && counts?.refunds != null ? counts.disputes + counts.refunds : null;

  return <section className="admin-card" aria-labelledby="admin-queue-heading">
    <div className="admin-panel-header"><div className="admin-panel-title">
      <h2 id="admin-queue-heading"><ListFilter size={17} />Hàng đợi ưu tiên <span className="admin-count">{loading || !counts || allFailed ? '—' : `${formatNumber(knownTotal)}${unknown ? '+' : ''}`}</span></h2>
    </div><p>Ưu tiên phản hồi khách hàng (P1), sau đó đến kiểm duyệt hồ sơ (P2) và đối soát (P3).</p></div>
    {!loading && customerAttention !== null && customerAttention > 0 && <div className="admin-queue-summary">
      <span className="admin-queue-summary-icon"><Scale size={20} strokeWidth={1.6} /></span>
      <div><span className="admin-eyebrow">ƯU TIÊN XỬ LÝ</span><strong>{formatNumber(customerAttention)} mục cần phản hồi khách hàng</strong><p>Khiếu nại đang mở và yêu cầu hoàn tiền đang chờ.</p></div>
      <Link className="admin-button admin-button--primary" to={href(counts?.disputes ? '/admin/disputes' : '/admin/orders')}>Xử lý ngay<ArrowRight size={14} /></Link>
    </div>}
    <div className="admin-queue-tabs" role="group" aria-label="Lọc hàng đợi công việc">
      {tabs.map(tab => <button key={tab.key} aria-pressed={filter === tab.key} onClick={() => setFilter(tab.key)}>{tab.label}</button>)}
    </div>
    {loading ? <AdminState kind="loading" title="Đang tải công việc…" />
      : allFailed || !counts ? <AdminState kind="error" title="Chưa tải được hàng đợi" description="Các nguồn dữ liệu vận hành chưa sẵn sàng." action={<button className="admin-button" onClick={onRetry}>Thử lại</button>} />
      : visible.length === 0 ? <AdminState kind="empty" title="Không có công việc đang chờ" description="Nhóm công việc này đã được xử lý hết." />
      : <div className="admin-queue-list">
          <AnimatePresence mode="popLayout" initial={false}>
            {visible.map((group, index) => {
              const count = counts[group.key]; const Icon = icons[group.key];
              return <motion.div
                layout
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                transition={{
                  duration: 0.35,
                  delay: index * 0.04,
                  ease: [0.16, 1, 0.3, 1],
                }}
                className="admin-queue-row"
                key={group.key}
                data-priority={group.priority}
              >
                <span className="admin-queue-icon"><Icon size={17} strokeWidth={1.7} /></span>
                <div className="admin-queue-copy">
                  <div className="admin-queue-item-heading">
                    <AdminBadge tone={count === null ? 'neutral' : group.priority === 1 ? 'rose' : group.priority === 2 ? 'amber' : 'teal'}>
                      {count === null ? 'Chưa tải được' : group.priority === 1 ? 'P1 · Cần chú ý' : group.priority === 2 ? 'P2 · Kiểm duyệt' : 'P3 · Đối soát'}
                    </AdminBadge>
                    <h3>{count === null ? group.label : `${formatNumber(count)} ${group.title}`}</h3>
                  </div>
                  <p>{group.description}</p>
                </div>
                <Link to={href(group.path)} className="admin-queue-action" aria-label={`Mở ${group.label.toLowerCase()}`}>
                  {actions[group.key]}
                  <ArrowRight size={14} className="admin-queue-action-arrow" />
                </Link>
              </motion.div>;
            })}
          </AnimatePresence>
        </div>}
    <div className="admin-panel-footer"><CircleHelp size={12} />{unknown ? 'Một số nguồn chưa tải được. Số lượng hiển thị là phần đã xác nhận.' : 'Hàng đợi theo trạng thái hiện tại, độc lập với bộ lọc 7/30 ngày.'}</div>
  </section>;
}
