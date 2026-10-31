import { useAuthStore } from "../../../store/useAuthStore";
import { useNavigate } from "react-router-dom";
import { useState } from "react";
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
} from "lucide-react";

type SettingsTab = "personal" | "security" | "orders" | "wishlist";

export function Profile() {
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();

  const [activeTab, setActiveTab] = useState<SettingsTab>("personal");
  const [editingField, setEditingField] = useState<"name" | "phone" | null>(null);
  const [showToast, setShowToast] = useState(false);
  const [toastMessage, setToastMessage] = useState("");

  // Personal Info Form states
  const [fullName, setFullName] = useState(user?.fullName || "Nguyễn Văn Khách");
  const [phone, setPhone] = useState(user?.phone || "0905 000 003");
  const [tempFullName, setTempFullName] = useState(fullName);
  const [tempPhone, setTempPhone] = useState(phone);
  const email = user?.email || "customer@danasea.vn";

  // Security Form states
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showCurrentPass, setShowCurrentPass] = useState(false);
  const [showNewPass, setShowNewPass] = useState(false);
  const [passwordSuccess, setPasswordSuccess] = useState(false);

  if (!user) {
    return (
      <main className="w-full pt-28 pb-20 bg-slate-50 flex-1 flex flex-col items-center justify-center min-h-[calc(100vh-80px)]">
        <div className="max-w-md w-full mx-auto p-8 rounded-2xl bg-white border border-slate-200/80 shadow-xs text-center flex flex-col items-center">
          <div className="w-12 h-12 rounded-xl bg-slate-50 border border-slate-200 text-slate-500 flex items-center justify-center mb-4">
            <User className="w-6 h-6" />
          </div>
          <h1 className="text-xl sm:text-[22px] font-bold text-slate-900 mb-2">Vui lòng đăng nhập</h1>
          <p className="text-[13px] text-slate-600 mb-6 leading-relaxed">
            Đăng nhập vào tài khoản DANASEA để quản lý thông tin cá nhân và cài đặt bảo mật.
          </p>
          <button 
            onClick={() => navigate('/auth')} 
            className="w-full py-3 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white font-semibold text-sm transition-colors cursor-pointer shadow-xs"
          >
            Đến trang Đăng nhập
          </button>
        </div>
      </main>
    );
  }

  const handleSaveName = (e: React.FormEvent) => {
    e.preventDefault();
    setFullName(tempFullName);
    setEditingField(null);
    setToastMessage("Đã cập nhật tên pháp lý thành công!");
    setShowToast(true);
    setTimeout(() => setShowToast(false), 3000);
  };

  const handleSavePhone = (e: React.FormEvent) => {
    e.preventDefault();
    setPhone(tempPhone);
    setEditingField(null);
    setToastMessage("Đã cập nhật số điện thoại liên hệ!");
    setShowToast(true);
    setTimeout(() => setShowToast(false), 3000);
  };

  const handlePasswordSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      alert("Mật khẩu xác nhận không khớp!");
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
                Cài đặt và quyền riêng tư
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
                  <span>Thông tin cá nhân</span>
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
                  <span>Đăng nhập &amp; Bảo mật</span>
                </div>
                <ChevronRight className={`w-4 h-4 ${activeTab === "security" ? "text-white/80" : "text-slate-400"}`} />
              </button>

              <button
                type="button"
                onClick={() => navigate("/profile/orders")}
                className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold text-slate-700 hover:bg-slate-100 transition-all cursor-pointer"
              >
                <div className="flex items-center gap-3">
                  <Receipt className="w-4 h-4 text-slate-500" />
                  <span>Chuyến đi &amp; Vé QR</span>
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
                  <span>Tour đã lưu</span>
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
                  <span>Đăng xuất tài khoản</span>
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
                    Thông tin cá nhân
                  </h1>
                </div>

                {/* Settings Rows */}
                <div className="divide-y divide-slate-100">
                  
                  {/* Row 1: Tên pháp lý */}
                  <div className="py-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-0.5 flex-1">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          Tên pháp lý
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
                                Lưu thay đổi
                              </button>
                              <button
                                type="button"
                                onClick={() => setEditingField(null)}
                                className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold cursor-pointer"
                              >
                                Hủy
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
                          Chỉnh sửa
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Row 2: Địa chỉ Email */}
                  <div className="py-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-0.5">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          Địa chỉ Email
                        </span>
                        <p className="text-sm sm:text-base font-semibold text-slate-900">
                          {email}
                        </p>
                      </div>
                      <span className="text-xs font-semibold text-slate-400 shrink-0">
                        Cố định
                      </span>
                    </div>
                  </div>

                  {/* Row 3: Số điện thoại */}
                  <div className="py-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-0.5 flex-1">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          Số điện thoại liên lạc
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
                                Lưu thay đổi
                              </button>
                              <button
                                type="button"
                                onClick={() => setEditingField(null)}
                                className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold cursor-pointer"
                              >
                                Hủy
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
                          Chỉnh sửa
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Row 4: CCCD & Bảo hiểm biển */}
                  <div className="py-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-0.5">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          Xác minh danh tính &amp; Bảo hiểm du lịch biển
                        </span>
                        <div className="flex items-center gap-2 pt-0.5">
                          <span className="text-sm sm:text-base font-semibold text-slate-900">
                            Đã xác minh đầy đủ
                          </span>
                          <span className="px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-800 text-[11px] font-bold border border-emerald-200">
                            Hạn mức 100 triệu
                          </span>
                        </div>
                      </div>
                      <span className="text-xs font-bold text-emerald-600 shrink-0">
                        Đạt chuẩn
                      </span>
                    </div>
                  </div>

                  {/* Row 5: Hạng thành viên */}
                  <div className="py-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-0.5">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                          Hạng thành viên
                        </span>
                        <p className="text-sm sm:text-base font-semibold text-slate-900">
                          Hạng Bạc
                        </p>
                      </div>
                      <span className="px-2.5 py-1 rounded-lg bg-slate-100 text-slate-700 text-xs font-bold border border-slate-200 shrink-0">
                        Đang hoạt động
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
                    Đăng nhập &amp; Bảo mật
                  </h1>
                  <p className="text-xs sm:text-sm text-slate-600 mt-1">
                    Cập nhật mật khẩu định kỳ và kiểm tra các phiên đăng nhập để bảo vệ an toàn cho tài khoản.
                  </p>
                </div>

                {passwordSuccess && (
                  <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs sm:text-sm font-semibold flex items-center gap-2">
                    <Check className="w-4 h-4 text-emerald-600" />
                    <span>Mật khẩu tài khoản đã được cập nhật thành công!</span>
                  </div>
                )}

                <form onSubmit={handlePasswordSubmit} className="space-y-4 max-w-lg">
                  <div className="space-y-1">
                    <label className="text-xs font-semibold text-slate-700">Mật khẩu hiện tại</label>
                    <div className="relative flex items-center">
                      <input
                        type={showCurrentPass ? "text" : "password"}
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        placeholder="Nhập mật khẩu hiện tại"
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
                    <label className="text-xs font-semibold text-slate-700">Mật khẩu mới</label>
                    <div className="relative flex items-center">
                      <input
                        type={showNewPass ? "text" : "password"}
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        placeholder="Tối thiểu 8 ký tự"
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
                    <label className="text-xs font-semibold text-slate-700">Xác nhận mật khẩu mới</label>
                    <input
                      type="password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="Nhập lại mật khẩu mới"
                      className="w-full h-10 px-3.5 rounded-xl border border-slate-200 text-sm outline-none focus:border-[#0284c7]"
                      required
                    />
                  </div>

                  <div className="pt-2">
                    <button
                      type="submit"
                      className="px-5 py-2.5 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs sm:text-sm font-semibold transition-colors cursor-pointer shadow-xs"
                    >
                      Cập nhật mật khẩu
                    </button>
                  </div>
                </form>

                {/* Device History Card */}
                <div className="pt-6 border-t border-slate-100 space-y-3">
                  <h3 className="text-sm font-bold text-slate-900">Thiết bị đang đăng nhập</h3>
                  <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/70 flex items-center justify-between text-xs">
                    <div>
                      <p className="font-semibold text-slate-900">Trình duyệt Web hiện tại (Windows · Chrome)</p>
                      <p className="text-slate-500 mt-0.5">Đà Nẵng, Việt Nam · Hoạt động ngay bây giờ</p>
                    </div>
                    <span className="text-[11px] font-bold text-emerald-600">Phiên này</span>
                  </div>
                </div>
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
