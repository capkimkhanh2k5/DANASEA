import { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { ArrowRight, Search, SlidersHorizontal } from 'lucide-react';
import { MOCK_ADMIN_VENDORS } from '../../../data/adminMockData';
import { adminVendorService, getAdminVendorError } from '../../../services/adminVendorService';
import type { AdminVendorResponse } from '../../../types/adminVendor';
import type { BadgeTier } from '../../../types';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import { AdminButton, AdminFilterTabs, AdminNotice, AdminPreviewBanner } from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import { applyVendorDecision, filterVendors, type VendorRecord, type VendorReviewState } from './vendorApprovalModel';
import { VendorProfile, VendorStatus } from './VendorProfile';
import './VendorApproval.css';

const tiers: BadgeTier[] = ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM'];

function mapVendorResponseToRecord(v: AdminVendorResponse): VendorRecord {
  return {
    id: v.id,
    userId: v.userId,
    businessName: v.businessName,
    taxCode: v.taxCode,
    email: '',
    phone: '',
    address: v.address || 'Đà Nẵng',
    avatarUrl: `https://ui-avatars.com/api/?name=${encodeURIComponent(v.businessName)}&background=00646F&color=fff`,
    ratingAvg: v.ratingAvg || 5.0,
    ratingCount: v.ratingCount || 0,
    verificationStatus: (v.verificationStatus === 'APPROVED' ? 'VERIFIED' : v.verificationStatus) as unknown as VendorRecord['verificationStatus'],
    badgeTier: (v.badgeTier === 'NONE' || !v.badgeTier ? 'BRONZE' : v.badgeTier) as BadgeTier,
    bankName: v.bankName || '',
    bankAccountNumber: v.bankAccountNumber || '',
    bankAccountHolder: v.bankAccountHolder || '',
    verifiedBy: v.verifiedBy || '',
    verifiedAt: v.verifiedAt || '',
    createdAt: v.createdAt,
    updatedAt: v.updatedAt,
  };
}

export function VendorApproval() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';
  const [review, setReview] = useState<VendorReviewState>(() => ({ vendors: demo ? MOCK_ADMIN_VENDORS : [], timeline: [] }));
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const [tierFilter, setTierFilter] = useState('all');
  const [tier, setTier] = useState<BadgeTier>('PLATINUM');
  const [decision, setDecision] = useState<'approve' | 'reject'>('approve');
  const [decisionOpen, setDecisionOpen] = useState(false);
  const [note, setNote] = useState('');
  const [documentName, setDocumentName] = useState('');
  const [documentOpen, setDocumentOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (demo) return;

    let isMounted = true;
    adminVendorService.getVendors({ page: 0, size: 50 })
      .then(res => {
        if (!isMounted) return;
        const mapped = (res.content || []).map(mapVendorResponseToRecord);
        setReview({ vendors: mapped, timeline: [] });
        setLoading(false);
      })
      .catch(err => {
        if (!isMounted) return;
        const msg = getAdminVendorError(err, 'Không thể tải danh sách đối tác.');
        setErrorMessage(msg);
        setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [demo]);

  const visibleVendors = review.vendors;
  const selected = visibleVendors.find(vendor => vendor.id === selectedId);
  const filtered = useMemo(() => filterVendors(visibleVendors, search, status, tierFilter), [visibleVendors, search, status, tierFilter]);
  const count = (value: string) => visibleVendors.filter(vendor => vendor.verificationStatus === value).length;
  const resetFilters = () => { setSearch(''); setStatus('all'); setTierFilter('all'); };
  const openProfile = (vendor: VendorRecord) => {
    setSelectedId(vendor.id);
    setTier(vendor.badgeTier);
    setNote('');
    setNotice(null);
    setDrawerOpen(true);
  };
  const confirmDecision = async () => {
    if (!selected || !decisionOpen) return;
    if (demo) {
      const action = { vendorId: selected.id, kind: decision, tier, note, at: new Date().toISOString() };
      setReview(previous => applyVendorDecision(previous, action, demo));
      setDecisionOpen(false);
      setNote('');
      setNotice(`Đã ${decision === 'approve' ? `cập nhật phê duyệt và hạng ${tier}` : 'ghi nhận từ chối'} cho ${selected.businessName} trong bản demo.`);
      return;
    }

    try {
      setIsSubmitting(true);
      if (decision === 'approve') {
        await adminVendorService.approveVendor(selected.id);
        setReview(prev => ({
          ...prev,
          vendors: prev.vendors.map(v => v.id === selected.id ? { ...v, verificationStatus: 'VERIFIED' as unknown as VendorRecord['verificationStatus'], badgeTier: tier } : v)
        }));
        setNotice(`Đã phê duyệt đối tác "${selected.businessName}".`);
      } else {
        await adminVendorService.rejectVendor(selected.id, note || 'Không đạt điều kiện xét duyệt');
        setReview(prev => ({
          ...prev,
          vendors: prev.vendors.map(v => v.id === selected.id ? { ...v, verificationStatus: 'REJECTED' as unknown as VendorRecord['verificationStatus'] } : v)
        }));
        setNotice(`Đã từ chối duyệt đối tác "${selected.businessName}".`);
      }
      setDecisionOpen(false);
      setNote('');
    } catch (err) {
      const msg = getAdminVendorError(err, 'Lỗi khi cập nhật trạng thái đối tác.');
      setNotice(msg);
      setDecisionOpen(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  return <div className="admin-workspace-page admin-vendors">
    <AdminPreviewBanner demo={demo} href={`${location.pathname}?demo=1`} />
    <div className="admin-workspace-summary" aria-label="Tổng quan hồ sơ">
      {[
        { label: 'Tổng hồ sơ', value: visibleVendors.length, detail: demo ? 'Trong bản xem trước' : 'Từ cơ sở dữ liệu', filter: 'all' },
        { label: 'Chờ duyệt', value: count('PENDING'), detail: 'Cần xem hồ sơ', filter: 'PENDING' },
        { label: 'Đã duyệt', value: count('VERIFIED'), detail: 'Đã cấp quyền kinh doanh', filter: 'VERIFIED' },
        { label: 'Từ chối', value: count('REJECTED'), detail: 'Hồ sơ chưa đạt', filter: 'REJECTED' },
      ].map(metric => <button key={metric.filter} type="button" className="admin-workspace-metric" aria-pressed={status === metric.filter}
        onClick={() => setStatus(metric.filter)}><span>{metric.label}</span><strong>{metric.value}</strong><small>{metric.detail}</small></button>)}
    </div>

    <section className="admin-vendors-list" aria-labelledby="vendor-list-title">
      <div className="admin-vendors-list-heading"><div><h2 id="vendor-list-title">Danh sách đối tác</h2><p>Mở hồ sơ để xem thông tin và lịch sử thao tác.</p></div>
        <span>{filtered.length} / {visibleVendors.length} hồ sơ</span></div>
      <AdminFilterTabs label="Lọc trạng thái hồ sơ" value={status} onChange={setStatus} items={[
        { value: 'all', label: 'Tất cả', count: visibleVendors.length },
        { value: 'PENDING', label: 'Chờ duyệt', count: count('PENDING') },
        { value: 'VERIFIED', label: 'Đã duyệt', count: count('VERIFIED') },
        { value: 'REJECTED', label: 'Từ chối', count: count('REJECTED') },
      ]} />
      <div className="admin-workspace-toolbar">
        <label className="admin-search-field"><Search size={18} aria-hidden="true" /><input type="search" value={search} onChange={event => setSearch(event.target.value)}
          aria-label="Tìm đối tác theo tên hoặc mã số thuế" placeholder="Tìm tên đối tác hoặc mã số thuế…" /></label>
        <label className="admin-field admin-vendors-tier-filter"><SlidersHorizontal size={16} aria-hidden="true" /><span>Huy hiệu</span>
          <select value={tierFilter} onChange={event => setTierFilter(event.target.value)}><option value="all">Tất cả hạng</option>{tiers.map(value => <option key={value}>{value}</option>)}</select></label>
        {(search || status !== 'all' || tierFilter !== 'all') && <AdminButton onClick={resetFilters}>Xóa bộ lọc</AdminButton>}
      </div>

      {loading && (
        <AdminState kind="empty" compact title="Đang tải danh sách đối tác..." description="Vui lòng đợi trong giây lát khi hệ thống kết nối máy chủ." />
      )}
      {errorMessage && !loading && (
        <AdminState kind="error" compact title="Không thể tải đối tác" description={errorMessage} />
      )}

      {!loading && !errorMessage && (
      <div key={status} className="admin-vendors-table-scroll admin-tab-pane" role="region" aria-label="Bảng đối tác" tabIndex={0}>
        <table className="admin-workspace-table admin-vendors-table"><caption className="sr-only">Hồ sơ đối tác; chọn Xem hồ sơ để mở chi tiết</caption><thead><tr>
          <th scope="col">Đối tác</th><th scope="col">Mã số thuế</th><th scope="col">Khu vực & đánh giá</th><th scope="col">Trạng thái</th><th scope="col">Huy hiệu</th><th scope="col"><span className="sr-only">Thao tác</span></th>
        </tr></thead><tbody>{filtered.map(vendor => <tr key={vendor.id} data-selected={vendor.id === selectedId}>
          <td><div className="admin-vendors-identity"><img src={vendor.avatarUrl} alt="" width="40" height="40" /><div><strong>{vendor.businessName}</strong>
            <span>{vendor.bankAccountHolder || vendor.email || 'Chưa cập nhật đại diện'}</span><small>{vendor.id}</small></div></div></td>
          <td><span className="admin-vendors-mono">{vendor.taxCode}</span><small>{vendor.bankName}</small></td>
          <td className="admin-vendors-area"><span>{vendor.address}</span><small>{vendor.ratingAvg} / 5 · {vendor.ratingCount} đánh giá</small></td>
          <td><VendorStatus status={vendor.verificationStatus} /></td><td><AdminBadge tone="neutral">{vendor.badgeTier}</AdminBadge></td>
          <td><AdminButton aria-label={`Xem hồ sơ ${vendor.businessName}`} aria-haspopup="dialog" aria-expanded={drawerOpen && vendor.id === selectedId}
            variant={vendor.id === selectedId ? 'primary' : 'secondary'} onClick={() => openProfile(vendor)}>Xem hồ sơ <ArrowRight size={15} aria-hidden="true" /></AdminButton></td>
        </tr>)}</tbody></table>
      </div>
      )}
      {!loading && !errorMessage && filtered.length === 0 && <AdminState kind="empty" compact title="Không có hồ sơ phù hợp" description="Thử tìm tên hoặc mã số thuế khác, hoặc xóa bộ lọc hiện tại."
        action={<AdminButton onClick={resetFilters}>Xóa bộ lọc</AdminButton>} />}
      <div className="admin-vendors-list-footer">Hiển thị {filtered.length} hồ sơ {demo ? 'mẫu' : 'từ hệ thống DANASEA'} · Chi tiết mở trong ngăn riêng, giữ nguyên danh sách và bộ lọc.</div>
    </section>
    {!drawerOpen && <AdminNotice message={notice} onDismiss={() => setNotice(null)} />}

    <AdminDialog open={drawerOpen} onClose={() => setDrawerOpen(false)} variant="drawer" title={selected?.businessName || 'Hồ sơ đối tác'} description="Hồ sơ đối tác · Rà soát tính hợp lệ và chứng từ"
      footer={selected && <div className="admin-drawer-actions admin-vendors-actions"><p>{demo ? 'Thao tác demo chỉ lưu trong phiên này. Tải lại trang sẽ khôi phục dữ liệu mẫu.' : 'Quyết định phê duyệt sẽ cập nhật trạng thái hoạt động thực của đối tác.'}</p>
        <div><AdminButton disabled={isSubmitting} variant="danger" onClick={() => { setNote(''); setDecision('reject'); setDecisionOpen(true); }}>Từ chối</AdminButton>
          <AdminButton disabled={isSubmitting} variant="primary" onClick={() => { setNote(''); setDecision('approve'); setDecisionOpen(true); }}>{selected.verificationStatus === 'VERIFIED' ? 'Cập nhật phê duyệt' : 'Phê duyệt đối tác'}</AdminButton></div></div>}>
      {selected && <><VendorProfile key={selected.id} vendor={selected} timeline={demo ? review.timeline.filter(event => event.vendorId === selected.id) : []} tier={tier} onTierChange={setTier} demo={demo}
        onPreview={name => { setDocumentName(name); setDocumentOpen(true); }} />
        {drawerOpen && <AdminNotice message={notice} onDismiss={() => setNotice(null)} />}</>}
    </AdminDialog>

    <AdminDialog open={decisionOpen} onClose={() => setDecisionOpen(false)} title={decision === 'reject' ? 'Xác nhận từ chối' : 'Xác nhận phê duyệt'}
      description={demo ? 'Chỉ thay đổi dữ liệu mẫu trong phiên hiện tại; không gửi quyết định đến đối tác.' : 'Thao tác sẽ gửi thông báo và cập nhật quyền bán dịch vụ của đối tác.'}
      footer={<><AdminButton onClick={() => setDecisionOpen(false)}>Quay lại hồ sơ</AdminButton><AdminButton variant={decision === 'reject' ? 'danger' : 'primary'} disabled={isSubmitting || !selected} onClick={confirmDecision}>
        {isSubmitting ? 'Đang xử lý...' : decision === 'reject' ? 'Xác nhận từ chối' : 'Xác nhận phê duyệt'}</AdminButton></>}>
      <div className="admin-vendors-confirm"><p><strong>{selected?.businessName}</strong></p>
        <p>{decision === 'reject' ? 'Trạng thái sẽ chuyển thành Từ chối; huy hiệu hiện tại được giữ nguyên.' : `Trạng thái sẽ chuyển thành Đã duyệt, huy hiệu ${tier}.`}</p>
        <label className="admin-field"><span>{decision === 'reject' ? 'Lý do từ chối' : 'Ghi chú quyết định'} <small>(tùy chọn)</small></span>
          <textarea value={note} onChange={event => setNote(event.target.value)} rows={4} placeholder="Ghi chú thẩm định…" /></label>
        <p className="admin-detail-note">Xác minh đầy đủ giấy phép kinh doanh và chứng nhận an toàn trước khi duyệt.</p></div>
    </AdminDialog>

    <AdminDialog open={documentOpen} onClose={() => setDocumentOpen(false)} title="Xem trước tài liệu mẫu" description={documentName}
      footer={<AdminButton onClick={() => setDocumentOpen(false)}>Đóng bản xem trước</AdminButton>}>
      <div className="admin-vendors-document-preview"><span className="material-symbols-outlined" aria-hidden="true">description</span><h3>Khung xem trước minh họa</h3>
        <p>{documentName}</p><p>Đây là tên tệp cố định trong giao diện mẫu. Không có nội dung PDF được tải từ hồ sơ đối tác và chưa có xác minh tính xác thực, chữ ký số hay hiệu lực chứng nhận.</p></div>
    </AdminDialog>
  </div>;
}

export default VendorApproval;
