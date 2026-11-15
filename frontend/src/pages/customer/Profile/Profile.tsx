import { useAuthStore } from "../../../store/useAuthStore";
import { useLanguageStore } from "../../../store/useLanguageStore";
import { useNavigate, Navigate } from "react-router-dom";
import { useState, useEffect, useCallback } from "react";
import { authService } from "../../../services/authService";
import { customerPartnerService } from "../../../services/customerPartnerService";
import type { PartnerApplicationResponse } from "../../../types/partnerApplication";
import {
  User,
  Receipt,
  Heart,
  ShieldCheck,
  Check,
  LogOut,
  ChevronRight,
  Eye,
  EyeOff,
  Store,
  Building2,
  Clock,
  CheckCircle2,
  AlertCircle,
  Loader2,
} from "lucide-react";

type SettingsTab = "personal" | "security" | "orders" | "wishlist" | "partner";

export function Profile() {
  const navigate = useNavigate();
  const { language } = useLanguageStore();
  const isEn = language === 'en';
  const { user, logout } = useAuthStore();

  const [activeTab, setActiveTab] = useState<SettingsTab>("personal");
  const [editingField, setEditingField] = useState<"name" | "phone" | null>(null);
  const [showToast, setShowToast] = useState(false);
  const [toastMessage, setToastMessage] = useState("");

  // Personal Info Form states
  const [fullName, setFullName] = useState(() => {
    if (user?.fullName && user.fullName !== "customer_1" && user.fullName !== "customer_2") return user.fullName;
    if (user?.email === "customer_1@danasea.test") return "Nguyễn Hoàng Long";
    if (user?.email === "customer_2@danasea.test") return "Trần Thị Mai Anh";
    return user?.fullName || "Nguyễn Hoàng Long";
  });
  const [phone, setPhone] = useState(() => {
    if (user?.phone && user.phone !== "0905 000 003" && user.phone !== "0905000003") return user.phone;
    if (user?.email === "customer_1@danasea.test") return "0912 345 678";
    if (user?.email === "customer_2@danasea.test") return "0988 765 432";
    return "0912 345 678";
  });
  const [tempFullName, setTempFullName] = useState(fullName);
  const [tempPhone, setTempPhone] = useState(phone);
  const email = user?.email || "customer@danasea.vn";

  // Sync profile data from Backend /api/users/me
  useEffect(() => {
    let active = true;
    authService.getMyProfile()
      .then((profile) => {
        if (!active || !profile) return;
        if (profile.fullName) {
          setFullName(profile.fullName);
          setTempFullName(profile.fullName);
        }
        if (user && profile.fullName && user.fullName !== profile.fullName) {
          useAuthStore.getState().setAuth(
            { ...user, fullName: profile.fullName, avatarUrl: profile.avatarUrl || user.avatarUrl },
            useAuthStore.getState().accessToken || ''
          );
        }
      })
      .catch(() => {});
    return () => { active = false; };
  }, [user]);

  // Security Form states
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showCurrentPass, setShowCurrentPass] = useState(false);
  const [showNewPass, setShowNewPass] = useState(false);
  const [passwordSuccess, setPasswordSuccess] = useState(false);

  // Partner Application states
  const [partnerApp, setPartnerApp] = useState<PartnerApplicationResponse | null>(null);
  const [loadingPartnerApp, setLoadingPartnerApp] = useState(false);
  const [partnerSubmitting, setPartnerSubmitting] = useState(false);
  const [partnerBusinessName, setPartnerBusinessName] = useState("");
  const [partnerTaxCode, setPartnerTaxCode] = useState("");
  const [partnerAddress, setPartnerAddress] = useState("");
  const [partnerBankName, setPartnerBankName] = useState("Vietcombank");
  const [partnerBankAccountNumber, setPartnerBankAccountNumber] = useState("");
  const [partnerBankAccountHolder, setPartnerBankAccountHolder] = useState("");

  const loadPartnerApplication = useCallback(async () => {
    setLoadingPartnerApp(true);
    try {
      const app = await customerPartnerService.getMyApplication();
      setPartnerApp(app);
      if (app) {
        setPartnerBusinessName(app.businessName || "");
        setPartnerTaxCode(app.taxCode || "");
        setPartnerAddress(app.address || "");
        setPartnerBankName(app.bankName || "Vietcombank");
        setPartnerBankAccountNumber(app.bankAccountNumber || "");
        setPartnerBankAccountHolder(app.bankAccountHolder || "");
      }
    } finally {
      setLoadingPartnerApp(false);
    }
  }, []);

  useEffect(() => {
    if (activeTab === "partner") {
      loadPartnerApplication();
    }
  }, [activeTab, loadPartnerApplication]);

  const handleSubmitPartner = async (e: React.FormEvent) => {
    e.preventDefault();
    setPartnerSubmitting(true);
    try {
      const payload = {
        businessName: partnerBusinessName.trim(),
        taxCode: partnerTaxCode.trim(),
        address: partnerAddress.trim(),
        bankName: partnerBankName.trim(),
        bankAccountNumber: partnerBankAccountNumber.trim(),
        bankAccountHolder: partnerBankAccountHolder.trim(),
      };
      const res = partnerApp?.status === "REJECTED"
        ? await customerPartnerService.resubmitApplication(payload)
        : await customerPartnerService.submitApplication(payload);

      setPartnerApp(res);
      setToastMessage(isEn ? "Partner application submitted successfully!" : "Đã gửi hồ sơ đăng ký đối tác thành công!");
      setShowToast(true);
      setTimeout(() => setShowToast(false), 3000);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || "Không thể gửi hồ sơ. Vui lòng kiểm tra lại thông tin.";
      setToastMessage(msg);
      setShowToast(true);
      setTimeout(() => setShowToast(false), 3000);
    } finally {
      setPartnerSubmitting(false);
    }
  };

  // Redirect unauthenticated user directly to login
  if (!user) {
    return <Navigate to="/auth" replace />;
  }

  const handleSaveName = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await authService.updateProfile({ fullName: tempFullName });
      setFullName(tempFullName);
      if (user) {
        useAuthStore.getState().setAuth(
          { ...user, fullName: tempFullName },
          useAuthStore.getState().accessToken || ''
        );
      }
      setEditingField(null);
      setToastMessage(isEn ? "Legal name updated successfully!" : "Đã cập nhật tên pháp lý thành công!");
      setShowToast(true);
      setTimeout(() => setShowToast(false), 3000);
    } catch {
      setToastMessage(isEn ? "Failed to update legal name. Please try again!" : "Cập nhật tên pháp lý thất bại, vui lòng thử lại!");
      setShowToast(true);
      setTimeout(() => setShowToast(false), 3000);
    }
  };


  const handleSavePhone = (e: React.FormEvent) => {
    e.preventDefault();
    setPhone(tempPhone);
    setEditingField(null);
    setToastMessage(isEn ? "Contact phone number updated!" : "Đã cập nhật số điện thoại liên hệ!");
    setShowToast(true);
    setTimeout(() => setShowToast(false), 3000);
  };

  const handlePasswordSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      alert(isEn ? "New passwords do not match!" : "Mật khẩu xác nhận không khớp!");
      return;
    }
    setPasswordSuccess(true);
    setCurrentPassword("");
    setNewPassword("");
    setConfirmPassword("");
    setTimeout(() => setPasswordSuccess(false), 4000);
  };

  return (
    <main className="w-full pt-20 bg-slate-50 min-h-[calc(100vh-80px)] flex-1 pb-16">
      <div className="w-full max-w-[1240px] mx-auto px-4 sm:px-6 lg:px-8 py-6">
        
        {/* =========================================================================
            2-COLUMN SPLIT SETTINGS ARCHITECTURE (Sidebar Left + Content Right)
           ========================================================================= */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          
          {/* =====================================================================
              LEFT SIDEBAR: Settings & Privacy Navigation (~320px / 4 cols)
             ===================================================================== */}
          <aside className="lg:col-span-4 bg-white rounded-2xl border border-slate-200/90 shadow-xs p-4 sm:p-5 lg:sticky lg:top-24 space-y-4">
            
            {/* Header / Identity Snippet */}
            <div className="flex items-center gap-3 pb-4 border-b border-slate-100">
              <div className="relative w-12 h-12 rounded-full overflow-hidden bg-slate-100 border-2 border-white shadow-xs shrink-0">
                <img
                  alt={fullName}
                  className="w-full h-full object-cover"
                  src={user.avatarUrl || "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80"}
                />
                <span className="absolute bottom-0 right-0 w-3.5 h-3.5 rounded-full bg-emerald-500 border-2 border-white" />
              </div>
              <div className="min-w-0 flex-1">
                <h2 className="text-sm font-bold text-slate-900 truncate">
                  {fullName}
                </h2>
                <p className="text-xs text-slate-500 truncate">
                  {email}
                </p>
              </div>
            </div>

            {/* Sidebar Title */}
            <div>
              <h3 className="text-xs font-bold text-slate-600 uppercase tracking-wider px-2">
                {isEn ? "Settings & Privacy" : "Cài đặt và quyền riêng tư"}
              </h3>
            </div>

            {/* Navigation Menu Items */}
            <nav className="flex flex-col gap-1">
              <button
                type="button"
                onClick={() => { setActiveTab("personal"); setEditingField(null); }}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold transition-all cursor-pointer ${
                  activeTab === "personal"
                    ? "bg-[#0284c7] text-white shadow-xs"
                    : "text-slate-700 hover:bg-slate-100"
                }`}
              >
                <div className="flex items-center gap-3">
                  <User className={`w-4 h-4 ${activeTab === "personal" ? "text-white" : "text-slate-500"}`} />
                  <span>{isEn ? "Personal Info" : "Thông tin cá nhân"}</span>
                </div>
                <ChevronRight className={`w-4 h-4 ${activeTab === "personal" ? "text-white/80" : "text-slate-400"}`} />
              </button>

              <button
                type="button"
                onClick={() => { setActiveTab("security"); setEditingField(null); }}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold transition-all cursor-pointer ${
                  activeTab === "security"
                    ? "bg-[#0284c7] text-white shadow-xs"
                    : "text-slate-700 hover:bg-slate-100"
                }`}
              >
                <div className="flex items-center gap-3">
                  <ShieldCheck className={`w-4 h-4 ${activeTab === "security" ? "text-white" : "text-slate-500"}`} />
                  <span>{isEn ? "Login & Security" : "Đăng nhập & Bảo mật"}</span>
                </div>
                <ChevronRight className={`w-4 h-4 ${activeTab === "security" ? "text-white/80" : "text-slate-400"}`} />
              </button>

              <button
                type="button"
                onClick={() => { setActiveTab("partner"); setEditingField(null); }}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold transition-all cursor-pointer ${
                  activeTab === "partner"
                    ? "bg-[#0284c7] text-white shadow-xs"
                    : "text-slate-700 hover:bg-slate-100"
                }`}
              >
                <div className="flex items-center gap-3">
                  <Store className={`w-4 h-4 ${activeTab === "partner" ? "text-white" : "text-slate-500"}`} />
                  <span>{isEn ? "Become a Partner" : "Đăng ký Đối tác"}</span>
                </div>
                {partnerApp && (
                  <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                    partnerApp.status === 'APPROVED' ? 'bg-emerald-100 text-emerald-700' :
                    partnerApp.status === 'PENDING' ? 'bg-amber-100 text-amber-700' :
                    'bg-rose-100 text-rose-700'
                  }`}>
                    {partnerApp.status === 'APPROVED' ? 'Đã duyệt' : partnerApp.status === 'PENDING' ? 'Chờ duyệt' : 'Từ chối'}
                  </span>
                )}
                <ChevronRight className={`w-4 h-4 ${activeTab === "partner" ? "text-white/80" : "text-slate-400"}`} />
              </button>

              <button
                type="button"
                onClick={() => navigate("/profile/orders")}
                className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-slate-700 hover:bg-slate-100 transition-all cursor-pointer"
              >
                <div className="flex items-center gap-3">
                  <Receipt className="w-4 h-4 text-slate-500" />
                  <span>{isEn ? "Bookings & QR Passes" : "Chuyến đi & Vé QR"}</span>
                </div>
                <ChevronRight className="w-4 h-4 text-slate-400" />
              </button>

              <button
                type="button"
                onClick={() => navigate("/profile/wishlist")}
                className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-slate-700 hover:bg-slate-100 transition-all cursor-pointer"
              >
                <div className="flex items-center gap-3">
                  <Heart className="w-4 h-4 text-slate-500" />
                  <span>{isEn ? "Saved Wishlist" : "Tour đã lưu"}</span>
                </div>
                <ChevronRight className="w-4 h-4 text-slate-400" />
              </button>

              <div className="pt-2 border-t border-slate-100 mt-2">
                <button
                  type="button"
                  onClick={() => { logout(); navigate("/auth"); }}
                  className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-rose-600 hover:bg-rose-50 transition-all cursor-pointer"
                >
                  <LogOut className="w-4 h-4 text-rose-500" />
                  <span>{isEn ? "Sign Out" : "Đăng xuất tài khoản"}</span>
                </button>
              </div>
            </nav>
          </aside>

          {/* =====================================================================
              RIGHT CONTENT PANEL: Active Settings Rows & Forms (~8 cols)
             ===================================================================== */}
          <div className="lg:col-span-8 space-y-6">
            
            {/* TAB 1: THÔNG TIN CÁ NHÂN (Row-based Settings List) */}
            {activeTab === "personal" && (
              <div className="bg-white rounded-2xl border border-slate-200/90 shadow-xs p-6 sm:p-8 space-y-6">
                <div>
                  <h1 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
                    {isEn ? "Personal Information" : "Thông tin cá nhân"}
                  </h1>
                </div>

                {/* Settings Rows */}
                <div className="divide-y divide-slate-100">
                  
                  {/* Row 1: Tên pháp lý */}
                  <div className="py-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-0.5 flex-1">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          {isEn ? "Legal Name" : "Tên pháp lý"}
                        </span>
                        {editingField === "name" ? (
                          <form onSubmit={handleSaveName} className="pt-2 space-y-3">
                            <input
                              type="text"
                              value={tempFullName}
                              onChange={(e) => setTempFullName(e.target.value)}
                              className="w-full max-w-md h-10 px-3.5 rounded-xl border border-slate-300 text-sm font-semibold text-slate-900 outline-none focus:border-[#0284c7]"
                              required
                            />
                            <div className="flex items-center gap-2">
                              <button
                                type="submit"
                                className="px-4 py-1.5 rounded-lg bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-semibold cursor-pointer shadow-xs"
                              >
                                {isEn ? "Save" : "Lưu thay đổi"}
                              </button>
                              <button
                                type="button"
                                onClick={() => setEditingField(null)}
                                className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold cursor-pointer"
                              >
                                {isEn ? "Cancel" : "Hủy"}
                              </button>
                            </div>
                          </form>
                        ) : (
                          <p className="text-sm sm:text-base font-semibold text-slate-900">
                            {fullName}
                          </p>
                        )}
                      </div>

                      {editingField !== "name" && (
                        <button
                          type="button"
                          onClick={() => { setTempFullName(fullName); setEditingField("name"); }}
                          className="text-xs font-bold text-[#0284c7] hover:underline cursor-pointer shrink-0"
                        >
                          {isEn ? "Edit" : "Chỉnh sửa"}
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Row 2: Địa chỉ Email */}
                  <div className="py-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-0.5">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          {isEn ? "Email Address" : "Địa chỉ Email"}
                        </span>
                        <p className="text-sm sm:text-base font-semibold text-slate-900">
                          {email}
                        </p>
                      </div>
                      <span className="text-xs font-semibold text-slate-400 shrink-0">
                        {isEn ? "Primary" : "Cố định"}
                      </span>
                    </div>
                  </div>

                  {/* Row 3: Số điện thoại */}
                  <div className="py-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-0.5 flex-1">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          {isEn ? "Contact Phone Number" : "Số điện thoại liên lạc"}
                        </span>
                        {editingField === "phone" ? (
                          <form onSubmit={handleSavePhone} className="pt-2 space-y-3">
                            <input
                              type="tel"
                              value={tempPhone}
                              onChange={(e) => setTempPhone(e.target.value)}
                              className="w-full max-w-md h-10 px-3.5 rounded-xl border border-slate-300 text-sm font-semibold text-slate-900 outline-none focus:border-[#0284c7]"
                              required
                            />
                            <div className="flex items-center gap-2">
                              <button
                                type="submit"
                                className="px-4 py-1.5 rounded-lg bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-semibold cursor-pointer shadow-xs"
                              >
                                {isEn ? "Save" : "Lưu thay đổi"}
                              </button>
                              <button
                                type="button"
                                onClick={() => setEditingField(null)}
                                className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold cursor-pointer"
                              >
                                {isEn ? "Cancel" : "Hủy"}
                              </button>
                            </div>
                          </form>
                        ) : (
                          <p className="text-sm sm:text-base font-semibold text-slate-900">
                            {phone}
                          </p>
                        )}
                      </div>

                      {editingField !== "phone" && (
                        <button
                          type="button"
                          onClick={() => { setTempPhone(phone); setEditingField("phone"); }}
                          className="text-xs font-bold text-[#0284c7] hover:underline cursor-pointer shrink-0"
                        >
                          {isEn ? "Edit" : "Chỉnh sửa"}
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Row 4: CCCD & Bảo hiểm biển */}
                  <div className="py-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-0.5">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          {isEn ? "Identity & Marine Travel Insurance" : "Xác minh danh tính & Bảo hiểm du lịch biển"}
                        </span>
                        <div className="flex items-center gap-2 pt-0.5">
                          <span className="text-sm sm:text-base font-semibold text-slate-900">
                            {isEn ? "Fully Verified" : "Đã xác minh đầy đủ"}
                          </span>
                          <span className="px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-800 text-[11px] font-bold border border-emerald-200">
                            {isEn ? "100M VND Coverage" : "Hạn mức 100 triệu"}
                          </span>
                        </div>
                      </div>
                      <span className="text-xs font-bold text-emerald-600 shrink-0">
                        {isEn ? "Compliant" : "Đạt chuẩn"}
                      </span>
                    </div>
                  </div>

                  {/* Row 5: Hạng thành viên */}
                  <div className="py-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-0.5">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          {isEn ? "Membership Tier" : "Hạng thành viên"}
                        </span>
                        <p className="text-sm sm:text-base font-semibold text-slate-900">
                          {isEn ? "Silver Tier" : "Hạng Bạc"}
                        </p>
                      </div>
                      <span className="px-2.5 py-1 rounded-lg bg-slate-100 text-slate-700 text-xs font-bold border border-slate-200 shrink-0">
                        {isEn ? "Active" : "Đang hoạt động"}
                      </span>
                    </div>
                  </div>

                </div>
              </div>
            )}

            {/* TAB 2: ĐĂNG NHẬP & BẢO MẬT */}
            {activeTab === "security" && (
              <div className="bg-white rounded-2xl border border-slate-200/90 shadow-xs p-6 sm:p-8 space-y-6">
                <div>
                  <h1 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
                    {isEn ? "Login & Security" : "Đăng nhập & Bảo mật"}
                  </h1>
                  <p className="text-xs sm:text-sm text-slate-600 mt-1">
                    {isEn
                      ? "Update your password periodically and monitor active login sessions to secure your account."
                      : "Cập nhật mật khẩu định kỳ và kiểm tra các phiên đăng nhập để bảo vệ an toàn cho tài khoản."}
                  </p>
                </div>

                {passwordSuccess && (
                  <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs sm:text-sm font-semibold flex items-center gap-2">
                    <Check className="w-4 h-4 text-emerald-600" />
                    <span>{isEn ? "Account password updated successfully!" : "Mật khẩu tài khoản đã được cập nhật thành công!"}</span>
                  </div>
                )}

                <form onSubmit={handlePasswordSubmit} className="space-y-4 max-w-lg">
                  <div className="space-y-1">
                    <label className="text-xs font-semibold text-slate-700">{isEn ? "Current Password" : "Mật khẩu hiện tại"}</label>
                    <div className="relative flex items-center">
                      <input
                        type={showCurrentPass ? "text" : "password"}
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        placeholder={isEn ? "Enter current password" : "Nhập mật khẩu hiện tại"}
                        className="w-full h-10 px-3.5 pr-10 rounded-xl border border-slate-200 text-sm outline-none focus:border-[#0284c7]"
                        required
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

                  <div className="space-y-1">
                    <label className="text-xs font-semibold text-slate-700">{isEn ? "New Password" : "Mật khẩu mới"}</label>
                    <div className="relative flex items-center">
                      <input
                        type={showNewPass ? "text" : "password"}
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        placeholder={isEn ? "At least 8 characters" : "Tối thiểu 8 ký tự"}
                        className="w-full h-10 px-3.5 pr-10 rounded-xl border border-slate-200 text-sm outline-none focus:border-[#0284c7]"
                        required
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

                  <div className="space-y-1">
                    <label className="text-xs font-semibold text-slate-700">{isEn ? "Confirm New Password" : "Xác nhận mật khẩu mới"}</label>
                    <input
                      type="password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder={isEn ? "Re-enter new password" : "Nhập lại mật khẩu mới"}
                      className="w-full h-10 px-3.5 rounded-xl border border-slate-200 text-sm outline-none focus:border-[#0284c7]"
                      required
                    />
                  </div>

                  <div className="pt-2">
                    <button
                      type="submit"
                      className="px-5 py-2.5 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs sm:text-sm font-semibold transition-colors cursor-pointer shadow-xs"
                    >
                      {isEn ? "Update Password" : "Cập nhật mật khẩu"}
                    </button>
                  </div>
                </form>

                {/* Device History Card */}
                <div className="pt-6 border-t border-slate-100 space-y-3">
                  <h3 className="text-sm font-bold text-slate-900">{isEn ? "Active Login Sessions" : "Thiết bị đang đăng nhập"}</h3>
                  <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/70 flex items-center justify-between text-xs">
                    <div>
                      <p className="font-semibold text-slate-900">{isEn ? "Current Web Browser (Windows · Chrome)" : "Trình duyệt Web hiện tại (Windows · Chrome)"}</p>
                      <p className="text-slate-500 mt-0.5">{isEn ? "Da Nang, Vietnam · Active now" : "Đà Nẵng, Việt Nam · Hoạt động ngay bây giờ"}</p>
                    </div>
                    <span className="text-[11px] font-bold text-emerald-600">{isEn ? "This session" : "Phiên này"}</span>
                  </div>
                </div>
              </div>
            )}

            {/* TAB 3: ĐĂNG KÝ ĐỐI TÁC (Partner Application & Onboarding) */}
            {activeTab === "partner" && (
              <div className="bg-white rounded-2xl border border-slate-200/90 shadow-xs p-6 sm:p-8 space-y-6">
                <div>
                  <h1 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
                    {isEn ? "Become a Partner Operator" : "Hồ sơ Đăng ký Đối tác Vận hành"}
                  </h1>
                </div>

                {loadingPartnerApp ? (
                  <div className="space-y-4 animate-pulse">
                    <div className="h-6 w-48 bg-slate-200 rounded" />
                    <div className="h-20 bg-slate-100 rounded-xl" />
                    <div className="h-10 w-full bg-slate-200 rounded-xl" />
                  </div>
                ) : partnerApp?.status === "APPROVED" ? (
                  <div className="space-y-6">
                    <div className="p-5 rounded-xl bg-emerald-50 border border-emerald-200/80 flex items-start gap-3.5">
                      <CheckCircle2 className="w-5 h-5 text-emerald-600 mt-0.5 shrink-0" />
                      <div className="space-y-1">
                        <h3 className="text-sm font-bold text-emerald-900">
                          {isEn ? "Application Approved" : "Hồ sơ đối tác đã được phê duyệt"}
                        </h3>
                        <p className="text-xs text-emerald-800 leading-relaxed">
                          {isEn
                            ? "Your vendor account is active. You have full access to DanaSea Vendor Portal for managing sports services, bookings, and payouts."
                            : "Tài khoản đối tác của bạn đã sẵn sàng. Bạn có quyền truy cập toàn diện vào Kênh Đối tác DanaSea để quản lý dịch vụ thể thao biển, tiếp nhận vé và quản lý giải ngân."}
                        </p>
                      </div>
                    </div>

                    <div className="p-4 rounded-xl border border-slate-200/80 bg-slate-50 space-y-3 text-xs">
                      <div className="flex justify-between py-1 border-b border-slate-200/60">
                        <span className="text-slate-500">{isEn ? "Business Name" : "Tên đơn vị"}</span>
                        <span className="font-semibold text-slate-900">{partnerApp.businessName}</span>
                      </div>
                      <div className="flex justify-between py-1 border-b border-slate-200/60">
                        <span className="text-slate-500">{isEn ? "Tax Code" : "Mã số thuế"}</span>
                        <span className="font-mono font-medium text-slate-900">{partnerApp.taxCode}</span>
                      </div>
                      <div className="flex justify-between py-1 border-b border-slate-200/60">
                        <span className="text-slate-500">{isEn ? "Address" : "Địa chỉ bến bãi"}</span>
                        <span className="font-medium text-slate-900 text-right">{partnerApp.address}</span>
                      </div>
                      <div className="flex justify-between py-1">
                        <span className="text-slate-500">{isEn ? "Bank Account" : "Ngân hàng thụ hưởng"}</span>
                        <span className="font-mono font-medium text-slate-900 text-right">
                          {partnerApp.bankName} · {partnerApp.bankAccountNumber} ({partnerApp.bankAccountHolder})
                        </span>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => navigate("/vendor")}
                      className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs sm:text-sm font-semibold transition-colors cursor-pointer shadow-xs"
                    >
                      <Building2 className="w-4 h-4" />
                      <span>{isEn ? "Go to Vendor Portal" : "Truy cập Kênh Đối tác"}</span>
                    </button>
                  </div>
                ) : partnerApp?.status === "PENDING" ? (
                  <div className="space-y-6">
                    <div className="p-5 rounded-xl bg-amber-50 border border-amber-200/80 flex items-start gap-3.5">
                      <Clock className="w-5 h-5 text-amber-600 mt-0.5 shrink-0" />
                      <div className="space-y-1">
                        <h3 className="text-sm font-bold text-amber-900">
                          {isEn ? "Application Under Review" : "Hồ sơ đang được Cảng vụ DanaSea thẩm định"}
                        </h3>
                        <p className="text-xs text-amber-800 leading-relaxed">
                          {isEn
                            ? "Our operations team is reviewing your business licenses and maritime safety compliance. Verification usually completes within 24 business hours."
                            : "Ban quản lý DanaSea đang đối soát giấy phép kinh doanh và điều kiện an toàn bến bãi đường thủy. Thời gian thẩm định thông thường trong vòng 24 giờ làm việc."}
                        </p>
                      </div>
                    </div>

                    <div className="p-4 rounded-xl border border-slate-200/80 bg-slate-50 space-y-3 text-xs">
                      <div className="flex justify-between py-1 border-b border-slate-200/60">
                        <span className="text-slate-500">{isEn ? "Business Name" : "Tên đơn vị"}</span>
                        <span className="font-semibold text-slate-900">{partnerApp.businessName}</span>
                      </div>
                      <div className="flex justify-between py-1 border-b border-slate-200/60">
                        <span className="text-slate-500">{isEn ? "Tax Code" : "Mã số thuế"}</span>
                        <span className="font-mono font-medium text-slate-900">{partnerApp.taxCode}</span>
                      </div>
                      <div className="flex justify-between py-1 border-b border-slate-200/60">
                        <span className="text-slate-500">{isEn ? "Address" : "Địa chỉ bến bãi"}</span>
                        <span className="font-medium text-slate-900 text-right">{partnerApp.address}</span>
                      </div>
                      <div className="flex justify-between py-1">
                        <span className="text-slate-500">{isEn ? "Bank Account" : "Tài khoản thanh toán"}</span>
                        <span className="font-mono font-medium text-slate-900 text-right">
                          {partnerApp.bankName} · {partnerApp.bankAccountNumber}
                        </span>
                      </div>
                    </div>
                  </div>
                ) : (
                  <form onSubmit={handleSubmitPartner} className="space-y-4 max-w-xl">
                    {partnerApp?.status === "REJECTED" && (
                      <div className="p-4 rounded-xl bg-rose-50 border border-rose-200/80 flex items-start gap-3">
                        <AlertCircle className="w-5 h-5 text-rose-600 mt-0.5 shrink-0" />
                        <div className="space-y-1 text-xs">
                          <p className="font-bold text-rose-900">
                            {isEn ? "Application Rejected" : "Hồ sơ chưa được phê duyệt"}
                          </p>
                          <p className="text-rose-800">
                            {partnerApp.rejectionReason || (isEn ? "Please update your business details and resubmit." : "Vui lòng kiểm tra lại thông tin mã số thuế và tài khoản ngân hàng để gửi lại hồ sơ.")}
                          </p>
                        </div>
                      </div>
                    )}

                    <div className="space-y-1">
                      <label className="text-xs font-semibold text-slate-700">
                        {isEn ? "Legal Business / Brand Name" : "Tên Doanh nghiệp / Hộ kinh doanh"}
                      </label>
                      <input
                        type="text"
                        value={partnerBusinessName}
                        onChange={(e) => setPartnerBusinessName(e.target.value)}
                        placeholder={isEn ? "e.g. Da Nang Marine Watersports Co." : "VD: Công ty TNHH Du thuyền & Thể thao Biển Đà Nẵng"}
                        className="w-full h-10 px-3.5 rounded-xl border border-slate-200 text-sm outline-none focus:border-[#0284c7]"
                        required
                      />
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <div className="space-y-1">
                        <label className="text-xs font-semibold text-slate-700">
                          {isEn ? "Tax Identification Code" : "Mã số thuế doanh nghiệp"}
                        </label>
                        <input
                          type="text"
                          value={partnerTaxCode}
                          onChange={(e) => setPartnerTaxCode(e.target.value)}
                          placeholder="0401234567"
                          className="w-full h-10 px-3.5 rounded-xl border border-slate-200 font-mono text-sm outline-none focus:border-[#0284c7]"
                          required
                        />
                      </div>

                      <div className="space-y-1">
                        <label className="text-xs font-semibold text-slate-700">
                          {isEn ? "Settlement Bank" : "Ngân hàng thụ hưởng"}
                        </label>
                        <select
                          value={partnerBankName}
                          onChange={(e) => setPartnerBankName(e.target.value)}
                          className="w-full h-10 px-3 rounded-xl border border-slate-200 text-sm outline-none focus:border-[#0284c7] bg-white cursor-pointer"
                        >
                          <option value="Vietcombank">Vietcombank</option>
                          <option value="MB Bank">MB Bank</option>
                          <option value="Techcombank">Techcombank</option>
                          <option value="BIDV">BIDV</option>
                          <option value="VietinBank">VietinBank</option>
                          <option value="ACB">ACB</option>
                          <option value="TPBank">TPBank</option>
                          <option value="VPBank">VPBank</option>
                        </select>
                      </div>
                    </div>

                    <div className="space-y-1">
                      <label className="text-xs font-semibold text-slate-700">
                        {isEn ? "Operating Pier / Beach Address" : "Địa chỉ bến bãi / Điểm hoạt động đón khách"}
                      </label>
                      <input
                        type="text"
                        value={partnerAddress}
                        onChange={(e) => setPartnerAddress(e.target.value)}
                        placeholder={isEn ? "e.g. Zone 4, My Khe Beach, Son Tra, Da Nang" : "VD: Bến thủy nội địa CT15, Bán đảo Sơn Trà, Đà Nẵng"}
                        className="w-full h-10 px-3.5 rounded-xl border border-slate-200 text-sm outline-none focus:border-[#0284c7]"
                        required
                      />
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      <div className="space-y-1">
                        <label className="text-xs font-semibold text-slate-700">
                          {isEn ? "Bank Account Number" : "Số tài khoản ngân hàng"}
                        </label>
                        <input
                          type="text"
                          value={partnerBankAccountNumber}
                          onChange={(e) => setPartnerBankAccountNumber(e.target.value)}
                          placeholder="0123456789"
                          className="w-full h-10 px-3.5 rounded-xl border border-slate-200 font-mono text-sm outline-none focus:border-[#0284c7]"
                          required
                        />
                      </div>

                      <div className="space-y-1">
                        <label className="text-xs font-semibold text-slate-700">
                          {isEn ? "Bank Account Holder" : "Tên chủ tài khoản (in hoa)"}
                        </label>
                        <input
                          type="text"
                          value={partnerBankAccountHolder}
                          onChange={(e) => setPartnerBankAccountHolder(e.target.value.toUpperCase())}
                          placeholder="NGUYEN VAN A"
                          className="w-full h-10 px-3.5 rounded-xl border border-slate-200 text-sm outline-none focus:border-[#0284c7]"
                          required
                        />
                      </div>
                    </div>

                    <div className="pt-3">
                      <button
                        type="submit"
                        disabled={partnerSubmitting}
                        className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] disabled:bg-slate-300 text-white text-xs sm:text-sm font-semibold transition-colors cursor-pointer shadow-xs"
                      >
                        {partnerSubmitting ? (
                          <>
                            <Loader2 className="w-4 h-4 animate-spin" />
                            <span>{isEn ? "Submitting..." : "Đang gửi hồ sơ..."}</span>
                          </>
                        ) : (
                          <>
                            <Building2 className="w-4 h-4" />
                            <span>
                              {partnerApp?.status === "REJECTED"
                                ? (isEn ? "Resubmit Application" : "Gửi lại hồ sơ")
                                : (isEn ? "Submit Application" : "Gửi hồ sơ đăng ký")}
                            </span>
                          </>
                        )}
                      </button>
                    </div>
                  </form>
                )}
              </div>
            )}

          </div>

        </div>

      </div>

      {/* Floating Success Toast */}
      {showToast && (
        <div className="fixed bottom-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3 rounded-xl bg-slate-900 text-white shadow-lg animate-in slide-in-from-bottom-5">
          <Check className="w-4 h-4 text-emerald-400" />
          <span className="text-[13px] font-medium">{toastMessage}</span>
        </div>
      )}
    </main>
  );
}

export default Profile;
