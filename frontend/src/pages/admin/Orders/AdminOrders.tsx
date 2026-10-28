import { useState, useMemo, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { adminOrderService, getAdminOrderError } from '../../../services/adminOrderService';
import { useAuthStore } from '../../../store/useAuthStore';
import type {
  AdminOrderSummaryResponse,
  OrderResponse,
  PaymentResponse,
  SubOrderResponse,
} from '../../../types/adminOrder';

export function AdminOrders() {
  const navigate = useNavigate();
  const accessToken = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const userRole = useAuthStore(state => state.user?.role);
  const canAccess = isAuthenticated && !!accessToken && userRole === 'ADMIN';

  // Data state
  const [masterOrders, setMasterOrders] = useState<AdminOrderSummaryResponse[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<string>('');
  const [selectedOrderDetail, setSelectedOrderDetail] = useState<OrderResponse | null>(null);
  const [loadedPayments, setLoadedPayments] = useState<{ orderId: string; items: PaymentResponse[] } | null>(null);

  // Loading & error state
  const [isLoading, setIsLoading] = useState(false);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Filter state
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [totalOrdersCount, setTotalOrdersCount] = useState<number>(0);

  // Refund Guidance Modal
  const [isRefundModalOpen, setIsRefundModalOpen] = useState(false);
  const [refundTargetSubOrder, setRefundTargetSubOrder] = useState<SubOrderResponse | null>(null);

  // Toast
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  };

  // Request sequence ref for detail loading
  const detailReqSeqRef = useRef<number>(0);

  // Initial load of master orders (tối đa 100 bản ghi mới nhất)
  useEffect(() => {
    let active = true;
    if (!canAccess) return;

    void (async () => {
      try {
        setIsLoading(true);
        setError(null);
        const data = await adminOrderService.getOrders({ size: 100 });
        if (!active) return;
        const items = data.content || [];
        setMasterOrders(items);
        setTotalOrdersCount(data.totalElements ?? items.length);
        if (items.length > 0) {
          setSelectedOrderId(prev => (items.some(o => o.id === prev) ? prev : items[0].id));
        }
      } catch (err) {
        if (active) {
          setError(getAdminOrderError(err, 'Không thể tải danh sách đơn hàng từ máy chủ'));
        }
      } finally {
        if (active) setIsLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [canAccess]);

  // Load order detail & payments asynchronously when selected order changes
  useEffect(() => {
    let active = true;
    if (!selectedOrderId || !canAccess) return;

    const seq = ++detailReqSeqRef.current;
    const targetOrderId = selectedOrderId;

    void (async () => {
      try {
        setIsDetailLoading(true);
        setActionError(null);
        // Không nuốt lỗi thanh toán để bảo đảm tính toàn vẹn dữ liệu đối soát
        const [detail, paymentPage] = await Promise.all([
          adminOrderService.getOrder(targetOrderId),
          adminOrderService.getPayments(targetOrderId),
        ]);

        if (!active || detailReqSeqRef.current !== seq) return;
        setSelectedOrderDetail(detail);
        setLoadedPayments({ orderId: targetOrderId, items: paymentPage.content || [] });
      } catch (err) {
        if (!active || detailReqSeqRef.current !== seq) return;
        setActionError(getAdminOrderError(err, 'Không thể tải chi tiết dịch vụ hoặc thông tin thanh toán của đơn hàng'));
      } finally {
        if (active && detailReqSeqRef.current === seq) {
          setIsDetailLoading(false);
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [selectedOrderId, canAccess]);

  const handleSelectOrder = (orderId: string) => {
    if (orderId === selectedOrderId) return;
    setSelectedOrderId(orderId);
    setSelectedOrderDetail(null);
    setLoadedPayments(null);
    setActionError(null);
  };

  // Manual refresh handler
  const handleRefresh = async () => {
    if (!canAccess || isLoading) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await adminOrderService.getOrders({ size: 100 });
      const items = data.content || [];
      setMasterOrders(items);
      setTotalOrdersCount(data.totalElements ?? items.length);
      if (items.length > 0) {
        setSelectedOrderId(prev => (items.some(o => o.id === prev) ? prev : items[0].id));
      }
    } catch (err) {
      setError(getAdminOrderError(err, 'Không thể tải danh sách đơn hàng từ máy chủ'));
    } finally {
      setIsLoading(false);
    }
  };

  const filteredOrders = useMemo(() => {
    return masterOrders.filter(order => {
      const matchSearch =
        order.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
        order.customerId.toLowerCase().includes(searchQuery.toLowerCase());
      const matchStatus = statusFilter === 'all' || order.status === statusFilter;
      return matchSearch && matchStatus;
    });
  }, [masterOrders, searchQuery, statusFilter]);

  const selectedMaster = masterOrders.find(o => o.id === selectedOrderId) || masterOrders[0] || null;
  const relatedSubOrders = selectedOrderDetail?.id === selectedMaster?.id ? selectedOrderDetail.items || [] : [];
  const currentPayments = (loadedPayments?.orderId === selectedMaster?.id) ? loadedPayments.items : null;

  const handleOpenRefundGuidance = (sub: SubOrderResponse) => {
    setRefundTargetSubOrder(sub);
    setIsRefundModalOpen(true);
  };

  const handleExportCSV = () => {
    // Hàm chuẩn hóa chuỗi CSV chống lỗi Injection công thức (=, +, -, @)
    const sanitizeCsvCell = (val: string | number | undefined | null) => {
      if (val === undefined || val === null) return '""';
      let str = String(val).replace(/"/g, '""');
      if (/^[=+\-@\t\r]/.test(str)) {
        str = `'${str}`;
      }
      return `"${str}"`;
    };

    const headers = 'Mã Đơn,Khách Hàng,Trạng Thái,Thanh Toán,Tổng Tiền,Giảm Giá,Thời Gian\n';
    const rows = filteredOrders
      .map(
        o =>
          `${sanitizeCsvCell(o.id)},${sanitizeCsvCell(o.customerId)},${sanitizeCsvCell(o.status)},${sanitizeCsvCell(o.paymentStatus)},${sanitizeCsvCell(o.totalAmount)},${sanitizeCsvCell(o.discountAmount)},${sanitizeCsvCell(o.createdAt || '')}`
      )
      .join('\n');

    const blob = new Blob([headers + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `DANASEA_Orders_Report_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast(`Đã xuất ${filteredOrders.length} đơn hàng sang file CSV (tối đa 100 bản ghi hiện hành)!`);
  };

  if (!canAccess) {
    return (
      <main className="w-full pt-16 bg-surface px-space-xl pb-space-2xl min-h-screen">
        <div className="max-w-xl mx-auto mt-12 p-space-xl rounded-2xl bg-white border border-slate-200 text-center shadow-xs">
          <span className="material-symbols-outlined text-[48px] text-amber-500 mb-2">lock</span>
          <h2 className="text-lg font-bold text-slate-800">Yêu cầu quyền Quản trị viên</h2>
          <p className="text-xs text-slate-500 mt-1">
            Vui lòng đăng nhập với tài khoản có quyền ADMIN để quản lý đơn hàng sàn DANASEA.
          </p>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full pt-16 bg-surface px-space-xl pb-space-2xl min-h-screen">
      <div className="flex flex-col w-full animate-fade-in-up">
        {/* Toast */}
        {toastMessage && (
          <div className="fixed top-20 right-8 z-50 flex items-center gap-3 px-4 py-3 rounded-xl bg-primary text-on-primary shadow-xl animate-fade-in-up">
            <span className="material-symbols-outlined text-[20px]">check_circle</span>
            <span className="font-label-md text-label-md font-semibold">{toastMessage}</span>
          </div>
        )}

        {/* Global error banner */}
        {error && (
          <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 flex items-center justify-between text-xs">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[18px]">error</span>
              <span>{error}</span>
            </div>
            <button onClick={handleRefresh} className="font-bold underline hover:no-underline">
              Thử lại
            </button>
          </div>
        )}

        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 mb-4 border-b border-slate-200">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl md:text-2xl font-bold text-slate-800 tracking-tight">
                Quản lý Đơn hàng
              </h1>
              <div className="hidden sm:flex items-center gap-2 text-xs font-medium">
                <span className="px-2.5 py-1 rounded-md bg-white border border-slate-200 text-slate-600">
                  Hiển thị: <strong>{masterOrders.length}</strong> / <strong>{totalOrdersCount || masterOrders.length}</strong>
                </span>
                <span className="px-2.5 py-1 rounded-md bg-primary/10 text-primary border border-primary/20">
                  Đã xác nhận: <strong>{masterOrders.filter(o => o.status === 'CONFIRMED').length}</strong>
                </span>
                <span className="px-2.5 py-1 rounded-md bg-amber-50 text-amber-700 border border-amber-200">
                  Chờ xử lý: <strong>{masterOrders.filter(o => o.status === 'PENDING').length}</strong>
                </span>
              </div>
            </div>
            <p className="text-slate-500 text-xs mt-1">
              Thống kê &amp; xuất file áp dụng cho danh sách hiển thị (tối đa 100 bản ghi mới nhất).
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleRefresh}
              disabled={isLoading}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 text-sm font-medium border border-slate-200 shadow-xs transition-colors disabled:opacity-50"
              title="Làm mới dữ liệu từ máy chủ"
            >
              <span className={`material-symbols-outlined text-[18px] ${isLoading ? 'animate-spin' : ''}`}>
                refresh
              </span>
              <span>Làm mới</span>
            </button>
            <button
              onClick={handleExportCSV}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 text-sm font-medium border border-slate-200 shadow-xs transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]">file_download</span>
              <span>Xuất CSV</span>
            </button>
          </div>
        </div>

        {/* Main 2-Column Split Console */}
        <div className="grid grid-cols-12 gap-space-lg items-start">
          {/* LEFT COLUMN: Master Orders Ledger Table */}
          <div className="col-span-12 xl:col-span-7 flex flex-col gap-space-md">
            {/* Filter Bar */}
            <div className="bg-white p-space-sm rounded-xl shadow-xs flex flex-col sm:flex-row gap-space-sm items-center justify-between border border-slate-200">
              <div className="relative flex-1 w-full">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-[18px]">
                  search
                </span>
                <input
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  className="w-full h-9 pl-9 pr-3 rounded-lg bg-slate-50 text-slate-800 text-xs placeholder:text-slate-400 focus:outline-none focus:bg-white focus:ring-1 focus:ring-primary border border-slate-200"
                  placeholder="Mã đơn (#order-...), Mã khách hàng..."
                  type="text"
                />
              </div>
              <div className="w-full sm:w-56">
                <select
                  value={statusFilter}
                  onChange={e => setStatusFilter(e.target.value)}
                  className="w-full h-9 px-2.5 rounded-lg bg-slate-50 text-slate-700 text-xs focus:outline-none focus:ring-1 focus:ring-primary border border-slate-200 cursor-pointer"
                >
                  <option value="all">Tất cả trạng thái</option>
                  <option value="CONFIRMED">Đã xác nhận (CONFIRMED)</option>
                  <option value="PENDING">Chờ xử lý (PENDING)</option>
                  <option value="CANCELLED">Đã hủy (CANCELLED)</option>
                </select>
              </div>
            </div>

            {/* Orders Table */}
            <div className="bg-white rounded-xl shadow-xs overflow-hidden border border-slate-200">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="bg-slate-50 text-slate-700 font-semibold border-b border-slate-200">
                      <th className="py-3 px-3.5 border-r border-slate-200 whitespace-nowrap">Mã đơn &amp; Thời gian</th>
                      <th className="py-3 px-3 border-r border-slate-200 whitespace-nowrap">Khách hàng</th>
                      <th className="py-3 px-2.5 text-center border-r border-slate-200 whitespace-nowrap">Dịch vụ con</th>
                      <th className="py-3 px-3 text-right border-r border-slate-200 whitespace-nowrap">Tổng tiền</th>
                      <th className="py-3 px-3 text-center whitespace-nowrap">Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 text-slate-700">
                    {filteredOrders.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="py-12 text-center text-slate-400">
                          <span className="material-symbols-outlined text-[32px] text-slate-300 mb-1">
                            folder_off
                          </span>
                          <p className="text-xs">Không tìm thấy đơn hàng nào phù hợp</p>
                        </td>
                      </tr>
                    ) : (
                      filteredOrders.map(order => {
                        const isSelected = order.id === selectedMaster?.id;

                        return (
                          <tr
                            key={order.id}
                            onClick={() => handleSelectOrder(order.id)}
                            className={`cursor-pointer transition-colors ${
                              isSelected
                                ? 'bg-primary/5 font-semibold border-l-4 border-primary'
                                : 'hover:bg-slate-50'
                            }`}
                          >
                            <td className="py-3 px-3.5 whitespace-nowrap border-r border-slate-200">
                              <span className="font-mono font-bold text-primary block">
                                #{order.id.slice(0, 8)}
                              </span>
                              <span className="text-[11px] text-slate-500">
                                {order.createdAt
                                  ? `${new Date(order.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} • ${new Date(order.createdAt).toLocaleDateString('vi-VN')}`
                                  : 'Mới tạo'}
                              </span>
                            </td>

                            <td className="py-3 px-3 whitespace-nowrap border-r border-slate-200">
                              <span
                                className="text-slate-800 font-medium block truncate max-w-[120px] font-mono text-[11px]"
                                title={order.customerId}
                              >
                                #{order.customerId.slice(0, 8)}
                              </span>
                              <span className="text-[10px] text-slate-400">Mã du khách</span>
                            </td>

                            <td className="py-3 px-2.5 text-center whitespace-nowrap border-r border-slate-200">
                              <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-slate-100 border border-slate-200 text-slate-700 font-bold text-[11px]">
                                {order.totalItems || 1} dịch vụ
                              </span>
                            </td>

                            <td className="py-3 px-3 text-right whitespace-nowrap border-r border-slate-200">
                              <span className="font-bold text-slate-800 text-sm block">
                                {(order.totalAmount || 0).toLocaleString('vi-VN')} đ
                              </span>
                              {order.discountAmount > 0 && (
                                <span className="text-[10px] text-primary font-medium block">
                                  -{(order.discountAmount || 0).toLocaleString('vi-VN')} đ
                                </span>
                              )}
                            </td>

                            <td className="py-3 px-3 text-center whitespace-nowrap">
                              <span
                                className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold ${
                                  order.status === 'CONFIRMED'
                                    ? 'bg-primary/10 text-primary border border-primary/20'
                                    : order.status === 'CANCELLED'
                                    ? 'bg-red-50 text-red-700 border border-red-200'
                                    : 'bg-amber-50 text-amber-700 border border-amber-200'
                                }`}
                              >
                                {order.status === 'CONFIRMED'
                                  ? 'ĐÃ XÁC NHẬN'
                                  : order.status === 'CANCELLED'
                                  ? 'ĐÃ HỦY'
                                  : 'CHỜ XỬ LÝ'}
                              </span>
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* RIGHT COLUMN: Order Detail Drawer */}
          {selectedMaster && (
            <div className="col-span-12 xl:col-span-5 flex flex-col gap-space-lg sticky top-20">
              <div className="bg-white rounded-xl shadow-xs p-space-lg flex flex-col gap-4 border border-slate-200">
                <div className="flex items-start justify-between pb-2 border-b border-slate-200">
                  <div>
                    <span className="font-label-sm text-xs text-primary font-medium">Chi tiết Master Order</span>
                    <h2 className="text-lg font-bold text-slate-800 font-mono">#{selectedMaster.id.slice(0, 8)}</h2>
                    <span className="text-[10px] text-slate-400 font-mono block">UUID: {selectedMaster.id}</span>
                  </div>
                  <span
                    className={`px-2.5 py-1 rounded-full font-bold text-xs ${
                      selectedMaster.status === 'CONFIRMED'
                        ? 'bg-primary/10 text-primary border border-primary/20'
                        : selectedMaster.status === 'CANCELLED'
                        ? 'bg-red-50 text-red-700 border border-red-200'
                        : 'bg-amber-50 text-amber-700 border border-amber-200'
                    }`}
                  >
                    {selectedMaster.status}
                  </span>
                </div>

                {actionError && (
                  <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs">
                    {actionError}
                  </div>
                )}

                {/* Customer Details */}
                <div className="bg-slate-50 border border-slate-200 p-3 rounded-xl flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-primary text-white flex items-center justify-center font-bold text-sm">
                    {selectedMaster.customerId.slice(0, 2).toUpperCase()}
                  </div>
                  <div className="min-w-0 flex-1">
                    <span className="font-bold text-xs text-slate-800 block truncate">
                      Khách hàng #{selectedMaster.customerId.slice(0, 8)}
                    </span>
                    <span className="text-[11px] text-slate-500 font-mono truncate block">
                      ID: {selectedMaster.customerId}
                    </span>
                  </div>
                </div>

                {/* Gateway Details */}
                <div className="bg-slate-50 border border-slate-200 p-4 rounded-xl flex flex-col gap-2">
                  <span className="text-xs font-semibold text-slate-800">Đối soát thanh toán (Payments)</span>
                  {isDetailLoading || currentPayments === null ? (
                    <div className="py-2.5 text-center text-xs text-slate-500 flex items-center justify-center gap-2">
                      <span className="material-symbols-outlined text-[16px] animate-spin text-primary">progress_activity</span>
                      <span>Đang đồng bộ giao dịch thanh toán...</span>
                    </div>
                  ) : (
                    <>
                      <div className="flex justify-between items-center text-xs">
                        <span className="text-slate-500">Cổng giao dịch:</span>
                        <span className="font-bold text-slate-800">
                          {currentPayments[0]?.provider || (currentPayments.length === 0 ? 'Chưa có thông tin' : '—')}
                        </span>
                      </div>
                      {currentPayments[0]?.providerTransactionId && (
                        <div className="flex justify-between items-center text-xs">
                          <span className="text-slate-500">Mã giao dịch cổng:</span>
                          <span className="font-mono text-slate-700">
                            {currentPayments[0].providerTransactionId}
                          </span>
                        </div>
                      )}
                      <div className="flex justify-between items-center text-xs">
                        <span className="text-slate-500">Trạng thái thanh toán:</span>
                        <span
                          className={`font-bold ${
                            selectedMaster.paymentStatus === 'PAID'
                              ? 'text-primary'
                              : selectedMaster.paymentStatus === 'REFUNDED'
                              ? 'text-red-600'
                              : 'text-amber-600'
                          }`}
                        >
                          {selectedMaster.paymentStatus || 'PENDING'}
                        </span>
                      </div>
                      <div className="flex justify-between items-center text-xs">
                        <span className="text-slate-500">Tổng thanh toán:</span>
                        <span className="font-bold text-slate-800 text-sm">
                          {(selectedMaster.totalAmount || 0).toLocaleString('vi-VN')} đ
                        </span>
                      </div>
                      <div className="flex justify-between items-center text-xs">
                        <span className="text-slate-500">Khuyến mãi áp dụng:</span>
                        <span className="font-bold text-primary">
                          -{(selectedMaster.discountAmount || 0).toLocaleString('vi-VN')} đ
                        </span>
                      </div>
                    </>
                  )}
                </div>

                {/* Related Sub Orders List */}
                <div className="flex flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-800">
                      Danh sách dịch vụ con ({relatedSubOrders.length})
                    </span>
                    {isDetailLoading && (
                      <span className="text-[11px] text-slate-400 flex items-center gap-1">
                        <span className="material-symbols-outlined text-[12px] animate-spin">progress_activity</span>
                        Đang đồng bộ...
                      </span>
                    )}
                  </div>

                  {isDetailLoading && relatedSubOrders.length === 0 ? (
                    <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 text-center text-xs text-slate-500 flex items-center justify-center gap-2">
                      <span className="material-symbols-outlined text-[16px] animate-spin text-primary">progress_activity</span>
                      Đang tải danh sách dịch vụ con...
                    </div>
                  ) : relatedSubOrders.length === 0 ? (
                    <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 text-center text-xs text-slate-500">
                      Không có dịch vụ con nào trong đơn hàng này
                    </div>
                  ) : (
                    <div className="flex flex-col gap-2 max-h-52 overflow-y-auto">
                      {relatedSubOrders.map(sub => (
                        <div
                          key={sub.id}
                          className="p-2.5 rounded-lg bg-slate-50 border border-slate-200 flex items-center justify-between text-xs"
                        >
                          <div>
                            <span className="font-mono font-bold text-slate-800">#{sub.id.slice(0, 8)}</span>
                            <span className="text-[11px] text-slate-500 block">
                              Tour #{sub.serviceId.slice(0, 8)} (SL: {sub.quantity})
                            </span>
                            <span className="font-semibold text-slate-800">
                              {(sub.subtotalAmount || 0).toLocaleString('vi-VN')} đ
                            </span>
                          </div>
                          <div className="flex flex-col items-end gap-1">
                            <span
                              className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                                sub.status === 'REFUNDED'
                                  ? 'bg-red-50 text-red-700 border border-red-200'
                                  : 'bg-primary/10 text-primary border border-primary/20'
                              }`}
                            >
                              {sub.status}
                            </span>
                            {sub.status !== 'REFUNDED' && (
                              <button
                                type="button"
                                onClick={() => handleOpenRefundGuidance(sub)}
                                className="text-[11px] text-primary hover:underline font-semibold cursor-pointer"
                              >
                                Hoàn tiền
                              </button>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Actions */}
                <div className="flex flex-col gap-2 pt-2 border-t border-slate-200">
                  <button
                    onClick={() => navigate('/admin/disputes')}
                    className="w-full py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 font-semibold text-xs transition-colors flex items-center justify-center gap-1.5"
                  >
                    <span className="material-symbols-outlined text-[16px] text-primary">troubleshoot</span>
                    <span>Mở quy trình Tra soát / Khiếu nại</span>
                  </button>
                  <button
                    disabled
                    title="Backend hiện tại chưa hỗ trợ API xuất hóa đơn điện tử"
                    className="w-full py-2.5 rounded-xl bg-slate-100 text-slate-400 border border-slate-200 font-semibold text-xs flex items-center justify-center gap-1.5 cursor-not-allowed opacity-70"
                  >
                    <span className="material-symbols-outlined text-[16px]">download</span>
                    <span>Tải Bản Kê Hóa Đơn Điện Tử (Chưa hỗ trợ)</span>
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Refund Guidance Modal */}
      {isRefundModalOpen && refundTargetSubOrder && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-2xl p-space-xl max-w-md w-full shadow-2xl border border-outline-variant flex flex-col gap-4">
            <div className="w-12 h-12 rounded-full bg-primary/10 text-primary flex items-center justify-center">
              <span className="material-symbols-outlined text-[28px]">account_balance_wallet</span>
            </div>
            <div>
              <h3 className="font-headline-md font-bold text-on-surface">Quy chế Hoàn Tiền Đơn Hàng</h3>
              <p className="text-xs text-on-surface-variant mt-1.5 leading-relaxed">
                Đơn con <strong>#{refundTargetSubOrder.id.slice(0, 8)}</strong> có tổng giá trị là{' '}
                <strong>{(refundTargetSubOrder.subtotalAmount || 0).toLocaleString('vi-VN')} đ</strong>.
              </p>
            </div>

            <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs leading-relaxed">
              <strong>Lưu ý kiểm soát rủi ro tài chính:</strong> Theo quy chuẩn Backend DANASEA, việc hoàn tiền cho đơn hàng hợp lệ được xử lý qua 2 luồng chính:
              <ul className="list-disc ml-4 mt-1 space-y-0.5">
                <li>Khách hàng gửi yêu cầu hủy vé (Customer Refund Request).</li>
                <li>Admin phân xử khiếu nại (Dispute Resolution với quyết định hoàn tiền).</li>
              </ul>
            </div>

            <div className="flex justify-end gap-2 pt-1">
              <button
                onClick={() => setIsRefundModalOpen(false)}
                className="px-4 py-2 rounded-xl bg-surface-container text-xs font-semibold hover:bg-surface-container-high transition-colors"
              >
                Đóng
              </button>
              <button
                onClick={() => {
                  setIsRefundModalOpen(false);
                  navigate('/admin/disputes');
                }}
                className="px-5 py-2 rounded-xl bg-primary text-on-primary text-xs font-bold hover:bg-primary-container transition-colors shadow-sm flex items-center gap-1.5"
              >
                <span className="material-symbols-outlined text-[16px]">gavel</span>
                <span>Chuyển sang Phân xử Khiếu nại</span>
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </main>
  );
}

export default AdminOrders;
