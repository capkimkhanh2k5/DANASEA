import { useEffect, useMemo, useState } from 'react';
import { Download, Search } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { MOCK_MASTER_ORDERS, MOCK_SUB_ORDERS } from '../../../mockData';
import { adminOrderService, getAdminOrderError } from '../../../services/adminOrderService';
import type { MasterOrder, SubOrder } from '../../../types';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import { AdminButton, AdminDisclosure, AdminFilterTabs, AdminNotice, AdminPreviewBanner } from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import { filterMasterOrders, ordersToCsv, relatedSubOrders, simulateRefund } from './ordersModel';
import './AdminOrders.css';

const statuses = [
  { value: 'all', label: 'Tất cả đơn' },
  { value: 'PENDING', label: 'Chờ xác nhận' },
  { value: 'CONFIRMED', label: 'Đã xác nhận' },
  { value: 'COMPLETED', label: 'Hoàn thành' },
  { value: 'CANCELLED', label: 'Đã hủy' },
  { value: 'REFUNDED', label: 'Đã hoàn' },
];
const money = (amount: number) => `${amount.toLocaleString('vi-VN')} đ`;
const dateTime = (value?: string) => {
  if (!value) return 'Chưa được cung cấp';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short' });
};

function OrderStatus({ status }: { status: MasterOrder['status'] }) {
  const tone = status === 'CANCELLED' || status === 'REFUNDED' ? 'rose' : status === 'PENDING' ? 'amber' : 'teal';
  return <AdminBadge tone={tone}>{status}</AdminBadge>;
}

export function OrderDetailContent({ master, subs, demo, simulatedReasons, onRefund }: {
  master: MasterOrder; subs: SubOrder[]; demo: boolean; simulatedReasons: Record<string, string>; onRefund: (sub: SubOrder) => void;
}) {
  return <div className="admin-orders admin-orders-detail">
    <section className="admin-detail-section">
      <div className="admin-orders-detail-heading"><h3>Thông tin đơn</h3><OrderStatus status={master.status} /></div>
      <dl className="admin-detail-grid">
        <div><dt>Master Order</dt><dd>#{master.id}</dd></div>
        <div><dt>Mã khách hàng</dt><dd>{master.customerId}</dd></div>
        <div><dt>Ngày tạo · giờ Việt Nam</dt><dd>{dateTime(master.createdAt)}</dd></div>
        <div><dt>Cập nhật gần nhất</dt><dd>{dateTime(master.updatedAt)}</dd></div>
      </dl>
      <p className="admin-detail-note">Thông tin liên hệ và hạng hội viên chưa được cung cấp trong dữ liệu đơn.</p>
    </section>
    <section className="admin-detail-section">
      <h3>Giá trị đơn</h3>
      <dl className="admin-orders-price">
        <div><dt>Tổng giá trị lưu trong đơn</dt><dd>{money(master.totalAmount)}</dd></div>
        <div><dt>Giảm giá được ghi nhận</dt><dd>{money(master.discountAmount)}</dd></div>
        <div><dt>Mã khuyến mãi</dt><dd>{master.discountCodeId || 'Không có mã'}</dd></div>
      </dl>
      <p className="admin-detail-note">Tổng giá trị đã lưu được giữ nguyên. Không trừ giảm giá lần nữa; đây không phải số tiền đã thu hay số tiền sẽ hoàn.</p>
    </section>
    <section className="admin-detail-section" aria-labelledby="order-subs-heading">
      <div className="admin-orders-detail-heading"><h3 id="order-subs-heading">Dịch vụ con</h3><span>{subs.length} SubOrders</span></div>
      {subs.length === 0 ? <AdminState compact kind="empty" title="Đơn chưa có dịch vụ con" description="Không có SubOrder liên kết với Master Order này trong dữ liệu mẫu." /> :
        <div className="admin-orders-sub-list">{subs.map(sub => <article key={sub.id} className="admin-orders-sub">
          <div className="admin-orders-sub-heading"><div><h4>#{sub.id}</h4><p>Dịch vụ {sub.serviceId} · Số lượng {sub.quantity}</p></div><OrderStatus status={sub.status} /></div>
          <div className="admin-orders-sub-value"><span>Thành tiền lưu trong đơn con</span><strong>{money(sub.subtotalAmount)}</strong></div>
          {demo && simulatedReasons[sub.id] && <p className="admin-orders-simulation"><strong>Mô phỏng cục bộ:</strong> {simulatedReasons[sub.id]}. Không phải bằng chứng hoàn tiền.</p>}
          <AdminDisclosure title={`Thông tin vận hành · ${sub.id}`} description="Đối tác, suất dịch vụ, phí và check-in">
            <dl className="admin-detail-grid">
              <div><dt>Master Order liên kết</dt><dd>#{sub.masterOrderId}</dd></div>
              <div><dt>Đối tác</dt><dd>{sub.vendorId}</dd></div>
              <div><dt>Mã dịch vụ</dt><dd>{sub.serviceId}</dd></div>
              <div><dt>Mã suất dịch vụ</dt><dd>{sub.slotId}</dd></div>
              <div><dt>Số lượng</dt><dd>{sub.quantity}</dd></div>
              <div><dt>Đơn giá</dt><dd>{money(sub.unitPrice)}</dd></div>
              <div><dt>Tỷ lệ hoa hồng</dt><dd>{sub.commissionRate}%</dd></div>
              <div><dt>Hoa hồng lưu trong đơn</dt><dd>{money(sub.commissionAmount)}</dd></div>
              <div><dt>Giá trị đối tác lưu trong đơn</dt><dd>{money(sub.vendorPayoutAmount)}</dd></div>
              <div><dt>Chấp nhận miễn trừ</dt><dd>{sub.waiverAccepted ? 'Đã chấp nhận' : 'Chưa chấp nhận'}</dd></div>
              <div><dt>Thời điểm chấp nhận</dt><dd>{dateTime(sub.waiverAcceptedAt)}</dd></div>
              <div><dt>Check-in</dt><dd>{sub.checkedInAt ? dateTime(sub.checkedInAt) : 'Chưa ghi nhận'}</dd></div>
              <div><dt>Khóa QR</dt><dd>{sub.qrSecret ? 'Đã có · nội dung được ẩn' : 'Chưa được cung cấp'}</dd></div>
              <div><dt>Ngày tạo</dt><dd>{dateTime(sub.createdAt)}</dd></div>
              <div><dt>Cập nhật gần nhất</dt><dd>{dateTime(sub.updatedAt)}</dd></div>
            </dl>
            <p className="admin-detail-note">Các khoản phí là dữ liệu của đơn con, chưa xác nhận đã quyết toán hay chi trả.</p>
          </AdminDisclosure>
          {sub.status !== 'REFUNDED' && <div className="admin-orders-sub-actions"><AdminButton variant="danger" disabled={!demo} onClick={() => onRefund(sub)} aria-describedby="orders-demo-explanation">Mô phỏng hoàn tiền</AdminButton></div>}
        </article>)}</div>}
      <p className="admin-detail-note" id="orders-demo-explanation">{demo ? 'Thao tác demo cần lý do và xác nhận, chỉ đổi trạng thái SubOrder trong phiên xem thử.' : 'Chỉ bật mô phỏng trong DEMO MODE ở môi trường phát triển.'} Không thực hiện chuyển tiền.</p>
    </section>
    <section className="admin-detail-section">
      <h3>Thanh toán &amp; hoàn tiền</h3>
      <AdminBadge tone="neutral">Chưa có dữ liệu thanh toán</AdminBadge>
      <p className="admin-detail-note">Trạng thái Master/Sub Order không xác nhận một giao dịch thanh toán hoặc hoàn tiền. Nguồn dữ liệu hiện tại chưa cung cấp bản ghi Payment hay Refund.</p>
      <AdminDisclosure title="Dữ liệu đối soát" description="Các thông tin còn thiếu để xác minh giao dịch">
        <dl className="admin-detail-grid">
          <div><dt>Mã Payment / giao dịch cổng</dt><dd>Chưa được cung cấp</dd></div>
          <div><dt>Nhà cung cấp thanh toán</dt><dd>Chưa được cung cấp</dd></div>
          <div><dt>Số tiền / trạng thái Payment</dt><dd>Chưa được cung cấp</dd></div>
          <div><dt>Ngày tạo / cập nhật Payment</dt><dd>Chưa được cung cấp</dd></div>
          <div><dt>Bằng chứng webhook</dt><dd>Chưa được cung cấp</dd></div>
          <div><dt>Mã Refund / đơn con liên kết</dt><dd>Chưa được cung cấp</dd></div>
          <div><dt>Số tiền / tỷ lệ hoàn</dt><dd>Chưa được cung cấp</dd></div>
          <div><dt>Lý do / trạng thái Refund</dt><dd>Chưa được cung cấp</dd></div>
          <div><dt>Ngày tạo / cập nhật Refund</dt><dd>Chưa được cung cấp</dd></div>
          <div><dt>Thời điểm xử lý hoàn tiền</dt><dd>Chưa được cung cấp</dd></div>
        </dl>
      </AdminDisclosure>
      <div className="admin-orders-invoice"><AdminButton disabled><Download size={16} aria-hidden="true" />Tải PDF hóa đơn · chưa khả dụng</AdminButton><p className="admin-detail-note">Chưa có tệp hóa đơn hoặc dữ liệu VAT để tải xuống.</p></div>
    </section>
  </div>;
}

export function AdminOrders() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const demo = import.meta.env.DEV && searchParams.get('demo') === '1';
  const [masterOrders, setMasterOrders] = useState<MasterOrder[]>(() => demo ? MOCK_MASTER_ORDERS : []);
  const [subOrders, setSubOrders] = useState<SubOrder[]>(() => demo ? MOCK_SUB_ORDERS : []);
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [simulatedReasons, setSimulatedReasons] = useState<Record<string, string>>({});
  const visibleSubs = subOrders;
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [refundTarget, setRefundTarget] = useState<SubOrder | null>(null);
  const [refundOpen, setRefundOpen] = useState(false);
  const [refundReason, setRefundReason] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const [exportError, setExportError] = useState(false);

  useEffect(() => {
    if (demo) return;

    let isMounted = true;
    adminOrderService.getOrders({ page: 0, size: 50 })
      .then(res => {
        if (!isMounted) return;
        const mappedOrders: MasterOrder[] = (res.content || []).map(o => ({
          id: o.id,
          customerId: o.customerId,
          totalAmount: o.totalAmount,
          discountAmount: o.discountAmount,
          discountCodeId: '',
          finalAmount: o.totalAmount - o.discountAmount,
          status: o.status as unknown as MasterOrder['status'],
          createdAt: o.createdAt,
          updatedAt: o.updatedAt,
        }));
        setMasterOrders(mappedOrders);
        setLoading(false);
      })
      .catch(err => {
        if (!isMounted) return;
        const msg = getAdminOrderError(err, 'Không thể tải danh sách đơn hàng.');
        setErrorMessage(msg);
        setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [demo]);

  const filteredOrders = useMemo(() => filterMasterOrders(masterOrders, searchQuery, statusFilter), [masterOrders, searchQuery, statusFilter]);
  const selectedMaster = masterOrders.find(order => order.id === selectedOrderId);
  const selectedSubs = selectedMaster ? relatedSubOrders(visibleSubs, selectedMaster.id) : [];

  const openRefund = (sub: SubOrder) => {
    if (!demo || sub.status === 'REFUNDED') return;
    setRefundTarget(sub);
    setRefundReason('');
    setRefundOpen(true);
  };
  const confirmRefund = () => {
    if (!refundTarget || !demo) return;
    const next = simulateRefund(subOrders, { demo, masterOrderId: refundTarget.masterOrderId, subOrderId: refundTarget.id, reason: refundReason });
    if (next === subOrders) return;
    setSubOrders(next);
    setSimulatedReasons(previous => ({ ...previous, [refundTarget.id]: refundReason.trim() }));
    setRefundOpen(false);
    setNotice(`Đã mô phỏng trạng thái REFUNDED cho #${refundTarget.id}. Không tạo giao dịch hoặc chuyển tiền.`);
  };
  const exportCsv = () => {
    setExportError(false);
    try {
      const url = URL.createObjectURL(new Blob(['\uFEFF', ordersToCsv(filteredOrders)], { type: 'text/csv;charset=utf-8;' }));
      const link = document.createElement('a');
      try {
        link.href = url;
        link.download = `DANASEA_Orders_${new Date().toISOString().slice(0, 10)}.csv`;
        document.body.appendChild(link);
        link.click();
      } finally {
        link.remove();
        URL.revokeObjectURL(url);
      }
      setNotice(`Đã tạo CSV gồm ${filteredOrders.length} đơn đang hiển thị từ dữ liệu mẫu.`);
    } catch {
      setExportError(true);
    }
  };

  return <main className="admin-workspace-page admin-orders">
    <AdminPreviewBanner demo={demo} href="/admin/orders?demo=1" />
    <div className="admin-workspace-summary" aria-label="Tổng quan đơn hàng">
      <div className="admin-workspace-metric"><span>Master Orders</span><strong>{masterOrders.length}</strong><small>{demo ? 'Đơn trong dữ liệu mẫu' : 'Đơn trong hệ thống'}</small></div>
      <div className="admin-workspace-metric"><span>Đã xác nhận</span><strong>{masterOrders.filter(order => order.status === 'CONFIRMED').length}</strong><small>Trạng thái đơn · không phải thanh toán</small></div>
      <div className="admin-workspace-metric"><span>SubOrders</span><strong>{visibleSubs.length}</strong><small>Dịch vụ con liên kết</small></div>
    </div>
    <AdminNotice message={notice} onDismiss={() => setNotice(null)} />
    {exportError && <AdminState compact kind="error" title="Không tạo được tệp CSV" description="Trình duyệt chưa thể tạo tệp. Hãy thử xuất lại danh sách đang lọc." action={<AdminButton onClick={exportCsv}>Thử xuất lại</AdminButton>} />}
    <section className="admin-orders-ledger" aria-label="Danh sách Master Orders">
      <AdminFilterTabs label="Lọc trạng thái Master Order" value={statusFilter} onChange={setStatusFilter} items={statuses.map(status => ({ ...status, count: status.value === 'all' ? masterOrders.length : masterOrders.filter(order => order.status === status.value).length }))} />
      <div className="admin-workspace-toolbar">
        <label className="admin-field admin-orders-search-field"><span>Tìm đơn hàng</span><div className="admin-search-field admin-orders-search"><Search size={18} aria-hidden="true" /><input type="search" value={searchQuery} onChange={event => setSearchQuery(event.target.value)} placeholder="Mã Master Order hoặc mã khách hàng" /></div></label>
        <p className="admin-orders-result" role="status" aria-live="polite">{filteredOrders.length} / {masterOrders.length} đơn · Giờ Việt Nam</p>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          {(searchQuery || statusFilter !== 'all') && <AdminButton onClick={() => { setSearchQuery(''); setStatusFilter('all'); }}>Xóa bộ lọc</AdminButton>}
          <AdminButton onClick={exportCsv} disabled={filteredOrders.length === 0}><Download size={16} aria-hidden="true" />Xuất CSV ({filteredOrders.length})</AdminButton>
        </div>
      </div>
      {loading && (
        <AdminState kind="empty" compact title="Đang tải danh sách đơn hàng..." description="Vui lòng đợi trong giây lát khi hệ thống kết nối máy chủ." />
      )}
      {errorMessage && !loading && (
        <AdminState kind="error" compact title="Không thể tải đơn hàng" description={errorMessage} />
      )}
      {!loading && !errorMessage && (
      <div key={statusFilter} className="admin-orders-table-scroll admin-tab-pane" role="region" aria-label="Bảng đơn hàng, cuộn ngang khi cần" tabIndex={0}>
        <table className="admin-workspace-table">
          <caption className="sr-only">Danh sách Master Orders</caption>
          <thead><tr><th scope="col">Mã đơn &amp; thời gian</th><th scope="col">Khách hàng</th><th scope="col">Dịch vụ con</th><th scope="col" className="admin-orders-money">Giá trị đơn</th><th scope="col">Trạng thái đơn</th><th scope="col">Thanh toán</th><th scope="col"><span className="sr-only">Chi tiết</span></th></tr></thead>
          <tbody>{filteredOrders.map(order => <tr key={order.id} data-selected={drawerOpen && order.id === selectedOrderId}>
            <td><strong className="admin-orders-id">#{order.id}</strong><small>{dateTime(order.createdAt)}</small></td>
            <td><strong>{order.customerId}</strong><small>Mã khách hàng</small></td>
            <td>{relatedSubOrders(visibleSubs, order.id).length} SubOrders</td>
            <td className="admin-orders-money"><strong>{money(order.totalAmount)}</strong>{order.discountAmount > 0 && <small>Giảm giá ghi nhận: {money(order.discountAmount)}</small>}</td>
            <td><OrderStatus status={order.status} /></td>
            <td><AdminBadge tone="neutral">Chưa có dữ liệu thanh toán</AdminBadge></td>
            <td><AdminButton aria-label={`Xem đơn ${order.id}`} onClick={() => { setSelectedOrderId(order.id); setDrawerOpen(true); }}>Xem đơn</AdminButton></td>
          </tr>)}</tbody>
        </table>
      </div>
      )}
      {!loading && !errorMessage && filteredOrders.length === 0 && <AdminState kind="empty" title="Không tìm thấy đơn hàng" description={masterOrders.length === 0 ? "Hiện tại chưa có đơn hàng nào trên sàn DANASEA." : "Thử tìm bằng mã Master Order, mã khách hàng hoặc xóa bộ lọc trạng thái."} action={<AdminButton onClick={() => { setSearchQuery(''); setStatusFilter('all'); }}>Xóa bộ lọc</AdminButton>} />}
    </section>
    <AdminDialog open={drawerOpen} onClose={() => setDrawerOpen(false)} variant="drawer" title={selectedMaster ? `Chi tiết đơn #${selectedMaster.id}` : 'Chi tiết đơn hàng'} description="Master / Sub Orders · Dữ liệu mẫu" footer={<div className="admin-drawer-actions"><AdminButton onClick={() => navigate(`/admin/disputes${demo ? '?demo=1' : ''}`)}>Tra soát / Khiếu nại</AdminButton><AdminButton variant="primary" onClick={() => setDrawerOpen(false)}>Đóng chi tiết</AdminButton></div>}>
      {selectedMaster && <OrderDetailContent key={selectedMaster.id} master={selectedMaster} subs={selectedSubs} demo={demo} simulatedReasons={demo ? simulatedReasons : {}} onRefund={openRefund} />}
    </AdminDialog>
    <AdminDialog open={refundOpen && demo} onClose={() => setRefundOpen(false)} title="Xác nhận mô phỏng hoàn tiền" description={`SubOrder #${refundTarget?.id || ''} · Chỉ thay đổi cục bộ trong DEMO MODE`} footer={<div className="admin-drawer-actions"><AdminButton onClick={() => setRefundOpen(false)}>Hủy</AdminButton><AdminButton variant="danger" disabled={!demo || !refundReason.trim()} onClick={confirmRefund}>Xác nhận mô phỏng</AdminButton></div>}>
      <p className="admin-detail-note">Thao tác chỉ đổi trạng thái đơn con thành REFUNDED trong phiên xem thử. Không tạo bản ghi Refund, không trích quỹ, không chuyển tiền và không thay đổi Master Order.</p>
      <label className="admin-field"><span>Lý do mô phỏng <span aria-hidden="true">*</span></span><textarea required rows={4} value={refundReason} onChange={event => setRefundReason(event.target.value)} placeholder="Nhập lý do để xác nhận mô phỏng" /></label>
    </AdminDialog>
  </main>;
}

export default AdminOrders;
