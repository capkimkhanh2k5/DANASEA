import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { disputeService } from '../../../services/disputeService';
import type { DisputeResponse } from '../../../types/dispute';
import { 
  AtlasPageHeader, 
  AtlasSegmentedTabs, 
  AtlasBadge, 
  AtlasButton,
  AtlasPagination
} from '../../../components/vendor/atlas/AtlasDesignSystem';

export function Disputes() {
  const navigate = useNavigate();
  const [activeFilter, setActiveFilter] = useState('ALL');
  const [disputes, setDisputes] = useState<DisputeResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const fetchDisputes = useCallback(async () => {
    try {
      setIsLoading(true);
      const res = await disputeService.getVendorDisputes(activeFilter, page, size);
      setDisputes(res.content || []);
      setTotalPages(res.totalPages || 1);
      setTotalElements(res.totalElements || 0);
    } catch (err) {
      console.error('[VendorDisputes] Lỗi khi tải danh sách khiếu nại:', err);
    } finally {
      setIsLoading(false);
    }
  }, [activeFilter, page, size]);

  useEffect(() => {
    fetchDisputes();
  }, [fetchDisputes]);

  const renderStatusBadge = (status: string) => {
    switch (status) {
      case 'OPEN':
        return <AtlasBadge label="Giải trình" variant="info" />;
      case 'IN_PROGRESS':
      case 'UNDER_REVIEW':
        return <AtlasBadge label="Đang xử lý" variant="warning" />;
      case 'RESOLVED':
      case 'RESOLVED_REFUND':
      case 'RESOLVED_PARTIAL':
        return <AtlasBadge label="Hoàn tiền" variant="warning" />;
      case 'RESOLVED_REJECTED':
        return <AtlasBadge label="Bác bỏ (Thắng)" variant="success" />;
      case 'CLOSED':
        return <AtlasBadge label="Đã đóng" variant="neutral" />;
      default:
        return <AtlasBadge label={status} variant="neutral" />;
    }
  };

  const getReasonLabel = (reason: string) => {
    switch (reason) {
      case 'SERVICE_NOT_AS_DESCRIBED':
        return 'Không đúng mô tả';
      case 'VENDOR_NO_SHOW':
        return 'Vắng mặt / Hủy ca';
      case 'SAFETY_CONCERN':
        return 'An toàn / Áo phao';
      case 'PAYMENT_ISSUE':
        return 'Sự cố thanh toán';
      default:
        return reason || 'Khác';
    }
  };

  const openCount = disputes.filter(d => d.status === 'OPEN').length;
  const inProgressCount = disputes.filter(d => ['IN_PROGRESS', 'UNDER_REVIEW'].includes(d.status)).length;
  const resolvedCount = disputes.filter(d => ['RESOLVED', 'RESOLVED_REFUND', 'RESOLVED_PARTIAL', 'RESOLVED_REJECTED'].includes(d.status)).length;
  const closedCount = disputes.filter(d => d.status === 'CLOSED').length;

  const statusFilters = [
    { id: 'ALL', label: 'Tất cả', count: totalElements },
    { id: 'OPEN', label: 'Giải trình', count: openCount },
    { id: 'IN_PROGRESS', label: 'Xử lý', count: inProgressCount },
    { id: 'RESOLVED', label: 'Đã giải quyết', count: resolvedCount },
    { id: 'CLOSED', label: 'Đã đóng', count: closedCount },
  ];

  return (
    <div className="w-full max-w-[1400px] mx-auto px-6 py-5 space-y-5">
      {/* 1. Header Toolbar (Atlas Page Header) */}
      <AtlasPageHeader
        title="Quản lý Khiếu nại từ Du khách"
        actions={
          <div className="px-3.5 py-2 rounded-xl bg-slate-100 text-slate-800 font-semibold text-xs border border-slate-200">
            Tổng số: <strong className="font-mono text-slate-900">{totalElements}</strong> vụ việc
          </div>
        }
      />

      {/* 2. Unified Atlas Card Container */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden flex flex-col">
        {/* Segmented Tabs Top Border */}
        <div className="p-3 border-b border-slate-200/80 bg-slate-50/50">
          <AtlasSegmentedTabs
            activeTab={activeFilter}
            onChange={(tabId) => {
              setActiveFilter(tabId);
              setPage(0);
            }}
            tabs={statusFilters}
          />
        </div>

        {/* Disputes Table */}
        <div className="overflow-x-auto scrollbar-thin">
          <table className="w-full text-left border-collapse table-auto text-sm">
            <thead>
              <tr className="bg-[#f8f9fa] text-slate-500 text-xs uppercase tracking-wider font-semibold border-b border-slate-200/80">
                <th className="py-3.5 px-4">MÃ KHIẾU NẠI</th>
                <th className="py-3.5 px-3">ĐƠN HÀNG</th>
                <th className="py-3.5 px-3">NGUYÊN NHÂN</th>
                <th className="py-3.5 px-3">TRẠNG THÁI</th>
                <th className="py-3.5 px-3">NGÀY TIẾP NHẬN</th>
                <th className="py-3.5 px-4 text-center">THAO TÁC</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {isLoading ? (
                Array.from({ length: 4 }).map((_, i) => (
                  <tr key={`skeleton-${i}`} className="animate-pulse">
                    <td className="py-4 px-4"><div className="h-4 bg-slate-200 rounded w-24" /></td>
                    <td className="py-4 px-3"><div className="h-4 bg-slate-200 rounded w-24" /></td>
                    <td className="py-4 px-3"><div className="h-4 bg-slate-200 rounded w-32" /></td>
                    <td className="py-4 px-3"><div className="h-6 bg-slate-200 rounded-full w-20" /></td>
                    <td className="py-4 px-3"><div className="h-4 bg-slate-200 rounded w-24" /></td>
                    <td className="py-4 px-4 text-center"><div className="h-8 bg-slate-200 rounded-lg w-16 mx-auto" /></td>
                  </tr>
                ))
              ) : disputes.length > 0 ? (
                disputes.map((d) => (
                  <tr key={d.id} className="hover:bg-slate-50/70 transition-colors">
                    <td className="py-3.5 px-4 font-mono font-bold text-sm text-slate-900">
                      #DIS-{d.id.slice(0, 8).toUpperCase()}
                    </td>
                    <td className="py-3.5 px-3 font-mono text-sm text-slate-700">
                      #VE-{d.subOrderId.slice(0, 8).toUpperCase()}
                    </td>
                    <td className="py-3.5 px-3 text-sm text-slate-700">
                      {getReasonLabel(d.reason)}
                    </td>
                    <td className="py-3.5 px-3">
                      {renderStatusBadge(d.status)}
                    </td>
                    <td className="py-3.5 px-3 font-mono text-xs text-slate-500">
                      {new Date(d.createdAt).toLocaleDateString('vi-VN')}
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      <AtlasButton
                        variant="outline"
                        size="sm"
                        onClick={() => navigate(`/vendor/disputes/${d.id}`)}
                      >
                        Chi tiết
                      </AtlasButton>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="py-16 text-center text-sm text-slate-500">
                    Không có hồ sơ khiếu nại nào trong danh mục này.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Standard Atlas Pagination */}
        <AtlasPagination
          currentPage={page}
          totalPages={totalPages}
          totalItems={totalElements}
          pageSize={size}
          onPageChange={setPage}
          onPageSizeChange={(newSize) => {
            setSize(newSize);
            setPage(0);
          }}
        />
      </div>
    </div>
  );
}
