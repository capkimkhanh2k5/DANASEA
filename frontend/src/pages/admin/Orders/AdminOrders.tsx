import { useEffect, useMemo, useState } from 'react';
import { Download, Eye, FileCheck, Search } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  MOCK_ADMIN_MASTER_ORDERS,
  MOCK_ADMIN_SUB_ORDERS,
  MOCK_ADMIN_PAYMENTS,
  MOCK_ADMIN_REFUNDS,
  MOCK_ADMIN_USERS,
  MOCK_ADMIN_VENDORS,
  MOCK_ADMIN_SERVICES,
  type DemoMasterOrderExtended
} from '../../../data/adminMockData';
import { adminOrderService, getAdminOrderError } from '../../../services/adminOrderService';
import type { MasterOrder, SubOrder, Payment, Refund } from '../../../types';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import { AdminButton, AdminDisclosure, AdminFilterTabs, AdminNotice, AdminPagination } from '../../../components/admin/AdminWorkspace';
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
  const labelMap: Record<string, string> = {
    PENDING: 'Chờ xác nhận',
    CONFIRMED: 'Đã xác nhận',
    COMPLETED: 'Hoàn thành',
    CANCELLED: 'Đã hủy',
    REFUNDED: 'Đã hoàn tiền'
  };
  return <AdminBadge tone={tone}>{labelMap[status] || status}</AdminBadge>;
}

function OrderPaymentBadge({ order, payment }: { order: MasterOrder; payment?: Payment }) {
  const ext = order as DemoMasterOrderExtended;
  const pStatus = ext.paymentStatus || payment?.status;
  const provider = ext.paymentProvider || payment?.provider;

  if (pStatus === 'PAID' || pStatus === 'COMPLETED' || order.status === 'COMPLETED') {
    return <AdminBadge tone="teal">Đã thanh toán · {provider || 'Cổng TT'}</AdminBadge>;
  }
  if (pStatus === 'REFUNDED' || order.status === 'REFUNDED') {
    return <AdminBadge tone="rose">Đã hoàn tiền</AdminBadge>;
  }
  if (pStatus === 'FAILED' || order.status === 'CANCELLED') {
    return <AdminBadge tone="rose">Thanh toán thất bại</AdminBadge>;
  }
  if (pStatus === 'PENDING' || pStatus === 'UNPAID' || order.status === 'PENDING') {
    return <AdminBadge tone="amber">Chờ thanh toán</AdminBadge>;
  }
  return <AdminBadge tone="neutral">Chưa có dữ liệu thanh toán</AdminBadge>;
}

export function OrderDetailContent({
  master,
  subs,
  demo,
  simulatedReasons,
  onRefund,
  payment,
  refund
}: {
  master: MasterOrder;
  subs: SubOrder[];
  demo: boolean;
  simulatedReasons: Record<string, string>;
  onRefund: (sub: SubOrder) => void;
  payment?: Payment;
  refund?: Refund;
}) {
  const ext = master as DemoMasterOrderExtended;
  const customerUser = MOCK_ADMIN_USERS.find(u => u.id === master.customerId);
  const customerDisplayName = ext.customerName || customerUser?.fullName || master.customerId;

  return (
    <div className="admin-orders admin-orders-detail">
      {/* 1. Thông tin đơn */}
      <section className="admin-detail-section">
        <div className="admin-orders-detail-heading">
          <h3>Thông tin đơn</h3>
          <OrderStatus status={master.status} />
        </div>
        <dl className="admin-detail-grid">
          <div>
            <dt>Mã đơn hàng (Master)</dt>
            <dd><strong>#{master.id}</strong></dd>
          </div>
          <div>
            <dt>Khách hàng đặt</dt>
            <dd>
              <strong>{customerDisplayName}</strong>
              {customerUser?.phone && <span style={{ display: 'block', color: 'var(--admin-secondary, #525252)', fontSize: '12px' }}>{customerUser.phone}</span>}
            </dd>
          </div>
          <div>
            <dt>Mã tài khoản khách</dt>
            <dd><code>{master.customerId}</code></dd>
          </div>
          <div>
            <dt>Ngày tạo · giờ Việt Nam</dt>
            <dd>{dateTime(master.createdAt)}</dd>
          </div>
          <div>
            <dt>Cập nhật gần nhất</dt>
            <dd>{dateTime(master.updatedAt)}</dd>
          </div>
        </dl>
      </section>

      {/* 2. Giá trị đơn */}
      <section className="admin-detail-section">
        <h3>Giá trị đơn &amp; Ưu đãi</h3>
        <dl className="admin-orders-price">
          <div>
            <dt>Tổng giá trị dịch vụ (Gross)</dt>
            <dd>{money(master.totalAmount)}</dd>
          </div>
          <div>
            <dt>Giảm giá ghi nhận</dt>
            <dd style={{ color: master.discountAmount > 0 ? '#b91c1c' : undefined }}>
              {master.discountAmount > 0 ? `-${money(master.discountAmount)}` : '0 đ'}
            </dd>
          </div>
          <div>
            <dt>Mã khuyến mãi áp dụng</dt>
            <dd>{master.discountCodeId ? <AdminBadge tone="teal">{master.discountCodeId}</AdminBadge> : 'Không áp dụng'}</dd>
          </div>
          <div>
            <dt>Số tiền phải thanh toán (Net)</dt>
            <dd><strong style={{ color: 'var(--admin-primary, #0B5D6F)', fontSize: '16px' }}>{money(master.totalAmount - master.discountAmount)}</strong></dd>
          </div>
        </dl>
        <p className="admin-detail-note">Giá trị thanh toán khớp đúng tổng các đơn con trừ chiết khấu voucher áp dụng hợp lệ.</p>
      </section>

      {/* 3. Dịch vụ con */}
      <section className="admin-detail-section" aria-labelledby="order-subs-heading">
        <div className="admin-orders-detail-heading">
          <h3 id="order-subs-heading">Dịch vụ con ({subs.length})</h3>
          <span>{subs.length} gói dịch vụ</span>
        </div>
        {subs.length === 0 ? (
          <AdminState compact kind="empty" title="Đơn chưa có dịch vụ con" description="Không có dịch vụ con liên kết với đơn hàng này." />
        ) : (
          <div className="admin-orders-sub-list">
            {subs.map(sub => {
              const service = MOCK_ADMIN_SERVICES.find(s => s.id === sub.serviceId);
              const vendor = MOCK_ADMIN_VENDORS.find(v => v.id === sub.vendorId);
              const serviceTitle = service?.name || `Trải nghiệm #${sub.serviceId.substring(0, 8)}`;
              const vendorTitle = vendor?.businessName || `Đối tác #${sub.vendorId.substring(0, 8)}`;

              return (
                <article key={sub.id} className="admin-orders-sub">
                  <div className="admin-orders-sub-heading">
                    <div>
                      <h4>{serviceTitle}</h4>
                      <p>Số lượng: {sub.quantity} khách · Mã đơn con: #{sub.id}</p>
                    </div>
                    <OrderStatus status={sub.status} />
                  </div>
                  <div className="admin-orders-sub-value">
                    <span>Thành tiền:</span>
                    <strong>{money(sub.subtotalAmount)}</strong>
                  </div>
                  {demo && simulatedReasons[sub.id] && (
                    <p className="admin-orders-simulation">
                      <strong>Mô phỏng cục bộ:</strong> {simulatedReasons[sub.id]}. Không phải bằng chứng hoàn tiền.
                    </p>
                  )}

                  {/* Thông tin vận hành trực diện */}
                  <dl className="admin-detail-grid" style={{ margin: '12px 0 6px', background: '#f8fafc', padding: '10px 14px', borderRadius: '8px', border: '1px solid #edf2f5' }}>
                    <div><dt>Đơn vị vận hành</dt><dd><strong>{vendorTitle}</strong></dd></div>
                    <div><dt>Đơn giá</dt><dd>{money(sub.unitPrice)}</dd></div>
                    <div><dt>Suất dịch vụ</dt><dd>#{sub.slotId.substring(0, 8)}</dd></div>
                    <div><dt>Check-in</dt><dd>{sub.checkedInAt ? dateTime(sub.checkedInAt) : 'Chưa ghi nhận'}</dd></div>
                    <div><dt>Miễn trừ trách nhiệm</dt><dd>{sub.waiverAccepted ? 'Đã chấp thuận' : 'Chưa ghi nhận'}</dd></div>
                  </dl>

                <AdminDisclosure title={`Chi tiết đối soát & hoa hồng · ${sub.id}`} description="Hoa hồng sàn, thanh toán đối tác và chứng từ QR">
                  <dl className="admin-detail-grid">
                    <div><dt>Master Order liên kết</dt><dd>#{sub.masterOrderId}</dd></div>
                    <div><dt>Mã dịch vụ</dt><dd>{sub.serviceId}</dd></div>
                    <div><dt>Tỷ lệ hoa hồng</dt><dd>{sub.commissionRate}%</dd></div>
                    <div><dt>Hoa hồng sàn thu</dt><dd>{money(sub.commissionAmount)}</dd></div>
                    <div><dt>Giá trị đối tác nhận</dt><dd>{money(sub.vendorPayoutAmount)}</dd></div>
                    <div><dt>Thời điểm miễn trừ</dt><dd>{dateTime(sub.waiverAcceptedAt)}</dd></div>
                    <div><dt>Mã QR Check-in</dt><dd>{sub.qrSecret ? 'Đã tạo bảo mật' : 'Chưa tạo'}</dd></div>
                    <div><dt>Ngày tạo</dt><dd>{dateTime(sub.createdAt)}</dd></div>
                  </dl>
                  <p className="admin-detail-note">Hoa hồng sàn 10% được tính tự động từ giá trị dịch vụ của đối tác.</p>
                </AdminDisclosure>
                {sub.status !== 'REFUNDED' && (
                  <div className="admin-orders-sub-actions">
                    <AdminButton variant="danger" disabled={!demo} onClick={() => onRefund(sub)} aria-describedby="orders-demo-explanation">
                      Mô phỏng hoàn tiền
                    </AdminButton>
                  </div>
                )}
              </article>
            );
          })}
        </div>
        )}
        <p className="admin-detail-note" id="orders-demo-explanation">
          {demo ? 'Thao tác demo cần lý do và xác nhận, chỉ đổi trạng thái SubOrder trong phiên xem thử.' : 'Chỉ bật mô phỏng trong DEMO MODE ở môi trường phát triển.'} Không thực hiện chuyển tiền.
        </p>
      </section>

      {/* 4. Thanh toán & hoàn tiền */}
      <section className="admin-detail-section">
        <div className="admin-orders-detail-heading">
          <h3>Thanh toán &amp; hoàn tiền</h3>
          <OrderPaymentBadge order={master} payment={payment} />
        </div>

        {payment ? (
          <>
            <dl className="admin-detail-grid">
              <div>
                <dt>Mã giao dịch cổng</dt>
                <dd><code>{payment.providerTransactionId || payment.id}</code></dd>
              </div>
              <div>
                <dt>Cổng thanh toán</dt>
                <dd><strong>{payment.provider}</strong></dd>
              </div>
              <div>
                <dt>Số tiền thanh toán</dt>
                <dd><strong style={{ color: 'var(--admin-primary, #0B5D6F)' }}>{money(payment.amount)}</strong></dd>
              </div>
              <div>
                <dt>Thời gian xác nhận</dt>
                <dd>{dateTime((payment as unknown as { paidAt?: string }).paidAt || payment.createdAt)}</dd>
              </div>
              <div>
                <dt>Trạng thái giao dịch</dt>
                <dd>
                  <AdminBadge tone={payment.status === 'COMPLETED' ? 'teal' : payment.status === 'REFUNDED' ? 'rose' : payment.status === 'FAILED' ? 'rose' : 'amber'}>
                    {payment.status === 'COMPLETED' ? 'Giao dịch thành công' : payment.status === 'REFUNDED' ? 'Đã hoàn tiền' : payment.status === 'FAILED' ? 'Thất bại' : 'Đang xử lý'}
                  </AdminBadge>
                </dd>
              </div>
              <div>
                <dt>Mã lệnh hệ thống</dt>
                <dd><code>{payment.id}</code></dd>
              </div>
            </dl>

            {/* Chi tiết hoàn tiền nếu có */}
            {refund && (
              <div style={{ marginTop: '14px', padding: '12px 14px', background: '#fff1f2', borderRadius: '8px', border: '1px solid #ffe4e6' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <strong style={{ color: '#be123c', fontSize: '13px' }}>Thông tin hoàn tiền ({refund.status === 'PROCESSED' ? 'Đã hoàn tất' : 'Đang xử lý'})</strong>
                  <AdminBadge tone="rose">{money(refund.amount)}</AdminBadge>
                </div>
                <dl className="admin-detail-grid" style={{ margin: 0 }}>
                  <div><dt>Mã lệnh hoàn</dt><dd><code>{refund.id}</code></dd></div>
                  <div><dt>Lý do hoàn tiền</dt><dd>{refund.reason}</dd></div>
                  <div><dt>Thời điểm hoàn tất</dt><dd>{dateTime(refund.processedAt)}</dd></div>
                  <div><dt>Tỷ lệ hoàn</dt><dd>{refund.refundPercentage ? `${refund.refundPercentage}%` : '100%'}</dd></div>
                </dl>
              </div>
            )}

            <div className="admin-orders-invoice" style={{ marginTop: '14px' }}>
              <AdminButton
                variant="secondary"
                onClick={() => alert(`Mô phỏng: Đang tải tệp hóa đơn điện tử DANASEA-VAT-${master.id}.pdf`)}
              >
                <FileCheck size={16} aria-hidden="true" />
                Tải PDF hóa đơn điện tử VAT ({master.id})
              </AdminButton>
              <p className="admin-detail-note">Chứng từ đối soát và hóa đơn điện tử hợp lệ DANASEA VAT Invoice.</p>
            </div>
          </>
        ) : (
          <>
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
            <div className="admin-orders-invoice">
              <AdminButton disabled>
                <Download size={16} aria-hidden="true" />
                Tải PDF hóa đơn · chưa khả dụng
              </AdminButton>
              <p className="admin-detail-note">Chưa có tệp hóa đơn hoặc dữ liệu VAT để tải xuống.</p>
            </div>
          </>
        )}
      </section>
    </div>
  );
}

export function AdminOrders() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const demo = import.meta.env.DEV && searchParams.get('demo') === '1';

  const [masterOrders, setMasterOrders] = useState<MasterOrder[]>(() => (demo ? MOCK_ADMIN_MASTER_ORDERS : []));
  const [subOrders, setSubOrders] = useState<SubOrder[]>(() => (demo ? MOCK_ADMIN_SUB_ORDERS : []));
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

  // Tra cứu User và Payment nhanh bằng Map
  const customerMap = useMemo(() => new Map(MOCK_ADMIN_USERS.map(u => [u.id, u])), []);
  const paymentMap = useMemo(() => new Map(MOCK_ADMIN_PAYMENTS.map(p => [p.masterOrderId, p])), []);
  const refundMap = useMemo(() => new Map(MOCK_ADMIN_REFUNDS.map(r => [r.subOrderId, r])), []);

  useEffect(() => {
    if (demo) return;

    let isMounted = true;
    adminOrderService
      .getOrders({ page: 0, size: 50 })
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

  const filteredOrders = useMemo(
    () => filterMasterOrders(masterOrders, searchQuery, statusFilter),
    [masterOrders, searchQuery, statusFilter]
  );

  const PAGE_SIZE = 8;
  const [currentPage, setCurrentPage] = useState(1);
  const totalPages = Math.ceil(filteredOrders.length / PAGE_SIZE) || 1;
  const activePage = Math.min(Math.max(1, currentPage), totalPages);

  const paginatedOrders = useMemo(() => {
    const start = (activePage - 1) * PAGE_SIZE;
    return filteredOrders.slice(start, start + PAGE_SIZE);
  }, [filteredOrders, activePage]);
  const selectedMaster = masterOrders.find(order => order.id === selectedOrderId);
  const selectedSubs = selectedMaster ? relatedSubOrders(visibleSubs, selectedMaster.id) : [];
  const selectedPayment = selectedMaster ? paymentMap.get(selectedMaster.id) : undefined;
  const selectedRefund = selectedSubs.find(s => refundMap.has(s.id)) ? refundMap.get(selectedSubs.find(s => refundMap.has(s.id))!.id) : undefined;

  const openRefund = (sub: SubOrder) => {
    if (!demo || sub.status === 'REFUNDED') return;
    setRefundTarget(sub);
    setRefundReason('');
    setRefundOpen(true);
  };

  const confirmRefund = () => {
    if (!refundTarget || !demo) return;
    const next = simulateRefund(subOrders, {
      demo,
      masterOrderId: refundTarget.masterOrderId,
      subOrderId: refundTarget.id,
      reason: refundReason,
    });
    if (next === subOrders) return;
    setSubOrders(next);
    setSimulatedReasons(previous => ({ ...previous, [refundTarget.id]: refundReason.trim() }));
    setRefundOpen(false);
    setNotice(`Đã mô phỏng trạng thái REFUNDED cho #${refundTarget.id}. Không tạo giao dịch hoặc chuyển tiền.`);
  };

  const exportCsv = () => {
    setExportError(false);
    try {
      const url = URL.createObjectURL(
        new Blob(['\uFEFF', ordersToCsv(filteredOrders)], { type: 'text/csv;charset=utf-8;' })
      );
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

  return (
    <main className="admin-workspace-page admin-orders">
      <div className="admin-orders__summary-strip" aria-label="Tổng quan đơn hàng">
        <div className="admin-orders__summary-item">
          <span>Tổng Master Orders:</span>
          <strong style={{ color: 'var(--admin-ink, #171717)' }}>{masterOrders.length}</strong>
        </div>
        <div className="admin-orders__summary-divider" />
        <div className="admin-orders__summary-item">
          <span>Đã xác nhận:</span>
          <strong style={{ color: 'var(--admin-primary, #0B5D6F)' }}>
            {masterOrders.filter(order => order.status === 'CONFIRMED').length}
          </strong>
        </div>
        <div className="admin-orders__summary-divider" />
        <div className="admin-orders__summary-item">
          <span>Đã hoàn thành:</span>
          <strong style={{ color: '#0d9488' }}>
            {masterOrders.filter(order => order.status === 'COMPLETED').length}
          </strong>
        </div>
        <div className="admin-orders__summary-divider" />
        <div className="admin-orders__summary-item">
          <span>Tổng dịch vụ con:</span>
          <strong style={{ color: 'var(--admin-ink, #171717)' }}>{visibleSubs.length}</strong>
        </div>
      </div>

      <AdminNotice message={notice} onDismiss={() => setNotice(null)} />
      {exportError && (
        <AdminState
          compact
          kind="error"
          title="Không tạo được tệp CSV"
          description="Trình duyệt chưa thể tạo tệp. Hãy thử xuất lại danh sách đang lọc."
          action={<AdminButton onClick={exportCsv}>Thử xuất lại</AdminButton>}
        />
      )}

      <section className="admin-orders-ledger" aria-label="Danh sách Master Orders">
        <AdminFilterTabs
          label="Lọc trạng thái Master Order"
          value={statusFilter}
          onChange={val => {
            setStatusFilter(val);
            setCurrentPage(1);
          }}
          items={statuses.map(status => ({
            ...status,
            count: status.value === 'all'
              ? masterOrders.length
              : masterOrders.filter(order => order.status === status.value).length,
          }))}
        />

        <div className="admin-workspace-toolbar">
          <div className="admin-search-field admin-orders-search">
            <Search size={18} aria-hidden="true" />
            <input
              type="search"
              value={searchQuery}
              onChange={event => {
                setSearchQuery(event.target.value);
                setCurrentPage(1);
              }}
              placeholder="Tìm theo tên khách, mã Master Order hoặc mã khách hàng..."
              aria-label="Tìm kiếm đơn hàng"
            />
          </div>
          <p className="admin-orders-result" role="status" aria-live="polite">
            {filteredOrders.length} / {masterOrders.length} đơn
          </p>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            {(searchQuery || statusFilter !== 'all') && (
              <AdminButton
                onClick={() => {
                  setSearchQuery('');
                  setStatusFilter('all');
                  setCurrentPage(1);
                }}
              >
                Xóa bộ lọc
              </AdminButton>
            )}
            <AdminButton onClick={exportCsv} disabled={filteredOrders.length === 0}>
              <Download size={16} aria-hidden="true" />
              Xuất CSV ({filteredOrders.length})
            </AdminButton>
          </div>
        </div>

        {loading && (
          <AdminState
            kind="empty"
            compact
            title="Đang tải danh sách đơn hàng..."
            description="Vui lòng đợi trong giây lát khi hệ thống kết nối máy chủ."
          />
        )}
        {errorMessage && !loading && (
          <AdminState kind="error" compact title="Không thể tải đơn hàng" description={errorMessage} />
        )}

        {!loading && !errorMessage && (
          <div
            key={statusFilter}
            className="admin-orders-table-scroll admin-tab-pane"
            role="region"
            aria-label="Bảng đơn hàng, cuộn ngang khi cần"
            tabIndex={0}
          >
            <table className="admin-workspace-table">
              <caption className="sr-only">Danh sách Master Orders</caption>
              <thead>
                <tr>
                  <th scope="col">Mã đơn &amp; thời gian</th>
                  <th scope="col">Khách hàng</th>
                  <th scope="col">Dịch vụ con</th>
                  <th scope="col" className="admin-orders-money">Giá trị đơn</th>
                  <th scope="col">Trạng thái đơn</th>
                  <th scope="col">Thanh toán</th>
                  <th scope="col"><span className="sr-only">Chi tiết</span></th>
                </tr>
              </thead>
              <tbody>
                {paginatedOrders.map(order => {
                  const ext = order as DemoMasterOrderExtended;
                  const customer = customerMap.get(order.customerId);
                  const customerName = ext.customerName || customer?.fullName || order.customerId;
                  const customerSub = customer?.phone
                    ? customer.phone
                    : order.customerId.length > 16
                    ? `Mã: ${order.customerId.substring(0, 8)}...`
                    : `Mã: ${order.customerId}`;
                  const orderPayment = paymentMap.get(order.id);

                  return (
                    <tr key={order.id} data-selected={drawerOpen && order.id === selectedOrderId}>
                      <td>
                        <strong className="admin-orders-id">#{order.id}</strong>
                        <small>{dateTime(order.createdAt)}</small>
                      </td>
                      <td>
                        <strong style={{ display: 'block', color: 'var(--admin-ink, #171717)', fontSize: '13.5px' }}>
                          {customerName}
                        </strong>
                        <small>{customerSub}</small>
                      </td>
                      <td style={{ fontWeight: 600 }}>{relatedSubOrders(visibleSubs, order.id).length}</td>
                      <td className="admin-orders-money">
                        <strong>{money(order.totalAmount)}</strong>
                        {order.discountAmount > 0 && (
                          <small style={{ color: '#b91c1c' }}>Giảm: -{money(order.discountAmount)}</small>
                        )}
                      </td>
                      <td>
                        <OrderStatus status={order.status} />
                      </td>
                      <td>
                        <OrderPaymentBadge order={order} payment={orderPayment} />
                      </td>
                      <td>
                        <button
                          type="button"
                          className="admin-action-icon-btn"
                          aria-label={`Xem chi tiết đơn #${order.id}`}
                          title="Xem chi tiết đơn hàng"
                          onClick={() => {
                            setSelectedOrderId(order.id);
                            setDrawerOpen(true);
                          }}
                        >
                          <Eye size={17} aria-hidden="true" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {!loading && !errorMessage && filteredOrders.length > 0 && (
          <AdminPagination
            currentPage={activePage}
            totalPages={totalPages}
            totalRecords={filteredOrders.length}
            pageSize={PAGE_SIZE}
            recordLabel="đơn hàng"
            onPageChange={setCurrentPage}
          />
        )}

        {!loading && !errorMessage && filteredOrders.length === 0 && (
          <AdminState
            kind="empty"
            title="Không tìm thấy đơn hàng"
            description={
              masterOrders.length === 0
                ? 'Hiện tại chưa có đơn hàng nào trên sàn DANASEA.'
                : 'Thử tìm bằng mã Master Order, tên khách hàng hoặc xóa bộ lọc trạng thái.'
            }
            action={
              <AdminButton
                onClick={() => {
                  setSearchQuery('');
                  setStatusFilter('all');
                }}
              >
                Xóa bộ lọc
              </AdminButton>
            }
          />
        )}
      </section>

      {/* Drawer Chi tiết */}
      <AdminDialog
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        variant="drawer"
        title={selectedMaster ? `Chi tiết đơn #${selectedMaster.id}` : 'Chi tiết đơn hàng'}
        description="Master / Sub Orders · Dữ liệu đối soát hệ thống"
        footer={
          <div className="admin-drawer-actions">
            <AdminButton onClick={() => navigate(`/admin/disputes${demo ? '?demo=1' : ''}`)}>
              Tra soát / Khiếu nại
            </AdminButton>
            <AdminButton variant="primary" onClick={() => setDrawerOpen(false)}>
              Đóng chi tiết
            </AdminButton>
          </div>
        }
      >
        {selectedMaster && (
          <OrderDetailContent
            key={selectedMaster.id}
            master={selectedMaster}
            subs={selectedSubs}
            demo={demo}
            simulatedReasons={demo ? simulatedReasons : {}}
            onRefund={openRefund}
            payment={selectedPayment}
            refund={selectedRefund}
          />
        )}
      </AdminDialog>

      {/* Modal xác nhận mô phỏng hoàn tiền */}
      <AdminDialog
        open={refundOpen && demo}
        onClose={() => setRefundOpen(false)}
        title="Xác nhận mô phỏng hoàn tiền"
        description={`Dịch vụ con #${refundTarget?.id || ''} · Chỉ thay đổi cục bộ trong DEMO MODE`}
        footer={
          <div className="admin-drawer-actions">
            <AdminButton onClick={() => setRefundOpen(false)}>Hủy</AdminButton>
            <AdminButton
              variant="danger"
              disabled={!demo || !refundReason.trim()}
              onClick={confirmRefund}
            >
              Xác nhận mô phỏng
            </AdminButton>
          </div>
        }
      >
        <p className="admin-detail-note">
          Thao tác chỉ đổi trạng thái đơn con thành REFUNDED trong phiên xem thử. Không tạo bản ghi Refund, không trích quỹ, không chuyển tiền và không thay đổi Master Order.
        </p>
        <label className="admin-field">
          <span>
            Lý do mô phỏng <span aria-hidden="true">*</span>
          </span>
          <textarea
            required
            rows={4}
            value={refundReason}
            onChange={event => setRefundReason(event.target.value)}
            placeholder="Nhập lý do để xác nhận mô phỏng (ví dụ: Khách báo hủy trước 24h hoặc Cảng vụ cấm biển)"
          />
        </label>
      </AdminDialog>
    </main>
  );
}

export default AdminOrders;
