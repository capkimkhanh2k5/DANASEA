import { useAuthStore } from "../../../store/useAuthStore";

import { useNavigate } from "react-router-dom";
import { useState } from "react";

export function Profile() {
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();

  const [showToast, setShowToast] = useState(false);
  
  if (!user) {
    return (
      <main className="w-full pt-32 pb-20 bg-surface flex-1 flex flex-col items-center justify-center">
        <h2 className="font-headline-md text-on-surface">Vui lòng đăng nhập</h2>
        <button onClick={() => navigate('/auth')} className="mt-4 px-6 py-2 bg-primary text-on-primary rounded-full">Đến trang Đăng nhập</button>
      </main>
    );
  }

  const handleSaveProfile = (e: React.FormEvent) => {
    e.preventDefault();
    setShowToast(true);
    setTimeout(() => {
      setShowToast(false);
    }, 3000);
  };

  return (
    <main className="w-full pt-20 bg-surface flex-1">
      <div className="flex flex-col w-full">
        <div className="relative w-full overflow-hidden bg-surface py-8">
          <div className="w-full max-w-[1440px] mx-auto px-4 sm:px-8 lg:px-12">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
              <div>
                <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight">Hồ sơ cá nhân</h1>
              </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start w-full">
              {/* Left Sidebar */}
              <aside className="lg:col-span-4 flex flex-col gap-6 w-full">
                <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100">
                  <div className="flex items-center gap-4 pb-6 border-b border-slate-100">
                    <div className="relative w-16 h-16 rounded-full overflow-hidden flex-shrink-0 bg-slate-100 shadow-inner">
                      <img
                        alt={user.fullName || "Tên người dùng"}
                        className="w-full h-full object-cover"
                        src={user.avatarUrl || "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80"}
                      />
                      <span className="absolute bottom-0 right-0 w-4 h-4 rounded-full bg-emerald-500 flex items-center justify-center text-white text-[10px] shadow-sm">
                        <span className="material-symbols-outlined text-[12px]">check</span>
                      </span>
                    </div>
                    <div className="min-w-0 flex-1">
                      <h2 className="font-bold text-base text-slate-900 truncate">{user.fullName || "Khách hàng"}</h2>
                      <p className="text-xs text-slate-500 truncate">{user.email || "Chưa cập nhật email"}</p>
                      <span className="inline-block mt-1 px-2.5 py-0.5 rounded-full bg-cyan-50 text-cyan-700 text-xs font-semibold">
                        Khách hàng
                      </span>
                    </div>
                  </div>

                  <nav aria-label="Menu tài khoản" className="flex flex-col gap-1.5 mt-6">
                    <button
                      onClick={() => navigate("/profile")}
                      className="w-full flex items-center justify-between px-4 py-2.5 rounded-xl bg-cyan-50 text-cyan-900 font-semibold text-sm transition-all"
                      type="button"
                    >
                      <span className="flex items-center gap-3">
                        <span className="material-symbols-outlined text-[20px] text-cyan-600">account_circle</span>
                        <span>Thông tin cá nhân</span>
                      </span>
                      <span className="w-2 h-2 rounded-full bg-cyan-500"></span>
                    </button>

                    <button
                      onClick={() => navigate("/profile/orders")}
                      className="w-full flex items-center justify-between px-4 py-2.5 rounded-xl hover:bg-slate-50 text-slate-700 font-medium text-sm transition-all"
                      type="button"
                    >
                      <span className="flex items-center gap-3">
                        <span className="material-symbols-outlined text-[20px] text-slate-500">receipt_long</span>
                        <span>Đơn hàng của tôi</span>
                      </span>
                      <span className="material-symbols-outlined text-[18px] text-slate-400">chevron_right</span>
                    </button>

                    <button
                      onClick={() => navigate("/profile/wishlist")}
                      className="w-full flex items-center justify-between px-4 py-2.5 rounded-xl hover:bg-slate-50 text-slate-700 font-medium text-sm transition-all"
                      type="button"
                    >
                      <span className="flex items-center gap-3">
                        <span className="material-symbols-outlined text-[20px] text-slate-500">favorite</span>
                        <span>Danh sách yêu thích</span>
                      </span>
                      <span className="material-symbols-outlined text-[18px] text-slate-400">chevron_right</span>
                    </button>

                    <button
                      onClick={() => navigate("/profile/ai-assistant")}
                      className="w-full flex items-center justify-between px-4 py-2.5 rounded-xl hover:bg-slate-50 text-slate-700 font-medium text-sm transition-all"
                      type="button"
                    >
                      <span className="flex items-center gap-3">
                        <span className="material-symbols-outlined text-[20px] text-slate-500">auto_awesome</span>
                        <span>Trợ lý AI Lên lịch trình</span>
                      </span>
                      <span className="material-symbols-outlined text-[18px] text-slate-400">chevron_right</span>
                    </button>

                    <button
                      onClick={() => navigate("/profile/change-password")}
                      className="w-full flex items-center justify-between px-4 py-2.5 rounded-xl hover:bg-slate-50 text-slate-700 font-medium text-sm transition-all"
                      type="button"
                    >
                      <span className="flex items-center gap-3">
                        <span className="material-symbols-outlined text-[20px] text-slate-500">lock_reset</span>
                        <span>Đổi mật khẩu</span>
                      </span>
                      <span className="material-symbols-outlined text-[18px] text-slate-400">chevron_right</span>
                    </button>

                    <button
                      onClick={() => { logout(); navigate("/auth"); }}
                      className="w-full flex items-center justify-between px-4 py-2.5 rounded-xl hover:bg-rose-50 text-rose-600 font-medium text-sm transition-all mt-2"
                      type="button"
                    >
                      <span className="flex items-center gap-3">
                        <span className="material-symbols-outlined text-[20px]">logout</span>
                        <span>Đăng xuất</span>
                      </span>
                    </button>
                  </nav>
                </div>
              </aside>

              {/* Main Profile Info Form */}
              <section className="lg:col-span-8 flex flex-col gap-6 w-full">
                <div className="bg-white rounded-2xl p-6 sm:p-8 shadow-sm border border-slate-100 w-full">
                  <div className="flex items-center justify-between pb-6 border-b border-slate-100 mb-6">
                    <div>
                      <h2 className="text-xl font-bold text-slate-900">Thông tin cá nhân</h2>
                    </div>
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 text-xs font-semibold">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                      Đã xác minh
                    </span>
                  </div>

                  <form className="flex flex-col gap-6" id="profileForm" onSubmit={handleSaveProfile}>
                    {/* Avatar Upload Block */}
                    <div className="flex flex-col sm:flex-row items-center gap-6 p-4 bg-slate-50 rounded-2xl">
                      <div className="relative w-20 h-20 rounded-full overflow-hidden bg-slate-200 shadow-sm flex-shrink-0">
                        <img
                          alt={user.fullName || "Avatar"}
                          className="w-full h-full object-cover"
                          src={user.avatarUrl || "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80"}
                        />
                      </div>
                      <div className="flex flex-col items-center sm:items-start text-center sm:text-left gap-2">
                        <span className="font-bold text-base text-slate-900">{user.fullName || "Khách hàng"}</span>
                        <div className="flex items-center gap-2">
                          <button className="px-4 py-1.5 rounded-xl bg-white border border-slate-200 text-slate-700 text-xs font-semibold hover:bg-slate-100 transition-colors" type="button">
                            Tải ảnh mới
                          </button>
                          <button className="px-4 py-1.5 rounded-xl text-slate-500 text-xs font-medium hover:text-rose-600 transition-colors" type="button">
                            Gỡ bỏ
                          </button>
                        </div>
                      </div>
                    </div>

                    {/* Form Fields Grid */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                      <div className="flex flex-col gap-1.5">
                        <label className="text-xs font-bold text-slate-700">Họ và tên</label>
                        <div className="relative flex items-center">
                          <span className="material-symbols-outlined absolute left-3.5 text-slate-400 text-[18px]">person</span>
                          <input
                            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-cyan-500 focus:bg-white transition-all font-medium"
                            type="text"
                            defaultValue={user.fullName || "Khách hàng"}
                          />
                        </div>
                      </div>

                      <div className="flex flex-col gap-1.5">
                        <label className="text-xs font-bold text-slate-700">Vai trò</label>
                        <div className="relative flex items-center">
                          <span className="material-symbols-outlined absolute left-3.5 text-slate-400 text-[18px]">badge</span>
                          <input
                            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-100 border border-slate-200 text-sm text-slate-600 outline-none cursor-not-allowed font-medium"
                            readOnly
                            type="text"
                            defaultValue="Khách hàng"
                          />
                        </div>
                      </div>

                      <div className="flex flex-col gap-1.5">
                        <label className="text-xs font-bold text-slate-700">Địa chỉ Email</label>
                        <div className="relative flex items-center">
                          <span className="material-symbols-outlined absolute left-3.5 text-slate-400 text-[18px]">mail</span>
                          <input
                            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-cyan-500 focus:bg-white transition-all font-medium"
                            type="email"
                            defaultValue={user.email || ""}
                          />
                        </div>
                      </div>

                      <div className="flex flex-col gap-1.5">
                        <label className="text-xs font-bold text-slate-700">Số điện thoại</label>
                        <div className="relative flex items-center">
                          <span className="material-symbols-outlined absolute left-3.5 text-slate-400 text-[18px]">phone_iphone</span>
                          <input
                            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-cyan-500 focus:bg-white transition-all font-medium"
                            type="tel"
                            defaultValue={user.phone || ""}
                          />
                        </div>
                      </div>
                    </div>

                    <div className="flex flex-col sm:flex-row items-center justify-end pt-4 border-t border-slate-100 gap-3">
                      <button
                        className="w-full sm:w-auto px-6 py-2.5 rounded-xl bg-slate-100 text-slate-700 font-semibold text-sm hover:bg-slate-200 transition-colors"
                        type="reset"
                      >
                        Hủy
                      </button>
                      <button
                        className="w-full sm:w-auto px-8 py-2.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white font-bold text-sm shadow-md transition-all"
                        type="submit"
                      >
                        Lưu thay đổi
                      </button>
                    </div>
                  </form>
                </div>
              </section>
            </div>
          </div>
        </div>

        {showToast && (
          <div className="fixed bottom-6 right-6 z-50 flex items-center gap-2 px-5 py-3 rounded-2xl bg-slate-900 text-white shadow-2xl animate-in slide-in-from-bottom-5">
            <span className="text-emerald-400 font-bold">✓</span>
            <span className="text-sm font-semibold">Đã lưu thông tin hồ sơ thành công!</span>
          </div>
        )}
      </div>
    </main>
  );
}
export default Profile;
