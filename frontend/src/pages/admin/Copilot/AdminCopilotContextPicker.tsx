import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  ShoppingBag,
  Scale,
  Search,
  X,
  Copy,
  Check,
  ChevronRight,
  ChevronLeft,
  Loader2,
  AlertCircle,
  Pin,
  Paperclip,
  Clock,
  Layers,
} from 'lucide-react';
import { formatDisplayCode, resolveDisplayCodeToUuid } from '../../../utils/displayCode';
import { formatVND } from './copilotArtifactHelpers';
import { adminOrderService } from '../../../services/adminOrderService';
import { adminDisputeService } from '../../../services/adminDisputeService';
import type { AdminOrderSummaryResponse } from '../../../types/adminOrder';
import type { DisputeResponse } from '../../../types/adminDispute';

export interface ContextSummary {
  type: 'ORDER' | 'DISPUTE';
  id: string; // raw UUID
  displayCode: string; // e.g. ORD-00010040
  title?: string;
  status?: string;
  amount?: number;
  subtitle?: string;
}

interface AdminCopilotContextPickerProps {
  open: boolean;
  onClose: () => void;
  onSelectContext: (type: 'ORDER' | 'DISPUTE', id: string, summary?: ContextSummary) => void;
  currentContextType?: 'ORDER' | 'DISPUTE' | null;
  currentContextId?: string | null;
}

export const AdminCopilotContextPicker: React.FC<AdminCopilotContextPickerProps> = ({
  open,
  onClose,
  onSelectContext,
  currentContextType,
  currentContextId,
}) => {
  const [activeTab, setActiveTab] = useState<'ORDER' | 'DISPUTE'>(currentContextType || 'ORDER');
  const [searchQuery, setSearchQuery] = useState('');
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  // Orders State
  const [orders, setOrders] = useState<AdminOrderSummaryResponse[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [ordersError, setOrdersError] = useState<string | null>(null);
  const [ordersPage, setOrdersPage] = useState(0);
  const [ordersTotalPages, setOrdersTotalPages] = useState(1);

  // Disputes State
  const [disputes, setDisputes] = useState<DisputeResponse[]>([]);
  const [disputesLoading, setDisputesLoading] = useState(false);
  const [disputesError, setDisputesError] = useState<string | null>(null);
  const [disputesPage, setDisputesPage] = useState(0);
  const [disputesTotalPages, setDisputesTotalPages] = useState(1);

  // Selected object state
  const [selectedItem, setSelectedItem] = useState<{
    type: 'ORDER' | 'DISPUTE';
    id: string;
    displayCode: string;
    order?: AdminOrderSummaryResponse;
    dispute?: DisputeResponse;
  } | null>(() => {
    if (currentContextId && currentContextType) {
      return {
        type: currentContextType,
        id: currentContextId,
        displayCode: formatDisplayCode(currentContextId, currentContextType === 'DISPUTE' ? 'DISP' : 'ORD'),
      };
    }
    return null;
  });

  // Manual UUID input state
  const [manualId, setManualId] = useState('');
  const [showManualInput, setShowManualInput] = useState(false);

  // Fetch Orders via real API
  const fetchOrders = useCallback(async (page: number) => {
    setOrdersLoading(true);
    setOrdersError(null);
    try {
      const res = await adminOrderService.getOrders({ page, size: 8 });
      setOrders(res.content || []);
      setOrdersTotalPages(res.totalPages || 1);
    } catch {
      setOrdersError('Không thể tải danh sách đơn hàng từ máy chủ.');
      setOrders([]);
    } finally {
      setOrdersLoading(false);
    }
  }, []);

  // Fetch Disputes via real API
  const fetchDisputes = useCallback(async (page: number) => {
    setDisputesLoading(true);
    setDisputesError(null);
    try {
      const res = await adminDisputeService.getDisputes({ page, size: 8 });
      setDisputes(res.content || []);
      setDisputesTotalPages(res.totalPages || 1);
    } catch {
      setDisputesError('Không thể tải danh sách khiếu nại từ máy chủ.');
      setDisputes([]);
    } finally {
      setDisputesLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!open) return;
    if (activeTab === 'ORDER') {
      fetchOrders(ordersPage);
    } else {
      fetchDisputes(disputesPage);
    }
  }, [open, activeTab, ordersPage, disputesPage, fetchOrders, fetchDisputes]);

  // Copy helper
  const handleCopyCode = (code: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const clean = code.replace(/^#/, '');
    navigator.clipboard.writeText(clean);
    setCopiedCode(clean);
    setTimeout(() => {
      setCopiedCode((prev) => (prev === clean ? null : prev));
    }, 2000);
  };

  // Filtered Orders
  const filteredOrders = useMemo(() => {
    if (!searchQuery.trim()) return orders;
    const q = searchQuery.trim().toLowerCase();
    return orders.filter((o) => {
      const displayCode = formatDisplayCode(o.id, 'ORD').toLowerCase();
      const rawId = (o.id || '').toLowerCase();
      const customerId = (o.customerId || '').toLowerCase();
      return (
        displayCode.includes(q) ||
        rawId.includes(q) ||
        customerId.includes(q) ||
        o.status.toLowerCase().includes(q) ||
        o.paymentStatus.toLowerCase().includes(q)
      );
    });
  }, [orders, searchQuery]);

  // Filtered Disputes
  const filteredDisputes = useMemo(() => {
    if (!searchQuery.trim()) return disputes;
    const q = searchQuery.trim().toLowerCase();
    return disputes.filter((d) => {
      const displayCode = formatDisplayCode(d.id, 'DISP').toLowerCase();
      const rawId = (d.id || '').toLowerCase();
      const orderCode = d.orderId ? formatDisplayCode(d.orderId, 'ORD').toLowerCase() : '';
      const reason = (d.reason || '').toLowerCase();
      const desc = (d.description || '').toLowerCase();
      return (
        displayCode.includes(q) ||
        rawId.includes(q) ||
        orderCode.includes(q) ||
        reason.includes(q) ||
        desc.includes(q)
      );
    });
  }, [disputes, searchQuery]);

  // Apply context
  const handleApply = () => {
    if (showManualInput && manualId.trim()) {
      const resolvedUuid = resolveDisplayCodeToUuid(manualId.trim());
      const dispCode = formatDisplayCode(resolvedUuid, activeTab === 'DISPUTE' ? 'DISP' : 'ORD');
      onSelectContext(activeTab, resolvedUuid, {
        type: activeTab,
        id: resolvedUuid,
        displayCode: dispCode,
        title: `${activeTab === 'DISPUTE' ? 'Khiếu nại' : 'Đơn hàng'} #${dispCode}`,
      });
      onClose();
      return;
    }

    if (!selectedItem) return;

    let summary: ContextSummary;
    if (selectedItem.type === 'ORDER' && selectedItem.order) {
      const o = selectedItem.order;
      summary = {
        type: 'ORDER',
        id: o.id,
        displayCode: selectedItem.displayCode,
        title: `Đơn hàng #${selectedItem.displayCode}`,
        status: o.status,
        amount: o.totalAmount,
        subtitle: `TT: ${o.paymentStatus} • ${formatVND(o.totalAmount)}`,
      };
    } else if (selectedItem.type === 'DISPUTE' && selectedItem.dispute) {
      const d = selectedItem.dispute;
      summary = {
        type: 'DISPUTE',
        id: d.id,
        displayCode: selectedItem.displayCode,
        title: `Khiếu nại #${selectedItem.displayCode}`,
        status: d.status,
        subtitle: `Đơn #${formatDisplayCode(d.orderId, 'ORD')} • ${d.reason || 'Tranh chấp'}`,
      };
    } else {
      summary = {
        type: selectedItem.type,
        id: selectedItem.id,
        displayCode: selectedItem.displayCode,
        title: `${selectedItem.type === 'DISPUTE' ? 'Khiếu nại' : 'Đơn hàng'} #${selectedItem.displayCode}`,
      };
    }

    onSelectContext(selectedItem.type, selectedItem.id, summary);
    onClose();
  };

  if (!open) return null;

  return (
    <div className="copilot-context-picker-backdrop" onClick={onClose}>
      <div
        className="copilot-context-picker"
        role="dialog"
        aria-modal="true"
        aria-labelledby="context-picker-title"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="copilot-context-picker__header">
          <div className="copilot-context-picker__title-group">
            <div className="copilot-context-picker__title-row">
              <Paperclip size={18} className="copilot-context-picker__title-icon" aria-hidden="true" />
              <h2 id="context-picker-title" className="copilot-context-picker__title">
                Ghim ngữ cảnh đối tượng
              </h2>
            </div>
            <p className="copilot-context-picker__desc">
              Chọn đơn hàng hoặc khiếu nại để DaNaBot hỗ trợ tra cứu và phân tích số liệu.
            </p>
          </div>
          <button
            type="button"
            className="copilot-context-picker__close"
            onClick={onClose}
            aria-label="Đóng cửa sổ"
          >
            <X size={17} />
          </button>
        </div>

        {/* Tabs */}
        <div className="copilot-context-picker__tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'ORDER'}
            className={`copilot-context-picker__tab ${
              activeTab === 'ORDER' ? 'copilot-context-picker__tab--active' : ''
            }`}
            onClick={() => {
              setActiveTab('ORDER');
              setSearchQuery('');
            }}
          >
            <ShoppingBag size={14} />
            <span>Đơn hàng (Order)</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'DISPUTE'}
            className={`copilot-context-picker__tab ${
              activeTab === 'DISPUTE' ? 'copilot-context-picker__tab--active' : ''
            }`}
            onClick={() => {
              setActiveTab('DISPUTE');
              setSearchQuery('');
            }}
          >
            <Scale size={14} />
            <span>Khiếu nại (Dispute)</span>
          </button>
        </div>

        {/* Search Input */}
        <div className="copilot-context-picker__search-row">
          <div className="copilot-context-picker__search-box">
            <Search size={14} className="copilot-context-picker__search-icon" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={
                activeTab === 'ORDER'
                  ? 'Tìm theo mã (#ORD-..., UUID), trạng thái...'
                  : 'Tìm theo mã (#DISP-..., UUID), lý do...'
              }
              className="copilot-context-picker__search-input"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="copilot-context-picker__search-clear"
                title="Xóa tìm kiếm"
              >
                <X size={13} />
              </button>
            )}
          </div>
        </div>

        {/* Scrollable Results List */}
        <div className="copilot-context-picker__list-container">
          {activeTab === 'ORDER' ? (
            ordersLoading ? (
              <div className="copilot-context-picker__feedback">
                <Loader2 size={18} className="copilot-context-picker__spin" />
                <span>Đang tải danh sách đơn hàng...</span>
              </div>
            ) : ordersError ? (
              <div className="copilot-context-picker__feedback copilot-context-picker__feedback--error">
                <AlertCircle size={18} />
                <span>{ordersError}</span>
                <button
                  type="button"
                  onClick={() => fetchOrders(ordersPage)}
                  className="copilot-context-picker__retry-btn"
                >
                  Thử lại
                </button>
              </div>
            ) : filteredOrders.length === 0 ? (
              <div className="copilot-context-picker__feedback copilot-context-picker__feedback--empty">
                <span>Không tìm thấy đơn hàng nào phù hợp.</span>
              </div>
            ) : (
              <div className="copilot-context-picker__grid">
                {filteredOrders.map((order) => {
                  const dispCode = formatDisplayCode(order.id, 'ORD');
                  const isSelected = selectedItem?.id === order.id && selectedItem.type === 'ORDER';
                  const isCopied = copiedCode === dispCode;

                  return (
                    <div
                      key={order.id}
                      className={`copilot-context-item ${
                        isSelected ? 'copilot-context-item--selected' : ''
                      }`}
                      onClick={() =>
                        setSelectedItem({
                          type: 'ORDER',
                          id: order.id,
                          displayCode: dispCode,
                          order,
                        })
                      }
                    >
                      <div className="copilot-context-item__top">
                        <div className="copilot-context-item__code-pill">
                          <span>#{dispCode}</span>
                          <button
                            type="button"
                            className="copilot-context-item__copy-btn"
                            title="Sao chép mã nghiệp vụ"
                            onClick={(e) => handleCopyCode(dispCode, e)}
                          >
                            {isCopied ? <Check size={11} className="text-emerald-600" /> : <Copy size={11} />}
                          </button>
                        </div>
                        <div className="copilot-context-item__badges">
                          <span
                            className={`copilot-context-badge copilot-context-badge--${order.status.toLowerCase()}`}
                          >
                            {order.status === 'CONFIRMED'
                              ? 'Đã xác nhận'
                              : order.status === 'PENDING'
                              ? 'Chờ xử lý'
                              : 'Đã hủy'}
                          </span>
                          <span
                            className={`copilot-context-badge copilot-context-badge--${order.paymentStatus.toLowerCase()}`}
                          >
                            {order.paymentStatus === 'PAID' ? 'Đã thanh toán' : 'Chưa thanh toán'}
                          </span>
                        </div>
                      </div>

                      <div className="copilot-context-item__bottom">
                        <div className="copilot-context-item__amount">
                          {formatVND(order.totalAmount)}
                        </div>
                        <div className="copilot-context-item__date">
                          <Clock size={11} />
                          <span>
                            {new Date(order.createdAt).toLocaleDateString('vi-VN', {
                              day: '2-digit',
                              month: '2-digit',
                              year: 'numeric',
                            })}
                          </span>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )
          ) : (
            disputesLoading ? (
              <div className="copilot-context-picker__feedback">
                <Loader2 size={18} className="copilot-context-picker__spin" />
                <span>Đang tải danh sách khiếu nại...</span>
              </div>
            ) : disputesError ? (
              <div className="copilot-context-picker__feedback copilot-context-picker__feedback--error">
                <AlertCircle size={18} />
                <span>{disputesError}</span>
                <button
                  type="button"
                  onClick={() => fetchDisputes(disputesPage)}
                  className="copilot-context-picker__retry-btn"
                >
                  Thử lại
                </button>
              </div>
            ) : filteredDisputes.length === 0 ? (
              <div className="copilot-context-picker__feedback copilot-context-picker__feedback--empty">
                <span>Không tìm thấy hồ sơ khiếu nại nào phù hợp.</span>
              </div>
            ) : (
              <div className="copilot-context-picker__grid">
                {filteredDisputes.map((dispute) => {
                  const dispCode = formatDisplayCode(dispute.id, 'DISP');
                  const orderCode = dispute.orderId ? formatDisplayCode(dispute.orderId, 'ORD') : null;
                  const isSelected = selectedItem?.id === dispute.id && selectedItem.type === 'DISPUTE';
                  const isCopied = copiedCode === dispCode;

                  return (
                    <div
                      key={dispute.id}
                      className={`copilot-context-item ${
                        isSelected ? 'copilot-context-item--selected' : ''
                      }`}
                      onClick={() =>
                        setSelectedItem({
                          type: 'DISPUTE',
                          id: dispute.id,
                          displayCode: dispCode,
                          dispute,
                        })
                      }
                    >
                      <div className="copilot-context-item__top">
                        <div className="copilot-context-item__code-pill">
                          <span>#{dispCode}</span>
                          <button
                            type="button"
                            className="copilot-context-item__copy-btn"
                            title="Sao chép mã nghiệp vụ"
                            onClick={(e) => handleCopyCode(dispCode, e)}
                          >
                            {isCopied ? <Check size={11} className="text-emerald-600" /> : <Copy size={11} />}
                          </button>
                        </div>
                        <span className="copilot-context-badge copilot-context-badge--dispute">
                          {dispute.status === 'OPEN'
                            ? 'Đang mở'
                            : dispute.status === 'UNDER_REVIEW'
                            ? 'Đang xem xét'
                            : 'Đã xử lý'}
                        </span>
                      </div>

                      <div className="copilot-context-item__desc">
                        {orderCode && <span className="copilot-context-item__subref">Đơn #{orderCode}</span>}
                        <span className="copilot-context-item__reason">{dispute.reason || dispute.description || 'Hồ sơ tranh chấp'}</span>
                      </div>

                      <div className="copilot-context-item__bottom">
                        <div className="copilot-context-item__date">
                          <Clock size={11} />
                          <span>
                            {new Date(dispute.createdAt).toLocaleDateString('vi-VN', {
                              day: '2-digit',
                              month: '2-digit',
                              year: 'numeric',
                            })}
                          </span>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )
          )}
        </div>

        {/* Pagination Bar */}
        <div className="copilot-context-picker__pagination">
          <span className="copilot-context-picker__page-info">
            Trang {(activeTab === 'ORDER' ? ordersPage : disputesPage) + 1} /{' '}
            {activeTab === 'ORDER' ? ordersTotalPages : disputesTotalPages}
          </span>
          <div className="copilot-context-picker__page-btns">
            <button
              type="button"
              disabled={activeTab === 'ORDER' ? ordersPage === 0 : disputesPage === 0}
              onClick={() => {
                if (activeTab === 'ORDER') setOrdersPage((p) => Math.max(0, p - 1));
                else setDisputesPage((p) => Math.max(0, p - 1));
              }}
              className="copilot-context-picker__page-btn"
            >
              <ChevronLeft size={13} />
              <span>Trước</span>
            </button>
            <button
              type="button"
              disabled={
                activeTab === 'ORDER'
                  ? ordersPage >= ordersTotalPages - 1
                  : disputesPage >= disputesTotalPages - 1
              }
              onClick={() => {
                if (activeTab === 'ORDER') setOrdersPage((p) => p + 1);
                else setDisputesPage((p) => p + 1);
              }}
              className="copilot-context-picker__page-btn"
            >
              <span>Sau</span>
              <ChevronRight size={13} />
            </button>
          </div>
        </div>

        {/* Selected Object Preview (Figure 3 Style) */}
        {selectedItem && (
          <div className="copilot-context-preview">
            <div className="copilot-context-preview__header">
              <div className="copilot-context-preview__label">
                <Layers size={13} />
                <span>
                  {selectedItem.type === 'ORDER' ? 'Đơn hàng đã chọn' : 'Hồ sơ khiếu nại đã chọn'}:
                </span>
                <strong className="copilot-context-preview__code">#{selectedItem.displayCode}</strong>
              </div>
              <button
                type="button"
                className="copilot-context-preview__copy-btn"
                onClick={(e) => handleCopyCode(selectedItem.displayCode, e)}
              >
                {copiedCode === selectedItem.displayCode ? (
                  <>
                    <Check size={11} className="text-emerald-600" />
                    <span>Đã chép mã</span>
                  </>
                ) : (
                  <>
                    <Copy size={11} />
                    <span>Chép mã {selectedItem.displayCode}</span>
                  </>
                )}
              </button>
            </div>
            <div className="copilot-context-preview__uuid">
              <span>UUID nội bộ: {selectedItem.id}</span>
            </div>
          </div>
        )}

        {/* Collapsible Manual UUID input for edge cases */}
        <div className="copilot-context-manual">
          <button
            type="button"
            className="copilot-context-manual__toggle"
            onClick={() => setShowManualInput(!showManualInput)}
          >
            <span>{showManualInput ? '− Thu gọn nhập UUID thủ công' : '+ Hoặc nhập UUID thủ công'}</span>
          </button>
          {showManualInput && (
            <div className="copilot-context-manual__body">
              <input
                type="text"
                value={manualId}
                onChange={(e) => setManualId(e.target.value)}
                placeholder="Nhập UUID (VD: 00010000-0000-0000-0000-000000000040) hoặc #ORD-..."
                className="copilot-context-manual__input"
              />
            </div>
          )}
        </div>

        {/* Modal Footer Actions */}
        <div className="copilot-context-picker__footer">
          <button
            type="button"
            className="copilot-context-picker__cancel-btn"
            onClick={onClose}
          >
            Hủy
          </button>
          <button
            type="button"
            className="copilot-context-picker__pin-btn"
            disabled={!selectedItem && (!showManualInput || !manualId.trim())}
            onClick={handleApply}
          >
            <Pin size={13} />
            <span>Ghim ngữ cảnh</span>
          </button>
        </div>
      </div>
    </div>
  );
};
