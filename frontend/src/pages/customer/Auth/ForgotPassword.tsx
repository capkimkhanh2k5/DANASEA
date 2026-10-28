import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { authService } from "../../../services/authService";

export function ForgotPassword() {
  // Step 1: Nhập email -> Step 2: Nhập OTP & Mật khẩu mới -> Step 3: Thành công (hoặc Step 4: OTP bị khóa / hết hạn)
  const [step, setStep] = useState<1 | 2 | 3 | 4>(1);
  const [email, setEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [timer, setTimer] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");

  // Passwords
  const [newPass, setNewPass] = useState("");
  const [confirmPass, setConfirmPass] = useState("");
  const [showNewPass, setShowNewPass] = useState(false);
  const [showConfirmPass, setShowConfirmPass] = useState(false);

  const navigate = useNavigate();

  // Timer countdown for Resend OTP (60s cooldown to respect Bucket4j rate limits)
  useEffect(() => {
    let interval: ReturnType<typeof setTimeout> | null = null;
    if (timer > 0) {
      interval = setTimeout(() => {
        setTimer((prev) => prev - 1);
      }, 1000);
    }
    return () => {
      if (interval) clearTimeout(interval);
    };
  }, [timer]);

  // Handle Step 1: Request Password Reset
  const handleRequestSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanEmail = email.trim();
    if (!cleanEmail) {
      setErrorMsg("Vui lòng nhập địa chỉ email tài khoản.");
      return;
    }

    setIsSubmitting(true);
    setErrorMsg("");

    try {
      console.log("[ForgotPassword] Gửi yêu cầu đặt lại mật khẩu cho:", cleanEmail);
      await authService.forgotPassword(cleanEmail);
      // Theo Anti-Enumeration Defense của Backend, dù email tồn tại hay không đều trả về 200 OK
      setStep(2);
      setTimer(60); // 60s cooldown
    } catch (err: any) {
      console.error("[ForgotPassword] Gửi yêu cầu thất bại:", err);
      const status = err.response?.status;
      if (status === 429) {
        setErrorMsg("Bạn đã gửi yêu cầu quá nhanh. Vui lòng chờ 1 phút trước khi thử lại.");
      } else {
        setErrorMsg(err.response?.data?.message || "Không thể gửi yêu cầu đặt lại mật khẩu lúc này. Vui lòng thử lại sau.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Resend OTP in Step 2
  const handleResend = async () => {
    if (timer > 0 || isSubmitting) return;
    setIsSubmitting(true);
    setErrorMsg("");
    setOtp(""); // Reset ô nhập OTP

    try {
      console.log("[ForgotPassword] Yêu cầu gửi lại mã OTP cho:", email);
      await authService.forgotPassword(email.trim());
      setTimer(60);
    } catch (err: any) {
      console.error("[ForgotPassword] Gửi lại mã thất bại:", err);
      const status = err.response?.status;
      if (status === 429) {
        setErrorMsg("Vui lòng đợi kết thúc thời gian chờ trước khi gửi lại mã.");
      } else {
        setErrorMsg(err.response?.data?.message || "Không thể gửi lại mã OTP. Vui lòng thử lại sau.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // Password criteria checking according to backend regex:
  // ^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$
  const hasMinLength = newPass.length >= 8;
  const hasUppercase = /[A-Z]/.test(newPass);
  const hasNumber = /[0-9]/.test(newPass);
  const hasSpecial = /[@$!%*?&]/.test(newPass);

  const passedCriteria = [hasMinLength, hasUppercase, hasNumber, hasSpecial].filter(Boolean).length;

  const getStrengthLabel = () => {
    if (newPass.length === 0) return "Chưa nhập";
    if (passedCriteria <= 1) return "Rất yếu";
    if (passedCriteria === 2) return "Trung bình";
    if (passedCriteria === 3) return "Khá an toàn";
    return "Rất mạnh";
  };

  // Handle Step 2: Reset Password Submit
  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg("");

    const cleanOtp = otp.trim();
    if (!/^\d{6}$/.test(cleanOtp)) {
      setErrorMsg("Mã OTP phải bao gồm chính xác 6 chữ số.");
      return;
    }

    if (passedCriteria < 4) {
      setErrorMsg("Mật khẩu mới chưa đáp ứng đầy đủ 4 tiêu chí an toàn bắt buộc.");
      return;
    }

    if (newPass !== confirmPass) {
      setErrorMsg("Mật khẩu xác nhận không khớp!");
      return;
    }

    setIsSubmitting(true);

    try {
      console.log("[ForgotPassword] Đang xác thực OTP & đặt lại mật khẩu cho:", email);
      await authService.resetPassword({
        email: email.trim(),
        otp: cleanOtp,
        newPassword: newPass,
      });

      console.log("[ForgotPassword] Đặt lại mật khẩu thành công!");
      setStep(3); // Chuyển sang màn Thành công
    } catch (err: any) {
      console.error("[ForgotPassword] Đặt lại mật khẩu thất bại:", err);
      const status = err.response?.status;
      const code = err.response?.data?.code;

      if (status === 429) {
        setErrorMsg("Bạn đã thử quá số lần quy định. Vui lòng chờ ít phút.");
      } else if (code === "AUTH_008" || err.response?.data?.message?.includes("AUTH_008")) {
        // Quá 5 lần thử sai -> Mã OTP bị hủy
        setStep(4);
      } else if (code === "AUTH_007" || err.response?.data?.message?.includes("AUTH_007")) {
        setErrorMsg("Mã OTP không chính xác hoặc đã hết hạn (tối đa 5 lần thử). Vui lòng kiểm tra lại.");
      } else {
        setErrorMsg(err.response?.data?.message || "Xác thực thất bại. Vui lòng kiểm tra lại mã OTP và mật khẩu.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="w-full min-h-screen pt-20 bg-surface flex flex-col justify-center items-center">
      <div className="flex flex-col w-full">
        <div className="relative w-full overflow-hidden py-10 px-4 sm:px-6 lg:px-20">
          {/* Oceanic Ambient Background Orbs */}
          <div className="absolute -top-32 -left-20 w-96 h-96 rounded-full bg-cyan-500/10 blur-3xl pointer-events-none"></div>
          <div className="absolute top-1/2 -right-32 w-80 h-80 rounded-full bg-teal-500/10 blur-3xl pointer-events-none"></div>

          <div className="max-w-7xl mx-auto flex flex-col gap-8">
            {/* Top Flow Header */}
            <div className="flex flex-wrap items-center justify-between gap-4 bg-white/90 backdrop-blur-md px-6 py-4 rounded-2xl shadow-xs border border-slate-200/80">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-primary flex items-center justify-center text-white shadow-xs">
                  <span className="material-symbols-outlined text-[24px]">lock_reset</span>
                </div>
                <div>
                  <div className="font-bold text-slate-900 text-base sm:text-lg">DANASEA Security Portal</div>
                  <div className="text-xs text-slate-500">Khôi phục quyền truy cập & bảo mật tài khoản hàng hải</div>
                </div>
              </div>

              {/* Progress Indicator */}
              <div className="flex items-center gap-2 text-xs font-semibold text-slate-600">
                <span className={`px-3 py-1.5 rounded-full transition-colors ${step === 1 ? "bg-primary text-white font-bold" : "bg-slate-100 text-slate-600"}`}>
                  1. Gửi mã OTP
                </span>
                <span className="text-slate-300">/</span>
                <span className={`px-3 py-1.5 rounded-full transition-colors ${step === 2 ? "bg-primary text-white font-bold" : "bg-slate-100 text-slate-600"}`}>
                  2. Đặt mật khẩu mới
                </span>
                <span className="text-slate-300">/</span>
                <span className={`px-3 py-1.5 rounded-full transition-colors ${step === 3 ? "bg-emerald-600 text-white font-bold" : "bg-slate-100 text-slate-600"}`}>
                  3. Hoàn tất
                </span>
              </div>
            </div>

            {/* Main Layout Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-stretch">
              {/* Left Column: Maritime Brand Security Story */}
              <div className="lg:col-span-5 flex flex-col justify-between rounded-2xl bg-white/95 border border-slate-200/80 shadow-xs p-6 sm:p-8 relative overflow-hidden">
                <div className="relative z-10 flex flex-col gap-5">
                  <div className="inline-flex items-center gap-2 self-start px-3 py-1.5 rounded-full bg-slate-100 text-primary font-bold text-xs uppercase tracking-wider">
                    <span className="material-symbols-outlined text-[16px]">verified_user</span>
                    Hệ thống xác thực chuẩn hàng hải
                  </div>
                  <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight leading-tight">
                    An tâm khám phá đại dương cùng DANASEA
                  </h1>
                  <p className="text-sm text-slate-600 leading-relaxed">
                    Quy trình khôi phục và bảo mật tài khoản áp dụng chuẩn bảo vệ chống rò rỉ danh tính (Anti-Enumeration) và kiểm soát dò mã OTP tự động, bảo vệ toàn diện lịch trình du ngoạn biển của bạn.
                  </p>

                  {/* Trust List */}
                  <div className="flex flex-col gap-3 pt-2">
                    <div className="flex items-center gap-3 p-3 rounded-xl bg-slate-50 border border-slate-100">
                      <div className="w-8 h-8 rounded-full bg-cyan-100 flex items-center justify-center text-cyan-700 shrink-0">
                        <span className="material-symbols-outlined text-[18px]">lock_clock</span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-xs font-bold text-slate-800">Thời hạn mã OTP an toàn</span>
                        <span className="text-[11px] text-slate-500">Mã có hiệu lực trong 15 phút và tự hủy sau 5 lần thử sai</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-3 p-3 rounded-xl bg-slate-50 border border-slate-100">
                      <div className="w-8 h-8 rounded-full bg-cyan-100 flex items-center justify-center text-cyan-700 shrink-0">
                        <span className="material-symbols-outlined text-[18px]">shield</span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-xs font-bold text-slate-800">Thu hồi phiên đăng nhập cũ</span>
                        <span className="text-[11px] text-slate-500">Tự động vô hiệu hóa các thiết bị khác ngay khi đổi mật khẩu</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-3 p-3 rounded-xl bg-slate-50 border border-slate-100">
                      <div className="w-8 h-8 rounded-full bg-cyan-100 flex items-center justify-center text-cyan-700 shrink-0">
                        <span className="material-symbols-outlined text-[18px]">support_agent</span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-xs font-bold text-slate-800">Hỗ trợ khẩn cấp 24/7</span>
                        <span className="text-[11px] text-slate-500">Kênh hỗ trợ trực tuyến hỗ trợ du khách gặp sự cố đăng nhập</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Right Column: Dynamic Flow Panels */}
              <div className="lg:col-span-7 flex flex-col justify-center">
                {/* Error Banner */}
                {errorMsg && (
                  <div className="mb-4 p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-sm flex items-start gap-2.5 animate-fade-in">
                    <span className="material-symbols-outlined text-[20px] shrink-0 mt-0.5 text-rose-600">error</span>
                    <span className="font-medium">{errorMsg}</span>
                  </div>
                )}

                {/* =========================================================================
                    STEP 1: Request Password Recovery OTP
                   ========================================================================= */}
                {step === 1 && (
                  <div className="w-full bg-white rounded-2xl border border-slate-200/80 shadow-md p-6 sm:p-10 animate-fade-in-up">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="text-xs uppercase font-extrabold text-primary tracking-widest">
                        Bước 01 / Nhập Email tài khoản
                      </span>
                    </div>
                    <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900 mb-2">Quên mật khẩu?</h2>
                    <p className="text-sm text-slate-600 mb-6">
                      Nhập địa chỉ email liên kết với tài khoản DANASEA của bạn để nhận mã xác minh OTP 6 chữ số.
                    </p>

                    <form className="flex flex-col gap-5" onSubmit={handleRequestSubmit}>
                      <div className="flex flex-col gap-2">
                        <label className="text-xs font-bold text-slate-700" htmlFor="recovery-email">
                          Địa chỉ email
                        </label>
                        <div className="relative flex items-center">
                          <span className="material-symbols-outlined absolute left-4 text-slate-400 text-[20px]">mail</span>
                          <input
                            className="w-full h-12 pl-12 pr-4 bg-slate-50 border border-slate-200 text-slate-900 rounded-xl outline-none focus:bg-white focus:border-primary focus:ring-2 focus:ring-primary/20 transition text-sm font-medium"
                            id="recovery-email"
                            placeholder="nhap.email@example.com"
                            required
                            type="email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            disabled={isSubmitting}
                          />
                        </div>
                      </div>

                      <button
                        className="w-full h-12 rounded-xl bg-primary text-white font-bold text-sm flex items-center justify-center gap-2 hover:bg-primary/90 transition shadow-xs cursor-pointer disabled:opacity-50"
                        type="submit"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? (
                          <>
                            <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                            <span>Đang xử lý...</span>
                          </>
                        ) : (
                          <>
                            <span>Gửi mã OTP xác nhận</span>
                            <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
                          </>
                        )}
                      </button>

                      <div className="flex items-center justify-center gap-2 pt-2 text-sm text-slate-500">
                        <span>Bạn đã nhớ lại mật khẩu?</span>
                        <button
                          type="button"
                          className="font-bold text-primary hover:underline inline-flex items-center gap-1 cursor-pointer"
                          onClick={() => navigate("/auth")}
                        >
                          <span>Đăng nhập</span>
                          <span className="material-symbols-outlined text-[16px]">arrow_back</span>
                        </button>
                      </div>
                    </form>
                  </div>
                )}

                {/* =========================================================================
                    STEP 2: Enter 6-digit OTP & New Password
                   ========================================================================= */}
                {step === 2 && (
                  <div className="w-full bg-white rounded-2xl border border-slate-200/80 shadow-md p-6 sm:p-10 animate-fade-in-up">
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <span className="text-xs uppercase font-extrabold text-primary tracking-widest">
                        Bước 02 / Xác thực OTP & Đổi mật khẩu
                      </span>
                      <button
                        type="button"
                        onClick={() => { setStep(1); setErrorMsg(""); }}
                        className="text-xs font-semibold text-slate-500 hover:text-slate-900 cursor-pointer flex items-center gap-1"
                      >
                        <span className="material-symbols-outlined text-[14px]">edit</span>
                        <span>Đổi email</span>
                      </button>
                    </div>

                    <h2 className="text-2xl font-extrabold text-slate-900 mb-1">Thiết lập mật khẩu mới</h2>
                    <p className="text-xs sm:text-sm text-slate-600 mb-5">
                      Mã xác nhận 6 số đã được gửi tới <strong className="text-slate-900">{email}</strong>. Vui lòng kiểm tra hộp thư hoặc terminal console.
                    </p>

                    <form className="flex flex-col gap-4" onSubmit={handlePasswordSubmit}>
                      {/* OTP 6 digits input */}
                      <div className="flex flex-col gap-1.5">
                        <div className="flex items-center justify-between">
                          <label className="text-xs font-bold text-slate-700" htmlFor="otp-input">
                            Mã xác nhận OTP (6 chữ số)
                          </label>
                          <button
                            type="button"
                            onClick={handleResend}
                            disabled={timer > 0 || isSubmitting}
                            className={`text-xs font-bold transition-colors cursor-pointer ${timer > 0 ? "text-slate-400 cursor-not-allowed" : "text-primary hover:underline"}`}
                          >
                            {timer > 0 ? `Gửi lại mã sau (${timer}s)` : "Gửi lại mã OTP"}
                          </button>
                        </div>
                        <div className="relative flex items-center">
                          <span className="material-symbols-outlined absolute left-4 text-slate-400 text-[20px]">pin</span>
                          <input
                            className="w-full h-12 pl-12 pr-4 bg-slate-50 border border-slate-200 text-slate-900 rounded-xl outline-none focus:bg-white focus:border-primary focus:ring-2 focus:ring-primary/20 transition text-center tracking-[0.3em] font-mono text-lg font-bold"
                            id="otp-input"
                            maxLength={6}
                            placeholder="------"
                            required
                            type="text"
                            value={otp}
                            onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                            disabled={isSubmitting}
                          />
                        </div>
                      </div>

                      {/* New Password */}
                      <div className="flex flex-col gap-1.5">
                        <label className="text-xs font-bold text-slate-700" htmlFor="new-pass">
                          Mật khẩu mới
                        </label>
                        <div className="relative flex items-center">
                          <span className="material-symbols-outlined absolute left-4 text-slate-400 text-[20px]">lock</span>
                          <input
                            className="w-full h-12 pl-12 pr-12 bg-slate-50 border border-slate-200 text-slate-900 rounded-xl outline-none focus:bg-white focus:border-primary focus:ring-2 focus:ring-primary/20 transition text-sm font-medium"
                            id="new-pass"
                            placeholder="Tối thiểu 8 ký tự..."
                            required
                            type={showNewPass ? "text" : "password"}
                            value={newPass}
                            onChange={(e) => setNewPass(e.target.value)}
                            disabled={isSubmitting}
                          />
                          <button
                            type="button"
                            className="absolute right-4 text-slate-400 hover:text-slate-700 cursor-pointer"
                            onClick={() => setShowNewPass(!showNewPass)}
                          >
                            <span className="material-symbols-outlined text-[20px]">
                              {showNewPass ? "visibility_off" : "visibility"}
                            </span>
                          </button>
                        </div>
                      </div>

                      {/* Password Strength Checklist */}
                      <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200/80 flex flex-col gap-2.5">
                        <div className="flex items-center justify-between text-xs">
                          <span className="text-slate-500 font-medium">Độ mạnh mật khẩu:</span>
                          <span className={`font-bold ${passedCriteria === 4 ? "text-emerald-600" : passedCriteria >= 2 ? "text-amber-600" : "text-slate-600"}`}>
                            {getStrengthLabel()}
                          </span>
                        </div>
                        <div className="grid grid-cols-4 gap-1.5 h-1.5 w-full">
                          <div className={`h-full rounded-full transition-all duration-300 ${passedCriteria >= 1 ? "bg-amber-500" : "bg-slate-200"}`}></div>
                          <div className={`h-full rounded-full transition-all duration-300 ${passedCriteria >= 2 ? "bg-amber-500" : "bg-slate-200"}`}></div>
                          <div className={`h-full rounded-full transition-all duration-300 ${passedCriteria >= 3 ? "bg-primary" : "bg-slate-200"}`}></div>
                          <div className={`h-full rounded-full transition-all duration-300 ${passedCriteria >= 4 ? "bg-emerald-500" : "bg-slate-200"}`}></div>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5 text-xs">
                          <div className={`flex items-center gap-1.5 ${hasMinLength ? "text-emerald-700 font-semibold" : "text-slate-400"}`}>
                            <span className="material-symbols-outlined text-[15px]">{hasMinLength ? "check_circle" : "cancel"}</span>
                            <span>Tối thiểu 8 ký tự</span>
                          </div>
                          <div className={`flex items-center gap-1.5 ${hasUppercase ? "text-emerald-700 font-semibold" : "text-slate-400"}`}>
                            <span className="material-symbols-outlined text-[15px]">{hasUppercase ? "check_circle" : "cancel"}</span>
                            <span>Ít nhất 1 chữ hoa (A-Z)</span>
                          </div>
                          <div className={`flex items-center gap-1.5 ${hasNumber ? "text-emerald-700 font-semibold" : "text-slate-400"}`}>
                            <span className="material-symbols-outlined text-[15px]">{hasNumber ? "check_circle" : "cancel"}</span>
                            <span>Ít nhất 1 chữ số (0-9)</span>
                          </div>
                          <div className={`flex items-center gap-1.5 ${hasSpecial ? "text-emerald-700 font-semibold" : "text-slate-400"}`}>
                            <span className="material-symbols-outlined text-[15px]">{hasSpecial ? "check_circle" : "cancel"}</span>
                            <span>Ký tự đặc biệt (@$!%*?&)</span>
                          </div>
                        </div>
                      </div>

                      {/* Confirm Password */}
                      <div className="flex flex-col gap-1.5">
                        <label className="text-xs font-bold text-slate-700" htmlFor="confirm-pass">
                          Xác nhận mật khẩu mới
                        </label>
                        <div className="relative flex items-center">
                          <span className="material-symbols-outlined absolute left-4 text-slate-400 text-[20px]">lock_clock</span>
                          <input
                            className="w-full h-12 pl-12 pr-12 bg-slate-50 border border-slate-200 text-slate-900 rounded-xl outline-none focus:bg-white focus:border-primary focus:ring-2 focus:ring-primary/20 transition text-sm font-medium"
                            id="confirm-pass"
                            placeholder="Nhập lại mật khẩu mới..."
                            required
                            type={showConfirmPass ? "text" : "password"}
                            value={confirmPass}
                            onChange={(e) => setConfirmPass(e.target.value)}
                            disabled={isSubmitting}
                          />
                          <button
                            type="button"
                            className="absolute right-4 text-slate-400 hover:text-slate-700 cursor-pointer"
                            onClick={() => setShowConfirmPass(!showConfirmPass)}
                          >
                            <span className="material-symbols-outlined text-[20px]">
                              {showConfirmPass ? "visibility_off" : "visibility"}
                            </span>
                          </button>
                        </div>
                      </div>

                      <button
                        className="w-full h-12 mt-2 rounded-xl bg-primary text-white font-bold text-sm flex items-center justify-center gap-2 hover:bg-primary/90 transition shadow-xs cursor-pointer disabled:opacity-50"
                        type="submit"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? (
                          <>
                            <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                            <span>Đang xác thực...</span>
                          </>
                        ) : (
                          <>
                            <span>Cập nhật mật khẩu</span>
                            <span className="material-symbols-outlined text-[18px]">check_circle</span>
                          </>
                        )}
                      </button>
                    </form>
                  </div>
                )}

                {/* =========================================================================
                    STEP 3: Success State
                   ========================================================================= */}
                {step === 3 && (
                  <div className="w-full bg-white rounded-2xl border border-slate-200/80 shadow-md p-6 sm:p-10 text-center animate-fade-in-up">
                    <div className="w-16 h-16 rounded-full bg-emerald-100 flex items-center justify-center text-emerald-600 mx-auto mb-4 shadow-xs">
                      <span className="material-symbols-outlined text-[36px]">task_alt</span>
                    </div>
                    <div className="inline-block px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 text-xs font-bold uppercase mb-2">
                      Khôi phục thành công
                    </div>
                    <h2 className="text-2xl font-extrabold text-slate-900 mb-2">Mật khẩu đã được cập nhật!</h2>
                    <p className="text-sm text-slate-600 max-w-md mx-auto mb-6 leading-relaxed">
                      Tài khoản của bạn đã được thiết lập mật khẩu mới an toàn. Toàn bộ phiên đăng nhập cũ trên các thiết bị khác đã được thu hồi tự động.
                    </p>

                    <button
                      type="button"
                      className="w-full sm:w-auto px-8 h-12 rounded-xl bg-primary text-white font-bold text-sm inline-flex items-center justify-center gap-2 hover:bg-primary/90 transition shadow-xs cursor-pointer"
                      onClick={() => navigate("/auth")}
                    >
                      <span>Đăng nhập ngay</span>
                      <span className="material-symbols-outlined text-[18px]">login</span>
                    </button>
                  </div>
                )}

                {/* =========================================================================
                    STEP 4: Token Locked (Brute-force lockout >= 5 attempts)
                   ========================================================================= */}
                {step === 4 && (
                  <div className="w-full bg-white rounded-2xl border border-slate-200/80 shadow-md p-6 sm:p-10 text-center animate-fade-in-up">
                    <div className="w-16 h-16 rounded-full bg-rose-100 flex items-center justify-center text-rose-600 mx-auto mb-4 shadow-xs">
                      <span className="material-symbols-outlined text-[36px]">lock_clock</span>
                    </div>
                    <div className="inline-block px-3 py-1 rounded-full bg-rose-50 text-rose-700 text-xs font-bold uppercase mb-2">
                      Mã xác nhận đã bị khóa
                    </div>
                    <h2 className="text-2xl font-extrabold text-slate-900 mb-2">Đã quá số lần thử cho phép</h2>
                    <p className="text-sm text-slate-600 max-w-md mx-auto mb-6 leading-relaxed">
                      Để bảo đảm an toàn dữ liệu hàng hải của bạn, mã OTP này đã bị vô hiệu hóa vì nhập sai quá 5 lần. Vui lòng gửi lại yêu cầu để nhận mã mới.
                    </p>

                    <button
                      type="button"
                      className="w-full sm:w-auto px-8 h-12 rounded-xl bg-primary text-white font-bold text-sm inline-flex items-center justify-center gap-2 hover:bg-primary/90 transition shadow-xs cursor-pointer"
                      onClick={() => { setStep(1); setErrorMsg(""); setOtp(""); setNewPass(""); setConfirmPass(""); }}
                    >
                      <span className="material-symbols-outlined text-[18px]">refresh</span>
                      <span>Gửi lại yêu cầu mới</span>
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}
