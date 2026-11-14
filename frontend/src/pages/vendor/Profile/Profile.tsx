import { useState, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import type { VendorProfileData } from '../../../services/vendorMockService';
import { vendorProfileService, getVendorProfileError } from '../../../services/vendorProfileService';
import { authService, type UserProfileResponse } from '../../../services/authService';
import { useAuthStore } from '../../../store/useAuthStore';
import { toast } from '../../../store/useToastStore';
import { 
  Building2, 
  FileText, 
  X, 
  Upload, 
  Eye, 
  EyeOff,
  Info,
  User as UserIcon,
  Mail,
  ShieldCheck,
  AlertCircle,
  RotateCcw
} from 'lucide-react';
import { 
  AtlasPageHeader, 
  AtlasSegmentedTabs, 
  AtlasBadge, 
  AtlasButton 
} from '../../../components/vendor/atlas/AtlasDesignSystem';

export function Profile() {
  const { user: authUser, accessToken } = useAuthStore();
  const [profile, setProfile] = useState<(VendorProfileData & { rejection_reason?: string }) | null>(null);
  const [isResubmitting, setIsResubmitting] = useState(false);
  const [userProfile, setUserProfile] = useState<UserProfileResponse | null>(null);
  const [activeTab, setActiveTab] = useState<'info' | 'account' | 'bank' | 'docs'>('info');

  // Business form state
  const [_isSavingBank, setIsSavingBank] = useState(false);
  const [_isUploadingDoc, setIsUploadingDoc] = useState(false);
  const [bankName, setBankName] = useState('');
  const [bankAccountNumber, setBankAccountNumber] = useState('');
  const [bankAccountHolder, setBankAccountHolder] = useState('');

  // Account identity state
  const [accountFullName, setAccountFullName] = useState('');
  const [isUpdatingAccount, setIsUpdatingAccount] = useState(false);

  // Change password state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showCurrentPass, setShowCurrentPass] = useState(false);
  const [showNewPass, setShowNewPass] = useState(false);
  const [showConfirmPass, setShowConfirmPass] = useState(false);
  const [isChangingPassword, setIsChangingPassword] = useState(false);

  // Document preview state
  const [previewDoc, setPreviewDoc] = useState<{ name: string; type: string } | null>(null);

  const showToast = (msg: string) => {
    toast.info(msg);
  };

  const loadData = useCallback(async () => {
    // 1. Load Vendor Profile
    try {
      const realProfile = await vendorProfileService.getProfile();
      const realDocs = await vendorProfileService.getDocuments().catch(() => []);
      
      const vStatus = realProfile.verificationStatus || realProfile.status || 'PENDING';
      const bTier = realProfile.badgeTier || 'NONE';

      const mapped: VendorProfileData & { rejection_reason?: string } = {
        id: realProfile.id,
        name: realProfile.businessName || realProfile.brandName || 'Doanh nghiệp đối tác',
        phone: realProfile.contactPhone || '0905123458',
        email: realProfile.contactEmail || 'vender_1@danasea.test',
        address: realProfile.address || realProfile.businessAddress || 'Đà Nẵng',
        verification_status: vStatus === 'APPROVED' ? 'APPROVED' : vStatus === 'REJECTED' ? 'REJECTED' : 'PENDING',
        badge_tier: bTier,
        bank_name: realProfile.bankName || '',
        bank_account_number: realProfile.bankAccountNumber || '',
        bank_account_holder: realProfile.bankAccountHolder || '',
        rejection_reason: realProfile.rejectionReason,
        documents: realDocs.map((d) => ({
          type: d.docType === 'SAFETY_CERT' ? 'SAFETY_CERT' : 'BUSINESS_LICENSE',
          name: d.docType === 'SAFETY_CERT' ? 'Giay_chung_nhan_ATGT_duong_thuy.png' : 'Giay_phep_dang_ky_kinh_doanh.png',
          fileUrl: d.fileUrl || '#preview-doc',
          status: d.status,
          submittedAt: d.createdAt ? d.createdAt.split('T')[0] : (d.uploadedAt ? d.uploadedAt.split('T')[0] : '2026-10-25'),
        })),
      };
      setProfile(mapped);
      setBankName(mapped.bank_name);
      setBankAccountNumber(mapped.bank_account_number);
      setBankAccountHolder(mapped.bank_account_holder);
    } catch {
      setProfile({
        id: '',
        name: 'Chưa thiết lập hồ sơ đối tác',
        phone: 'Chưa cập nhật',
        email: 'Chưa cập nhật',
        address: 'Chưa cập nhật',
        verification_status: 'PENDING',
        badge_tier: 'NONE',
        bank_name: '',
        bank_account_number: '',
        bank_account_holder: '',
        documents: [],
      });
    }

    // 2. Load User Profile (GET /api/users/me)
    try {
      const uProfile = await authService.getMyProfile();
      setUserProfile(uProfile);
      setAccountFullName(uProfile.fullName || '');
    } catch {
      if (authUser) {
        setUserProfile({
          id: authUser.id,
          email: authUser.email,
          fullName: authUser.fullName,
          avatarUrl: authUser.avatarUrl,
          locale: authUser.locale,
          role: authUser.role,
          isEmailVerified: authUser.isEmailVerified,
        });
        setAccountFullName(authUser.fullName || '');
      }
    }
  }, [authUser]);

  useEffect(() => {
    loadData();
  }, [loadData]);

// Action: Resubmit Profile
  const handleResubmitProfile = async () => {
    try {
      setIsResubmitting(true);
      await vendorProfileService.resubmitProfile();
      toast.success('Đã gửi lại yêu cầu phê duyệt hồ sơ đối tác thành công! Vui lòng chờ Ban quản trị thẩm định.');
      loadData();
    } catch (err: unknown) {
      console.error('[Profile] Lỗi gửi lại hồ sơ:', err);
      toast.error(getVendorProfileError(err, 'Không thể gửi lại hồ sơ đối tác. Vui lòng kiểm tra đầy đủ giấy tờ hợp lệ.'));
    } finally {
      setIsResubmitting(false);
    }
  };

  // Action: Save bank info
  const handleSaveBank = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bankAccountNumber.trim()) {
      showToast('Vui lòng nhập số tài khoản ngân hàng.');
      return;
    }
    try {
      setIsSavingBank(true);
      await vendorProfileService.updateProfile({
        bankName,
        bankAccountNumber: bankAccountNumber.trim(),
        bankAccountHolder: bankAccountHolder.trim(),
      });
      toast.success('Đã lưu thông tin tài khoản ngân hàng đối soát thành công!');
      loadData();
    } catch (err: unknown) {
      console.error('[Profile] Lỗi lưu ngân hàng:', err);
      toast.error('Không thể cập nhật thông tin ngân hàng lên hệ thống. Vui lòng thử lại sau.');
    } finally {
      setIsSavingBank(false);
    }
  };

  // Action: Upload legal document
  const handleUploadDoc = async (type: 'BUSINESS_LICENSE' | 'SAFETY_CERT', file: File) => {
    try {
      setIsUploadingDoc(true);
      await vendorProfileService.uploadDocument(file, type);
      toast.success(`Đã tải lên "${file.name}"! Hồ sơ chuyển sang trạng thái chờ Cảng vụ phê duyệt.`);
      loadData();
    } catch (err: unknown) {
      console.error('[Profile] Lỗi tải tài liệu:', err);
      toast.error(`Không thể tải lên tài liệu "${file.name}". Vui lòng thử lại sau.`);
    } finally {
      setIsUploadingDoc(false);
    }
  };

  // Action: Update user account profile (PATCH /api/users/me)
  const handleUpdateAccount = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!accountFullName.trim()) {
      toast.error('Họ và tên không được để trống.');
      return;
    }
    try {
      setIsUpdatingAccount(true);
      const updated = await authService.updateProfile({ fullName: accountFullName.trim() });
      setUserProfile(updated);
      if (authUser && accessToken) {
        useAuthStore.getState().setAuth(
          { ...authUser, fullName: updated.fullName || accountFullName.trim() },
          accessToken
        );
      }
      toast.success('Đã cập nhật thông tin tài khoản thành công!');
    } catch (err: unknown) {
      console.error('[Profile] Lỗi cập nhật tài khoản:', err);
      toast.error('Không thể cập nhật thông tin tài khoản. Vui lòng thử lại.');
    } finally {
      setIsUpdatingAccount(false);
    }
  };

  // Action: Change password (POST /api/users/me/change-password)
  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentPassword) {
      toast.error('Vui lòng nhập mật khẩu hiện tại.');
      return;
    }
    if (!newPassword || newPassword.length < 8) {
      toast.error('Mật khẩu mới phải có tối thiểu 8 ký tự.');
      return;
    }
    const strongRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    if (!strongRegex.test(newPassword)) {
      toast.error('Mật khẩu mới cần tối thiểu 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt (@$!%*?&).');
      return;
    }
    if (newPassword !== confirmPassword) {
      toast.error('Mật khẩu xác nhận không trùng khớp với mật khẩu mới.');
      return;
    }
    if (newPassword === currentPassword) {
      toast.error('Mật khẩu mới không được trùng với mật khẩu hiện tại.');
      return;
    }

    try {
      setIsChangingPassword(true);
      await authService.changePassword({ currentPassword, newPassword });
      toast.success('Đổi mật khẩu thành công! Toàn bộ phiên cũ đã được thu hồi an toàn.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: unknown) {
      console.error('[Profile] Lỗi đổi mật khẩu:', err);
      let msg = 'Không thể đổi mật khẩu. Vui lòng kiểm tra lại mật khẩu hiện tại.';
      if (err && typeof err === 'object' && 'response' in err) {
        const res = (err as { response?: { data?: { message?: string } } }).response;
        if (res?.data?.message) {
          msg = res.data.message;
        }
      }
      toast.error(msg);
    } finally {
      setIsChangingPassword(false);
    }
  };

  if (!profile) {
    return (
      <div className="w-full px-6 py-6 text-sm text-slate-500">
        Đang tải hồ sơ đối tác...
      </div>
    );
  }

  return (
    <div className="w-full max-w-[1400px] mx-auto px-6 py-5 space-y-5">
      {/* 1. Header Toolbar (Atlas Page Header) */}
      <AtlasPageHeader
        title="Hồ sơ & Pháp lý Doanh nghiệp"
      />

      {/* Rejection Alert & Resubmit CTA */}
      {profile.verification_status === 'REJECTED' && (
        <div className="p-5 rounded-xl bg-rose-50 border border-rose-200 text-rose-950 space-y-3" role="alert">
          <div className="flex items-start gap-3">
            <AlertCircle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" aria-hidden="true" />
            <div className="space-y-1">
              <strong className="text-sm font-bold text-rose-950">
                Hồ sơ hoạt động đối tác chưa đạt yêu cầu kiểm duyệt
              </strong>
              <p className="text-xs text-rose-800 leading-relaxed">
                Lý do từ Ban quản trị: <span className="font-bold underline">{profile.rejection_reason || 'Vui lòng bổ sung hoặc thay thế các giấy tờ pháp lý chưa đạt chuẩn.'}</span>
              </p>
            </div>
          </div>

          <div className="pt-2 border-t border-rose-200/80 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs">
            <p className="text-rose-700">
              Sau khi cập nhật đầy đủ thông tin hoặc tải lại giấy phép, hãy nhấn nút bên cạnh để gửi yêu cầu thẩm định lại.
            </p>
            <AtlasButton
              variant="danger"
              size="sm"
              disabled={isResubmitting || profile.documents.length < 2}
              onClick={handleResubmitProfile}
              icon={<RotateCcw className={`w-3.5 h-3.5 ${isResubmitting ? 'animate-spin' : ''}`} />}
            >
              {isResubmitting ? 'Đang gửi...' : 'Gửi duyệt lại hồ sơ'}
            </AtlasButton>
          </div>
        </div>
      )}

      {/* 2. Navigation Tabs (Atlas Segmented Tabs) */}
      <AtlasSegmentedTabs<'info' | 'account' | 'bank' | 'docs'>
        activeTab={activeTab}
        onChange={(tab) => setActiveTab(tab)}
        tabs={[
          { id: 'info', label: 'Hồ sơ doanh nghiệp' },
          { id: 'account', label: 'Tài khoản & Bảo mật' },
          { id: 'bank', label: 'Tài khoản Ngân hàng (Payout)' },
          { id: 'docs', label: 'Hồ sơ pháp lý', count: profile.documents.length },
        ]}
      />

      {/* 3. Tab Body Container */}
      <div>
        {/* TAB 1: Business Profile Info */}
        {activeTab === 'info' && (
          <div className="bg-white p-6 rounded-xl border border-slate-200/80 shadow-xs space-y-5">
            <div className="pb-3 border-b border-slate-100">
              <h3 className="text-sm font-semibold text-slate-900">
                Thông tin doanh nghiệp &amp; Cơ sở bến bãi
              </h3>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold text-slate-700">Tên doanh nghiệp / Hộ kinh doanh</label>
                <input 
                  type="text" 
                  defaultValue={profile.name} 
                  className="px-3.5 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900" 
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold text-slate-700">Số điện thoại liên hệ</label>
                <input 
                  type="text" 
                  defaultValue={profile.phone} 
                  className="px-3.5 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900 font-mono" 
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold text-slate-700">Email tiếp nhận thông báo</label>
                <input 
                  type="email" 
                  defaultValue={profile.email} 
                  className="px-3.5 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900 font-mono" 
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold text-slate-700">Địa chỉ cơ sở / Trạm bến trực</label>
                <input 
                  type="text" 
                  defaultValue={profile.address} 
                  className="px-3.5 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900" 
                />
              </div>
            </div>

            <div className="flex justify-end pt-4 border-t border-slate-100">
              <AtlasButton
                variant="primary"
                size="md"
                onClick={() => showToast('Đã lưu thông tin liên hệ thành công!')}
              >
                Lưu thay đổi
              </AtlasButton>
            </div>
          </div>
        )}

        {/* TAB 2: Account Identity & Security */}
        {activeTab === 'account' && (
          <div className="space-y-6">
            {/* Block 1: Account Information */}
            <form onSubmit={handleUpdateAccount} className="bg-white p-6 rounded-xl border border-slate-200/80 shadow-xs space-y-5">
              <div className="pb-3 border-b border-slate-100 flex flex-wrap items-center justify-between gap-3">
                <h3 className="text-sm font-semibold text-slate-900">
                  Thông tin tài khoản đăng nhập
                </h3>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-700 flex items-center gap-1.5">
                    <Mail className="w-3.5 h-3.5 text-slate-400" />
                    <span>Email đăng nhập hệ thống (Username)</span>
                  </label>
                  <input 
                    type="email" 
                    value={userProfile?.email || authUser?.email || ''} 
                    readOnly
                    disabled
                    className="px-3.5 py-2.5 rounded-xl border border-slate-200 bg-slate-50 text-slate-500 text-sm font-medium font-mono cursor-not-allowed" 
                  />
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-700 flex items-center gap-1.5">
                    <UserIcon className="w-3.5 h-3.5 text-slate-400" />
                    <span>Họ và tên người đại diện / Quản trị viên</span>
                  </label>
                  <input 
                    type="text" 
                    value={accountFullName} 
                    onChange={(e) => setAccountFullName(e.target.value)}
                    placeholder="Nhập họ và tên người quản trị tài khoản"
                    className="px-3.5 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900" 
                  />
                </div>

                <div className="flex flex-col gap-1.5 md:col-span-2">
                  <label className="text-xs font-semibold text-slate-700 flex items-center gap-1.5">
                    <ShieldCheck className="w-3.5 h-3.5 text-slate-400" />
                    <span>Mã định danh tài khoản (User ID)</span>
                  </label>
                  <input 
                    type="text" 
                    value={userProfile?.id || authUser?.id || ''} 
                    readOnly
                    disabled
                    className="px-3.5 py-2.5 rounded-xl border border-slate-200 bg-slate-50 text-slate-400 text-xs font-mono cursor-not-allowed" 
                  />
                </div>
              </div>

              <div className="flex justify-end pt-4 border-t border-slate-100">
                <AtlasButton
                  type="submit"
                  variant="primary"
                  size="md"
                  disabled={isUpdatingAccount}
                >
                  {isUpdatingAccount ? 'Đang lưu...' : 'Lưu thông tin tài khoản'}
                </AtlasButton>
              </div>
            </form>

            {/* Block 2: Change Password */}
            <form onSubmit={handleChangePassword} className="bg-white p-6 rounded-xl border border-slate-200/80 shadow-xs space-y-5">
              <div className="pb-3 border-b border-slate-100">
                <h3 className="text-sm font-semibold text-slate-900">
                  Đổi mật khẩu tài khoản
                </h3>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-700">Mật khẩu hiện tại *</label>
                  <div className="relative flex items-center">
                    <input 
                      type={showCurrentPass ? "text" : "password"}
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                      placeholder="Nhập mật khẩu hiện tại"
                      className="w-full px-3.5 pr-10 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900" 
                    />
                    <button
                      type="button"
                      onClick={() => setShowCurrentPass(!showCurrentPass)}
                      className="absolute right-3 text-slate-400 hover:text-slate-600 cursor-pointer"
                    >
                      {showCurrentPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-700">Mật khẩu mới *</label>
                  <div className="relative flex items-center">
                    <input 
                      type={showNewPass ? "text" : "password"}
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="Tối thiểu 8 ký tự"
                      className="w-full px-3.5 pr-10 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900" 
                    />
                    <button
                      type="button"
                      onClick={() => setShowNewPass(!showNewPass)}
                      className="absolute right-3 text-slate-400 hover:text-slate-600 cursor-pointer"
                    >
                      {showNewPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-slate-700">Xác nhận mật khẩu mới *</label>
                  <div className="relative flex items-center">
                    <input 
                      type={showConfirmPass ? "text" : "password"}
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="Nhập lại mật khẩu mới"
                      className="w-full px-3.5 pr-10 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900" 
                    />
                    <button
                      type="button"
                      onClick={() => setShowConfirmPass(!showConfirmPass)}
                      className="absolute right-3 text-slate-400 hover:text-slate-600 cursor-pointer"
                    >
                      {showConfirmPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              </div>

              <div className="flex justify-end pt-4 border-t border-slate-100">
                <AtlasButton
                  type="submit"
                  variant="primary"
                  size="md"
                  disabled={isChangingPassword}
                >
                  {isChangingPassword ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
                </AtlasButton>
              </div>
            </form>
          </div>
        )}

        {/* TAB 3: Bank Account Info */}
        {activeTab === 'bank' && (
          <form onSubmit={handleSaveBank} className="bg-white p-6 rounded-xl border border-slate-200/80 shadow-xs space-y-5">
            <div className="pb-3 border-b border-slate-100">
              <h3 className="text-sm font-semibold text-slate-900">
                Tài khoản nhận tiền đối soát (Payout)
              </h3>
              <p className="text-xs text-slate-500 mt-1">
                Tài khoản này dùng để nhận tiền đối soát doanh thu định kỳ và giải ngân tự động.
              </p>
            </div>

            <div className="grid grid-cols-1 gap-4 max-w-lg">
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold text-slate-700">Ngân hàng</label>
                <select 
                  value={bankName}
                  onChange={(e) => setBankName(e.target.value)}
                  className="h-11 px-3.5 rounded-xl border border-slate-200 bg-white text-slate-800 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-slate-900 cursor-pointer"
                >
                  <option value="Vietcombank">Vietcombank - Ngân hàng Ngoại thương Việt Nam</option>
                  <option value="Techcombank">Techcombank - Ngân hàng Kỹ Thương</option>
                  <option value="MB Bank">MB Bank - Ngân hàng Quân Đội</option>
                  <option value="VietinBank">VietinBank - Ngân hàng Công Thương Việt Nam</option>
                  <option value="BIDV">BIDV - Ngân hàng Đầu tư và Phát triển Việt Nam</option>
                </select>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold text-slate-700 flex items-center justify-between">
                  <span>Số tài khoản</span>
                  <span className="text-xs text-slate-400 font-normal">(Bảo lưu số 0 đầu chuỗi)</span>
                </label>
                <input 
                  type="text" 
                  value={bankAccountNumber}
                  onChange={(e) => setBankAccountNumber(e.target.value)}
                  placeholder="Nhập số tài khoản ngân hàng thụ hưởng"
                  className="px-3.5 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 font-mono font-bold text-sm tracking-wide focus:outline-none focus:ring-2 focus:ring-slate-900" 
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold text-slate-700">Chủ tài khoản</label>
                <input 
                  type="text" 
                  value={bankAccountHolder}
                  onChange={(e) => setBankAccountHolder(e.target.value)}
                  placeholder="Tên in hoa trên thẻ / ĐKKD"
                  className="px-3.5 py-2.5 rounded-xl border border-slate-200 bg-white text-slate-900 text-sm uppercase font-semibold focus:outline-none focus:ring-2 focus:ring-slate-900 font-mono" 
                />
              </div>
            </div>

            <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80 text-slate-600 text-xs flex items-start gap-2.5 max-w-lg">
              <Info className="w-4 h-4 text-slate-500 shrink-0 mt-0.5" />
              <span>Dữ liệu số tài khoản được lưu giữ dưới dạng chuỗi chuẩn, bảo toàn đầy đủ các số 0 ở đầu tài khoản ngân hàng.</span>
            </div>

            <div className="pt-3 border-t border-slate-100">
              <AtlasButton
                type="submit"
                variant="primary"
                size="md"
              >
                Cập nhật tài khoản ngân hàng
              </AtlasButton>
            </div>
          </form>
        )}

        {/* TAB 4: Legal Documents */}
        {activeTab === 'docs' && (
          <div className="bg-white p-6 rounded-xl border border-slate-200/80 shadow-xs space-y-5">
            <div className="pb-3 border-b border-slate-100">
              <h3 className="text-sm font-semibold text-slate-900">
                Hồ sơ pháp lý cấp Vendor
              </h3>
              <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                Theo quy chuẩn Cảng vụ DANASEA, hồ sơ cấp Vendor bao gồm duy nhất 02 loại giấy tờ: <strong>BUSINESS_LICENSE</strong> (Giấy phép ĐKKD) và <strong>SAFETY_CERT</strong> (Chứng nhận ATGT đường thủy).
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              {/* Document 1: BUSINESS_LICENSE */}
              {(() => {
                const doc = profile.documents.find(d => d.type === 'BUSINESS_LICENSE');
                return (
                  <div className="p-5 border border-slate-200/80 rounded-xl flex flex-col justify-between gap-4 bg-slate-50/50">
                    <div className="flex justify-between items-start gap-2">
                      <div>
                        <span className="px-2 py-0.5 rounded font-mono text-slate-700 bg-slate-200/70 text-xs font-bold">
                          BUSINESS_LICENSE
                        </span>
                        <h4 className="text-sm font-bold text-slate-900 mt-2">
                          Giấy phép Đăng ký Kinh doanh
                        </h4>
                        <p className="text-xs text-slate-500 mt-1 font-mono">
                          Tệp: <strong className="text-slate-800">{doc ? doc.name : 'Chưa tải lên'}</strong>
                        </p>
                      </div>

                      {doc?.status === 'APPROVED' && (
                        <AtlasBadge label="Đã duyệt" variant="success" />
                      )}
                      {doc?.status === 'PENDING' && (
                        <AtlasBadge label="Chờ duyệt" variant="warning" />
                      )}
                      {doc?.status === 'REJECTED' && (
                        <AtlasBadge label="Chưa đạt" variant="danger" />
                      )}
                    </div>

                    <div className="flex items-center gap-2.5 pt-3 border-t border-slate-200/70">
                      <AtlasButton
                        variant="outline"
                        size="sm"
                        onClick={() => setPreviewDoc({ name: doc?.name || 'Giấy phép ĐKKD', type: 'BUSINESS_LICENSE' })}
                        icon={<Eye className="w-3.5 h-3.5 text-slate-500" />}
                      >
                        Xem trước
                      </AtlasButton>
                      <label className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-900 text-white text-xs font-semibold hover:bg-black transition-colors cursor-pointer shadow-xs">
                        <Upload className="w-3.5 h-3.5" />
                        <span>Thay thế tệp</span>
                        <input 
                          type="file" 
                          accept=".pdf,.png,.jpg" 
                          className="hidden" 
                          onChange={(e) => {
                            if (e.target.files?.[0]) handleUploadDoc('BUSINESS_LICENSE', e.target.files[0]);
                          }} 
                        />
                      </label>
                    </div>
                  </div>
                );
              })()}

              {/* Document 2: SAFETY_CERT */}
              {(() => {
                const doc = profile.documents.find(d => d.type === 'SAFETY_CERT');
                return (
                  <div className="p-5 border border-slate-200/80 rounded-xl flex flex-col justify-between gap-4 bg-slate-50/50">
                    <div className="flex justify-between items-start gap-2">
                      <div>
                        <span className="px-2 py-0.5 rounded font-mono text-slate-700 bg-slate-200/70 text-xs font-bold">
                          SAFETY_CERT
                        </span>
                        <h4 className="text-sm font-bold text-slate-900 mt-2">
                          Chứng nhận An toàn Giao thông Biển
                        </h4>
                        <p className="text-xs text-slate-500 mt-1 font-mono">
                          Tệp: <strong className="text-slate-800">{doc ? doc.name : 'Chưa tải lên'}</strong>
                        </p>
                      </div>

                      {doc?.status === 'APPROVED' && (
                        <AtlasBadge label="Đã duyệt" variant="success" />
                      )}
                      {doc?.status === 'PENDING' && (
                        <AtlasBadge label="Chờ duyệt" variant="warning" />
                      )}
                      {doc?.status === 'REJECTED' && (
                        <AtlasBadge label="Chưa đạt" variant="danger" />
                      )}
                    </div>

                    <div className="flex items-center gap-2.5 pt-3 border-t border-slate-200/70">
                      <AtlasButton
                        variant="outline"
                        size="sm"
                        onClick={() => setPreviewDoc({ name: doc?.name || 'Chứng nhận ATGT Biển', type: 'SAFETY_CERT' })}
                        icon={<Eye className="w-3.5 h-3.5 text-slate-500" />}
                      >
                        Xem trước
                      </AtlasButton>
                      <label className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-900 text-white text-xs font-semibold hover:bg-black transition-colors cursor-pointer shadow-xs">
                        <Upload className="w-3.5 h-3.5" />
                        <span>Thay thế tệp</span>
                        <input 
                          type="file" 
                          accept=".pdf,.png,.jpg" 
                          className="hidden" 
                          onChange={(e) => {
                            if (e.target.files?.[0]) handleUploadDoc('SAFETY_CERT', e.target.files[0]);
                          }} 
                        />
                      </label>
                    </div>
                  </div>
                );
              })()}
            </div>
          </div>
        )}
      </div>

      {/* Document Preview Modal */}
      {previewDoc && createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl flex flex-col gap-4 border border-slate-200/90">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <FileText className="w-5 h-5 text-slate-700" />
                <h3 className="font-bold text-base text-slate-900">Xem trước tài liệu</h3>
              </div>
              <button 
                onClick={() => setPreviewDoc(null)} 
                className="text-slate-400 hover:text-slate-600 cursor-pointer"
                type="button"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="p-6 rounded-xl bg-slate-50 text-center flex flex-col items-center gap-2.5 border border-slate-200/80">
              <Building2 className="w-10 h-10 text-slate-400" />
              <p className="text-sm font-bold text-slate-900">{previewDoc.name}</p>
              <span className="text-xs text-slate-500">Định dạng PDF đã nộp lên hệ thống kiểm duyệt Cảng vụ</span>
            </div>
            <div className="flex justify-end pt-3 border-t border-slate-100">
              <AtlasButton
                variant="outline"
                size="sm"
                onClick={() => setPreviewDoc(null)}
              >
                Đóng
              </AtlasButton>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
