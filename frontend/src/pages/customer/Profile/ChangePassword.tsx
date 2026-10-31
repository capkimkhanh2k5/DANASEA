import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  KeyRound,
  Lock,
  ArrowLeft,
  CheckCircle2,
  ShieldCheck,
  Shield,
  Smartphone,
  Eye,
  EyeOff,
} from "lucide-react";

export function ChangePassword() {
  const navigate = useNavigate();
  const [success, setSuccess] = useState(false);
  const [showCurrentPass, setShowCurrentPass] = useState(false);
  const [showNewPass, setShowNewPass] = useState(false);
  const [showConfirmPass, setShowConfirmPass] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSuccess(true);
    setTimeout(() => {
      navigate('/profile');
    }, 2000);
  };

  return (
    <main className="w-full pt-24 pb-16 bg-slate-50 min-h-[calc(100vh-80px)] flex-1 flex flex-col">
      <div className="max-w-[800px] mx-auto px-4 sm:px-6 w-full">
        
        {/* Navigation Breadcrumb */}
        <div className="flex items-center gap-2 mb-6 text-[13px] text-slate-500 font-medium">
          <button 
            onClick={() => navigate('/profile')} 
            className="hover:text-slate-900 transition-colors cursor-pointer bg-transparent border-0 p-0 flex items-center gap-1 text-slate-600 font-semibold"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Tài khoản của bạn</span>
          </button>
          <span>/</span>
          <span className="text-slate-900 font-semibold">Đăng nhập &amp; Bảo mật</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-start">
          
          {/* Main Form Column */}
          <div className="md:col-span-8 bg-white rounded-2xl shadow-xs p-6 sm:p-8 border border-slate-200/80">
            <div className="flex items-center gap-3.5 mb-6 pb-4 border-b border-slate-100">
              <div className="w-10 h-10 rounded-xl bg-sky-50 text-[#0284c7] flex items-center justify-center shrink-0">
                <KeyRound className="w-5 h-5" />
              </div>
              <div>
                <h1 className="text-xl sm:text-[22px] font-bold text-slate-900 tracking-tight">
                  Đổi mật khẩu tài khoản
                </h1>
                <p className="text-[13px] text-slate-600 mt-0.5">
                  Thiết lập mật khẩu bảo mật mới cho tài khoản DANASEA của bạn
                </p>
              </div>
            </div>

            {success ? (
              <div className="p-6 rounded-xl bg-emerald-50 border border-emerald-200 text-center animate-in fade-in zoom-in">
                <CheckCircle2 className="w-10 h-10 text-emerald-600 mx-auto mb-2" />
                <h2 className="text-base font-bold text-emerald-900">Đổi mật khẩu thành công!</h2>
                <p className="text-xs text-emerald-700 mt-1">Hệ thống sẽ chuyển hướng bạn về trang Hồ sơ trong giây lát.</p>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div className="flex flex-col gap-1.5">
                  <label className="text-[13px] font-semibold text-slate-700">Mật khẩu hiện tại *</label>
                  <div className="relative flex items-center">
                    <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 pointer-events-none" />
                    <input 
                      required 
                      type={showCurrentPass ? "text" : "password"} 
                      placeholder="Nhập mật khẩu hiện tại" 
                      className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors font-medium" 
                    />
                    <button
                      type="button"
                      onClick={() => setShowCurrentPass(!showCurrentPass)}
                      className="absolute right-3 text-slate-400 hover:text-slate-700 cursor-pointer bg-transparent border-0 p-1"
                    >
                      {showCurrentPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="text-[13px] font-semibold text-slate-700">Mật khẩu mới *</label>
                  <div className="relative flex items-center">
                    <KeyRound className="w-4 h-4 text-slate-400 absolute left-3.5 pointer-events-none" />
                    <input 
                      required 
                      type={showNewPass ? "text" : "password"} 
                      placeholder="Tối thiểu 8 ký tự, gồm chữ hoa và số" 
                      className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors font-medium" 
                    />
                    <button
                      type="button"
                      onClick={() => setShowNewPass(!showNewPass)}
                      className="absolute right-3 text-slate-400 hover:text-slate-700 cursor-pointer bg-transparent border-0 p-1"
                    >
                      {showNewPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="text-[13px] font-semibold text-slate-700">Xác nhận mật khẩu mới *</label>
                  <div className="relative flex items-center">
                    <KeyRound className="w-4 h-4 text-slate-400 absolute left-3.5 pointer-events-none" />
                    <input 
                      required 
                      type={showConfirmPass ? "text" : "password"} 
                      placeholder="Nhập lại mật khẩu mới" 
                      className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-900 outline-none focus:border-slate-400 focus:bg-white transition-colors font-medium" 
                    />
                    <button
                      type="button"
                      onClick={() => setShowConfirmPass(!showConfirmPass)}
                      className="absolute right-3 text-slate-400 hover:text-slate-700 cursor-pointer bg-transparent border-0 p-1"
                    >
                      {showConfirmPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                {/* Password Requirement Note */}
                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/80 flex items-start gap-2.5 text-xs text-slate-600 mt-1">
                  <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                  <p>
                    Mật khẩu an toàn giúp bảo vệ thông tin đặt chỗ và lịch sử giao dịch vé điện tử cano của bạn.
                  </p>
                </div>
                
                <div className="pt-4 border-t border-slate-100 flex items-center justify-end gap-3 mt-2">
                  <button 
                    type="button" 
                    onClick={() => navigate('/profile')}
                    className="px-5 py-2.5 rounded-xl bg-slate-100 text-slate-700 font-semibold text-xs sm:text-sm hover:bg-slate-200 transition-colors cursor-pointer"
                  >
                    Hủy bỏ
                  </button>
                  <button 
                    type="submit" 
                    className="px-6 py-2.5 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white font-semibold text-xs sm:text-sm shadow-xs transition-colors cursor-pointer"
                  >
                    Cập nhật mật khẩu
                  </button>
                </div>
              </form>
            )}
          </div>

          {/* Right Tips Column */}
          <div className="md:col-span-4 flex flex-col gap-4">
            <div className="p-5 bg-white rounded-2xl border border-slate-200/80 shadow-xs flex flex-col gap-3">
              <div className="flex items-center gap-2 text-slate-900 font-bold text-sm">
                <Shield className="w-4 h-4 text-emerald-600" />
                <span>Tiêu chuẩn bảo mật</span>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Sau khi đổi mật khẩu thành công, toàn bộ phiên đăng nhập cũ trên các thiết bị khác sẽ được tự động đăng xuất để bảo đảm an toàn.
              </p>
            </div>

            <div className="p-5 bg-white rounded-2xl border border-slate-200/80 shadow-xs flex flex-col gap-3">
              <div className="flex items-center gap-2 text-slate-900 font-bold text-sm">
                <Smartphone className="w-4 h-4 text-sky-600" />
                <span>Thiết bị đang dùng</span>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Trình duyệt web hiện tại trên thiết bị của bạn được xác thực hợp lệ qua giao thức HTTPS.
              </p>
            </div>
          </div>

        </div>
      </div>
    </main>
  );
}

export default ChangePassword;
