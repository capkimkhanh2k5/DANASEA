import type { HttpRequestError } from "../../../types/http";
import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "../../../store/useAuthStore";
import { useLoadingStore } from "../../../store/useLoadingStore";
import { authService } from "../../../services/authService";
import { AuthCinematicBackdrop } from "../../../components/customer/AuthCinematicBackdrop";
import { GoogleLoginButton } from "../../../components/customer/GoogleLoginButton";
import type { User } from "../../../types";

export function Auth() {
  const [activeTab, setActiveTab] = useState<"login" | "register">("login");
  
  // Login Form States
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  
  // Register Form States
  const [regFullName, setRegFullName] = useState("");
  const [regEmail, setRegEmail] = useState("");
  const [regPassword, setRegPassword] = useState("");
  const [regConfirmPassword, setRegConfirmPassword] = useState("");
  const [showRegPassword, setShowRegPassword] = useState(false);
  const [showRegConfirmPassword, setShowRegConfirmPassword] = useState(false);
  
  // Register Sub-step: 'form' (enter info) -> 'otp' (verify 6-digit OTP)
  const [regStep, setRegStep] = useState<"form" | "otp">("form");
  const [otpCode, setOtpCode] = useState("");
  const [otpCooldown, setOtpCooldown] = useState(0);
  const [otpSessionExpiresAt, setOtpSessionExpiresAt] = useState<number | null>(null);
  const [pendingUser, setPendingUser] = useState<User | null>(null);

  // General Feedback States
  const [banner, setBanner] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [showSuccessToast, setShowSuccessToast] = useState(false);
  const [toastMessage, setToastMessage] = useState("Đăng nhập thành công");
  
  const navigate = useNavigate();
  const setAuth = useAuthStore(state => state.setAuth);
  const setPendingToken = useAuthStore(state => state.setPendingToken);

  // 60-second OTP cooldown ticker
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    if (otpCooldown > 0) {
      timer = setTimeout(() => setOtpCooldown(prev => prev - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [otpCooldown]);

  // 15-minute OTP session expiration timer: automatically reset to register form
  useEffect(() => {
    if (regStep !== 'otp' || !otpSessionExpiresAt) return;

    const interval = setInterval(() => {
      if (Date.now() >= otpSessionExpiresAt) {
        console.warn('[Auth OTP] Phiên xác thực OTP 15 phút đã hết hạn. Đang chuyển về form đăng ký...');
        setRegStep('form');
        setOtpCode('');
        setPendingUser(null);
        setOtpSessionExpiresAt(null);
        useAuthStore.getState().logout();
        setBanner('custom_error');
        setErrorMessage('Phiên xác thực đã hết hạn sau 15 phút. Vui lòng đăng ký lại.');
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [regStep, otpSessionExpiresAt]);

  // -------------------------------------------------------------
  // LOGIN FLOW (Preserved & Tested from feat/auth-login)
  // -------------------------------------------------------------
  const executeLogin = async (emailInput: string, passwordInput: string) => {
    const trimmedEmail = emailInput.trim();
    if (!trimmedEmail || !passwordInput) {
      setBanner('wrong_creds');
      setErrorMessage(null);
      return;
    }

    setIsLoading(true);
    setBanner(null);
    setErrorMessage(null);

    console.groupCollapsed(`%c[Auth Flow] Đang đăng nhập: ${trimmedEmail}`, 'color: #0284c7; font-weight: bold;');
    console.log('[Bước 1/5] Kiểm tra tham số gửi đi:', { email: trimmedEmail, password: '***' });
    console.log('[Bước 2/5] Đang gửi HTTP POST /api/auth/login tới Backend...');

    try {
      const authRes = await authService.login({
        email: trimmedEmail,
        password: passwordInput,
      });

      console.log('[Bước 3/5] Backend trả về kết quả thành công (200 OK):', authRes);

      const role = authRes.role;
      const avatarUrl = role === 'ADMIN'
        ? 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150'
        : role === 'VENDOR'
        ? 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150'
        : 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150';

      const userModel: User = {
        id: authRes.userId,
        email: authRes.email,
        role: role,
        fullName: trimmedEmail.split('@')[0],
        avatarUrl: avatarUrl,
        isEmailVerified: true,
        isLocked: false,
        phone: '',
        passwordHash: '',
        locale: 'vi',
      };

      console.log('[Bước 4/5] Lưu trữ Token và User vào Zustand Store (danasea_auth_storage):', {
        userId: userModel.id,
        role: userModel.role,
        tokenLength: authRes.accessToken?.length,
      });
      setAuth(userModel, authRes.accessToken);

      const destination = role === 'ADMIN' ? '/admin' : role === 'VENDOR' ? '/vendor' : '/';
      setToastMessage("Đăng nhập thành công");
      setShowSuccessToast(true);

      console.log(`[Bước 5/5] Hoàn tất đăng nhập! Chuẩn bị điều hướng tới: ${destination}`);
      console.groupEnd();

      // Trigger wave transition into destination workspace
      useLoadingStore.getState().triggerDemoLoading(1200, "Đang kết nối tài khoản...", "Chào mừng trở lại với DANASEA");

      setTimeout(() => {
        navigate(destination);
      }, 1100);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
      console.groupEnd();
      console.error('[Auth Flow] Lỗi đăng nhập thất bại:', err);

      const status = err.response?.status;
      const serverMessage = err.response?.data?.message || err.message;

      if (status === 401) {
        console.warn('[Auth Flow] Sai tài khoản hoặc mật khẩu (401 Unauthorized)');
        setBanner('wrong_creds');
      } else if (status === 403) {
        const errCode = (err.response?.data as { code?: string })?.code;
        const isLocked = errCode === 'USER_LOCKED' ||
          serverMessage?.toLowerCase().includes('khóa') ||
          serverMessage?.toLowerCase().includes('locked') ||
          serverMessage?.toLowerCase().includes('bị vô hiệu');
        if (isLocked) {
          console.warn('[Auth Flow] Tài khoản bị khóa (403 Forbidden - USER_LOCKED)');
          setBanner('locked');
        } else {
          console.warn('[Auth Flow] 403 Forbidden - Không có quyền hoặc bị từ chối:', serverMessage);
          setBanner('custom_error');
          setErrorMessage(serverMessage || 'Truy cập bị từ chối (403 Forbidden).');
        }
      } else if (!err.response || err.code === 'ERR_NETWORK') {
        console.error('[Auth Flow] Không thể kết nối tới Backend (Network Error)');
        setBanner('network_error');
        setErrorMessage('Không thể kết nối đến máy chủ Backend (http://localhost:8080). Hãy đảm bảo Backend đã khởi động.');
      } else {
        console.error('[Auth Flow] Lỗi từ máy chủ:', serverMessage);
        setBanner('custom_error');
        setErrorMessage(serverMessage || 'Đăng nhập không thành công. Vui lòng thử lại.');
      }
    } finally {
      setIsLoading(false);
    }
  };


  const handleLoginSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    executeLogin(email, password);
  };

  // -------------------------------------------------------------
  // GOOGLE OAUTH2 LOGIN FLOW (POST /api/auth/oauth2/google)
  // -------------------------------------------------------------
  const handleGoogleLoginSuccess = async (credential: string) => {
    if (!credential) {
      console.warn('[Google Auth] Không nhận được credential từ Google');
      return;
    }

    setIsLoading(true);
    setBanner(null);
    setErrorMessage(null);

    console.groupCollapsed('%c[Google Auth Flow] Đang xác thực với Backend...', 'color: #ea4335; font-weight: bold;');
    console.log('[Bước 1/4] Gửi credential tới Backend: POST /api/auth/oauth2/google');

    try {
      const authRes = await authService.loginWithGoogle(credential);
      console.log('[Bước 2/4] Backend xác thực thành công:', authRes);

      let userModel: User;
      try {
        useAuthStore.getState().setPendingToken(authRes.accessToken);
        const profile = await authService.getMyProfile();
        userModel = {
          id: profile.id,
          email: profile.email,
          role: profile.role,
          fullName: profile.fullName || authRes.email.split('@')[0],
          avatarUrl: profile.avatarUrl || 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
          isEmailVerified: profile.isEmailVerified,
          isLocked: false,
          phone: '',
          passwordHash: '',
          locale: profile.locale || 'vi',
        };
      } catch (profileErr) {
        console.warn('[Google Auth] Không lấy được profile từ /users/me, sử dụng payload cơ bản:', profileErr);
        userModel = {
          id: authRes.userId,
          email: authRes.email,
          role: authRes.role,
          fullName: authRes.email.split('@')[0],
          avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
          isEmailVerified: true,
          isLocked: false,
          phone: '',
          passwordHash: '',
          locale: 'vi',
        };
      }

      console.log('[Bước 3/4] Lưu trữ Token và User vào Zustand Store');
      setAuth(userModel, authRes.accessToken);

      const destination = userModel.role === 'ADMIN' ? '/admin' : userModel.role === 'VENDOR' ? '/vendor' : '/';
      setToastMessage("Đăng nhập Google thành công");
      setShowSuccessToast(true);

      console.log(`[Bước 4/4] Điều hướng tới: ${destination}`);
      console.groupEnd();

      setTimeout(() => {
        navigate(destination);
      }, 1200);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
      console.groupEnd();
      console.error('[Google Auth] Lỗi đăng nhập Google thất bại:', err);
      const status = err.response?.status;
      const serverMessage = err.response?.data?.message || err.message;

      if (status === 401) {
        setBanner('custom_error');
        setErrorMessage('Mã xác thực Google không hợp lệ hoặc đã hết hạn.');
      } else if (status === 403) {
        setBanner('locked');
      } else if (!err.response || err.code === 'ERR_NETWORK') {
        setBanner('network_error');
        setErrorMessage('Không thể kết nối đến máy chủ Backend (http://localhost:8080). Hãy đảm bảo Backend đã khởi động.');
      } else {
        setBanner('custom_error');
        setErrorMessage(serverMessage || 'Đăng nhập Google không thành công. Vui lòng thử lại.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  // -------------------------------------------------------------
  // REGISTER & OTP FLOW (Branch 2: feat/auth-register)
  // -------------------------------------------------------------
  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedEmail = regEmail.trim();
    const trimmedFullName = regFullName.trim();

    if (!trimmedFullName || !trimmedEmail || !regPassword) {
      setBanner('custom_error');
      setErrorMessage('Vui lòng điền đầy đủ Họ và tên, Email và Mật khẩu.');
      return;
    }

    if (regPassword !== regConfirmPassword) {
      setBanner('custom_error');
      setErrorMessage('Mật khẩu xác nhận không khớp với mật khẩu đã nhập.');
      return;
    }

    // Backend Regex validation: min 8, has lower, upper, digit, special char in [@$!%*?&]
    const strongPasswordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    if (!strongPasswordRegex.test(regPassword)) {
      setBanner('custom_error');
      setErrorMessage('Mật khẩu tối thiểu 8 ký tự, gồm ít nhất 1 chữ thường, 1 chữ hoa, 1 số và 1 ký tự đặc biệt (@$!%*?&).');
      return;
    }

    setIsLoading(true);
    setBanner(null);
    setErrorMessage(null);

    console.groupCollapsed(`%c[Auth Register] Đang tạo tài khoản: ${trimmedEmail}`, 'color: #0284c7; font-weight: bold;');
    console.log('[Bước 1/4] Kiểm tra dữ liệu đầu vào:', { email: trimmedEmail, fullName: trimmedFullName });
    console.log('[Bước 2/4] Đang gửi HTTP POST /api/auth/register tới Backend...');

    try {
      const authRes = await authService.register({
        email: trimmedEmail,
        password: regPassword,
      });

      console.log('[Bước 2/4] Đăng ký thành công (200 OK)! Nhận token:', authRes);

      const userModel: User = {
        id: authRes.userId,
        email: authRes.email,
        role: authRes.role,
        fullName: trimmedFullName || trimmedEmail.split('@')[0],
        avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
        isEmailVerified: false,
        isLocked: false,
        phone: '',
        passwordHash: '',
        locale: 'vi',
      };

      // Store temporary registration token in unauthenticated state for OTP endpoints
      setPendingUser(userModel);
      setPendingToken(authRes.accessToken);

      console.log('[Bước 3/4] Đang yêu cầu Backend phát sinh và gửi mã OTP (/api/auth/otp/send)...');
      try {
        await authService.sendVerificationOtp();
        console.log('[Bước 3/4] Mã OTP đã được sinh và gửi thành công (202 Accepted).');
      } catch (otpSendErr) {
        console.warn('[Bước 3/4] Cảnh báo gửi OTP bước đầu:', otpSendErr);
      }

      setOtpCooldown(60);
      setOtpSessionExpiresAt(Date.now() + 15 * 60 * 1000);
      setRegStep('otp');
      console.log('[Bước 4/4] Chuyển giao diện sang bước nhập mã OTP 6 chữ số.');
      console.groupEnd();
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
      console.groupEnd();
      console.error('[Auth Register] Đăng ký thất bại:', err);

      const status = err.response?.status;
      const code = err.response?.data?.code;

      if (status === 409 || code === 'EMAIL_ALREADY_USED') {
        setBanner('register_error');
      } else if (status === 400) {
        setBanner('custom_error');
        setErrorMessage(err.response?.data?.message || 'Dữ liệu đăng ký không hợp lệ. Vui lòng kiểm tra lại mật khẩu và email.');
      } else if (!err.response || err.code === 'ERR_NETWORK') {
        setBanner('network_error');
        setErrorMessage('Không thể kết nối đến máy chủ Backend (http://localhost:8080).');
      } else {
        setBanner('custom_error');
        setErrorMessage(err.response?.data?.message || 'Đăng ký không thành công. Vui lòng thử lại.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleResendOtp = async () => {
    if (otpCooldown > 0 || isLoading) return;
    setIsLoading(true);
    setBanner(null);
    setErrorMessage(null);
    setOtpCode(""); // Reset lại phần nhập OTP nếu người dùng bấm gửi lại mã

    console.groupCollapsed(`%c[Auth OTP] Yêu cầu gửi lại mã OTP tới: ${regEmail.trim()}`, 'color: #0284c7; font-weight: bold;');
    console.log('[Gửi lại OTP] Gửi HTTP POST /api/auth/otp/send...');
    try {
      await authService.sendVerificationOtp();
      console.log('[Gửi lại OTP] Thành công (202 Accepted)! Bắt đầu đếm ngược 60s.');
      setOtpCooldown(60);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
      console.error('[Gửi lại OTP] Thất bại:', err);
      const status = err.response?.status;
      if (status === 401) {
        setRegStep('form');
        setOtpCode('');
        setPendingUser(null);
        setOtpSessionExpiresAt(null);
        useAuthStore.getState().logout();
        setBanner('custom_error');
        setErrorMessage('Phiên xác thực đã hết hạn sau 15 phút. Vui lòng đăng ký lại.');
      } else if (status === 429) {
        setErrorMessage('Bạn thao tác quá nhanh. Vui lòng chờ kết thúc thời gian chờ 60 giây.');
        setBanner('custom_error');
      } else {
        setErrorMessage(err.response?.data?.message || 'Không thể gửi lại mã OTP lúc này. Vui lòng thử lại sau.');
        setBanner('custom_error');
      }
    } finally {
      setIsLoading(false);
      console.groupEnd();
    }
  };

  const handleVerifyOtpSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanOtp = otpCode.trim();
    if (cleanOtp.length !== 6) {
      setBanner('custom_error');
      setErrorMessage('Mã OTP phải bao gồm chính xác 6 chữ số.');
      return;
    }

    setIsLoading(true);
    setBanner(null);
    setErrorMessage(null);

    console.groupCollapsed(`%c[Auth OTP] Đang xác thực OTP: ${cleanOtp}`, 'color: #0284c7; font-weight: bold;');
    console.log('[Bước 1/4] Kiểm tra mã OTP 6 chữ số:', cleanOtp);
    console.log('[Bước 2/4] Đang gửi HTTP POST /api/auth/otp/verify tới Backend...');

    try {
      await authService.verifyOtp({ code: cleanOtp });
      console.log('[Bước 3/4] Backend xác thực OTP thành công (200 OK)!');

      // Update state: user is now fully verified and authenticated
      if (pendingUser) {
        setAuth({ ...pendingUser, isEmailVerified: true }, useAuthStore.getState().accessToken || '');
      }

      setToastMessage('Đăng ký & Kích hoạt thành công!');
      setShowSuccessToast(true);

      console.log('[Bước 4/4] Kích hoạt tài khoản hoàn tất! Điều hướng về trang chủ...');
      console.groupEnd();

      setTimeout(() => {
        navigate('/');
      }, 1200);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
      console.groupEnd();
      console.error('[Auth OTP] Xác thực OTP thất bại:', err);
      const status = err.response?.status;
      const code = err.response?.data?.code;

      if (status === 401) {
        setRegStep('form');
        setOtpCode('');
        setPendingUser(null);
        setOtpSessionExpiresAt(null);
        useAuthStore.getState().logout();
        setBanner('custom_error');
        setErrorMessage('Phiên xác thực đã hết hạn sau 15 phút. Vui lòng đăng ký lại.');
      } else if (status === 400 || code === 'INVALID_OTP') {
        setBanner('custom_error');
        setErrorMessage('Mã OTP không chính xác hoặc đã hết hạn (tối đa 5 lần thử). Vui lòng thử lại.');
      } else {
        setBanner('custom_error');
        setErrorMessage(err.response?.data?.message || 'Xác thực OTP thất bại. Vui lòng thử lại.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="w-full min-h-screen flex flex-col justify-center items-center relative overflow-hidden px-4 pt-24 pb-10 sm:pt-28 sm:pb-14">
      {/* Dynamic Auto-Rotating Coastal Scene Backdrop */}
      <AuthCinematicBackdrop autoRotateInterval={5500} />

      {/* Solid Green Success Toast */}
      {showSuccessToast && (
        <aside
          role="status"
          aria-live="polite"
          className="fixed top-6 right-6 z-[9999] px-6 py-3 rounded-xl bg-green-600 text-white shadow-[0_12px_32px_rgba(22,163,74,0.45)] animate-toast-slide-in flex items-center gap-2.5 pointer-events-auto border border-white/20"
          style={{ backgroundColor: '#16a34a' }}
        >
          <span className="material-symbols-outlined text-[20px] text-white" style={{ fontVariationSettings: "'FILL' 1" }}>
            check_circle
          </span>
          <span className="text-sm font-semibold tracking-wide text-white">
            {toastMessage}
          </span>
        </aside>
      )}

      <div className="w-full max-w-[1180px] grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-10 items-center relative z-10 my-auto">
        {/* Left Column: Coastal Brand Messaging */}
        <div className="lg:col-span-5 hidden lg:flex flex-col space-y-3">
          <h1 className="text-3xl xl:text-4xl font-extrabold text-white leading-tight drop-shadow-[0_4px_16px_rgba(0,0,0,0.8)]">
            Khám phá đại dương cùng <span className="text-cyan-400">DANASEA</span>
          </h1>
          <p className="text-sm xl:text-base text-slate-200 leading-relaxed max-w-md drop-shadow-[0_2px_8px_rgba(0,0,0,0.8)]">
            Cổng xác thực tập trung dành cho du khách trải nghiệm cano, lặn biển ngắm san hô Bán đảo Sơn Trà và nền tảng trải nghiệm &amp; thể thao biển Đà Nẵng.
          </p>
        </div>
        
        {/* Right Column: Auth Form Card */}
        <div className="lg:col-span-7 flex justify-center lg:justify-end w-full lg:pr-4">
          <div className="w-full max-w-md bg-surface-container-lowest/95 backdrop-blur-2xl rounded-2xl shadow-[0_20px_50px_rgba(0,0,0,0.35)] border border-white/30 p-5 sm:p-6 space-y-3.5">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-primary-container flex items-center justify-center text-on-primary shadow-sm">
                <span className="material-symbols-outlined text-[18px]" style={{ fontVariationSettings: "'FILL' 1" }}>waves</span>
              </div>
              <span className="text-lg text-on-surface tracking-tight font-bold">DANA<span className="text-primary-container">SEA</span></span>
            </div>
            
            {/* Tab Switcher (Login / Register) */}
            <div className="relative p-1 bg-surface-container rounded-xl flex items-center">
              <button 
                type="button"
                className={`relative z-10 w-1/2 py-1.5 rounded-lg text-xs sm:text-sm font-semibold transition-all duration-200 flex items-center justify-center gap-1.5 ${activeTab === "login" ? "text-primary-container bg-surface-container-lowest shadow-sm" : "text-on-surface-variant hover:text-on-surface bg-transparent"}`} 
                onClick={() => { setActiveTab("login"); setBanner(null); setErrorMessage(null); }}
              >
                <span className="material-symbols-outlined text-[17px]">login</span>
                Đăng nhập
              </button>
              <button 
                type="button"
                className={`relative z-10 w-1/2 py-1.5 rounded-lg text-xs sm:text-sm font-semibold transition-all duration-200 flex items-center justify-center gap-1.5 ${activeTab === "register" ? "text-primary-container bg-surface-container-lowest shadow-sm" : "text-on-surface-variant hover:text-on-surface bg-transparent"}`} 
                onClick={() => { setActiveTab("register"); setBanner(null); setErrorMessage(null); }}
              >
                <span className="material-symbols-outlined text-[17px]">person_add</span>
                Tạo tài khoản mới
              </button>
            </div>
            
            {/* Banner Messages */}
            {banner && (
              <div className="space-y-2">
                {banner === 'locked' && (
                  <div className="p-2.5 rounded-xl bg-error-container text-on-error-container flex items-start gap-2">
                    <span className="material-symbols-outlined text-[18px] text-error flex-shrink-0 mt-0.5">lock_clock</span>
                    <div>
                      <p className="text-xs font-bold">Tài khoản tạm thời bị khóa</p>
                      <p className="text-[11px] mt-0.5">Vui lòng liên hệ ban quản trị để được hỗ trợ mở khóa tài khoản.</p>
                    </div>
                  </div>
                )}
                {banner === 'wrong_creds' && (
                  <div className="p-2.5 rounded-xl bg-error-container text-on-error-container flex items-start gap-2">
                    <span className="material-symbols-outlined text-[18px] text-error flex-shrink-0 mt-0.5">error</span>
                    <p className="text-xs font-medium">Email hoặc mật khẩu không chính xác. Vui lòng kiểm tra lại thông tin.</p>
                  </div>
                )}
                {banner === 'register_error' && (
                  <div className="p-2.5 rounded-xl bg-error-container text-on-error-container flex items-start gap-2">
                    <span className="material-symbols-outlined text-[18px] text-error flex-shrink-0 mt-0.5">error</span>
                    <div>
                      <p className="text-xs font-bold">Email này đã được sử dụng</p>
                      <p className="text-[11px] mt-0.5">Địa chỉ email đã tồn tại trong hệ sinh thái DANASEA. Vui lòng đăng nhập hoặc dùng email khác.</p>
                    </div>
                  </div>
                )}
                {banner === 'network_error' && (
                  <div className="p-2.5 rounded-xl bg-error-container text-on-error-container flex items-start gap-2">
                    <span className="material-symbols-outlined text-[18px] text-error flex-shrink-0 mt-0.5">cloud_off</span>
                    <div>
                      <p className="text-xs font-bold">Không thể kết nối đến máy chủ Backend</p>
                      <p className="text-[11px] mt-0.5">{errorMessage || 'Vui lòng kiểm tra Docker và Backend Spring Boot (cổng 8080) đã được khởi động chưa.'}</p>
                    </div>
                  </div>
                )}
                {banner === 'custom_error' && (
                  <div className="p-2.5 rounded-xl bg-error-container text-on-error-container">
                    <p className="text-xs font-medium">{errorMessage}</p>
                  </div>
                )}
              </div>
            )}

            {/* TAB 1: LOGIN */}
            {activeTab === "login" && (
              <div className="space-y-3 block">
                <form className="space-y-2.5" onSubmit={handleLoginSubmit}>
                  <div className="space-y-1">
                    <label className="text-xs font-medium text-on-surface">
                      Địa chỉ Email hoặc Tên tài khoản
                    </label>
                    <div className="relative flex items-center">
                      <span className="material-symbols-outlined absolute left-3.5 text-outline text-[18px] pointer-events-none">mail</span>
                      <input 
                        value={email} 
                        onChange={e => setEmail(e.target.value)} 
                        disabled={isLoading}
                        className="w-full h-[42px] pl-10 pr-3 rounded-xl bg-surface-container-lowest text-on-surface text-sm shadow-sm outline-none border border-outline-variant/30 focus:border-primary focus:shadow-md transition-all placeholder:text-outline-variant disabled:opacity-60" 
                        placeholder="Nhập email (vd: customer@danasea.vn)" 
                        required 
                        type="text" 
                      />
                    </div>
                  </div>
                  
                  <div className="space-y-1">
                    <div className="flex items-center justify-between">
                      <label className="text-xs font-medium text-on-surface">Mật khẩu</label>
                      <a className="text-[11px] text-primary-container hover:underline cursor-pointer" onClick={() => navigate('/auth/forgot-password')}>
                        Quên mật khẩu?
                      </a>
                    </div>
                    <div className="relative flex items-center">
                      <span className="material-symbols-outlined absolute left-3.5 text-outline text-[18px] pointer-events-none">lock</span>
                      <input 
                        value={password} 
                        onChange={e => setPassword(e.target.value)} 
                        disabled={isLoading}
                        className="w-full h-[42px] pl-10 pr-11 rounded-xl bg-surface-container-lowest text-on-surface text-sm shadow-sm outline-none border border-outline-variant/30 focus:border-primary focus:shadow-md transition-all placeholder:text-outline-variant disabled:opacity-60" 
                        placeholder="Nhập mật khẩu" 
                        required 
                        type={showPassword ? "text" : "password"} 
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(prev => !prev)}
                        className="absolute right-3 p-1 text-outline hover:text-on-surface transition-colors flex items-center justify-center cursor-pointer focus:outline-none"
                        tabIndex={-1}
                        title={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                      >
                        <span className="material-symbols-outlined text-[20px]">
                          {showPassword ? "visibility_off" : "visibility"}
                        </span>
                      </button>
                    </div>
                  </div>
                  
                  <button 
                    className="w-full h-[44px] rounded-xl bg-primary-container text-on-primary text-sm font-semibold flex items-center justify-center gap-2 shadow-md hover:bg-primary transition-all duration-200 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed mt-1" 
                    type="submit"
                    disabled={isLoading}
                  >
                    {isLoading ? (
                      <>
                        <span className="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                        <span>Đang đăng nhập...</span>
                      </>
                    ) : (
                      <span>Đăng nhập</span>
                    )}
                  </button>
                </form>

                {/* Divider */}
                <div className="relative my-3 flex items-center">
                  <div className="flex-1 border-t border-outline-variant/30" />
                  <span className="flex-shrink-0 px-3 text-[11px] text-outline font-medium uppercase tracking-wider whitespace-nowrap">
                    hoặc tiếp tục với
                  </span>
                  <div className="flex-1 border-t border-outline-variant/30" />
                </div>

                {/* Google Sign In */}
                <GoogleLoginButton
                  mode="login"
                  isLoading={isLoading}
                  disabled={isLoading}
                  onSuccess={handleGoogleLoginSuccess}
                  onError={(msg) => {
                    setBanner('custom_error');
                    setErrorMessage(msg);
                  }}
                />
                
                <div className="pt-1 text-center">
                  <p className="text-xs text-on-surface-variant">
                    Chưa có tài khoản tham gia trải nghiệm? 
                    <button type="button" className="text-primary-container font-semibold hover:underline bg-transparent border-0 cursor-pointer ml-1" onClick={() => { setActiveTab("register"); setRegStep("form"); setBanner(null); setErrorMessage(null); }}>Đăng ký thành viên ngay</button>
                  </p>
                </div>
              </div>
            )}

            {/* TAB 2: REGISTER */}
            {activeTab === "register" && (
              <div className="space-y-3">
                {/* SUB-STEP 1: Registration Form */}
                {regStep === "form" && (
                  <>
                    <div className="space-y-0.5">
                      <h3 className="text-sm font-bold text-on-surface">Đăng ký tài khoản du khách &amp; đối tác</h3>
                    </div>

                    <form className="space-y-2.5" onSubmit={handleRegisterSubmit}>
                      <div className="space-y-1">
                        <label className="text-xs font-medium text-on-surface">
                          Họ và tên
                        </label>
                        <input 
                          value={regFullName}
                          onChange={e => setRegFullName(e.target.value)}
                          disabled={isLoading}
                          required
                          className="w-full h-[40px] px-3.5 rounded-xl bg-surface-container-lowest text-on-surface text-sm shadow-sm outline-none border border-outline-variant/30 focus:border-primary focus:shadow-md transition-all placeholder:text-outline-variant disabled:opacity-60" 
                          placeholder="Ví dụ: Nguyễn Hải Đăng" 
                          type="text" 
                        />
                      </div>

                      <div className="space-y-1">
                        <label className="text-xs font-medium text-on-surface">
                          Địa chỉ Email
                        </label>
                        <input 
                          value={regEmail}
                          onChange={e => setRegEmail(e.target.value)}
                          disabled={isLoading}
                          className="w-full h-[40px] px-3.5 rounded-xl bg-surface-container-lowest text-on-surface text-sm shadow-sm outline-none border border-outline-variant/30 focus:border-primary focus:shadow-md transition-all placeholder:text-outline-variant disabled:opacity-60" 
                          placeholder="ban@email.com" 
                          required 
                          type="email" 
                        />
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                        <div className="space-y-1">
                          <label className="text-xs font-medium text-on-surface">
                            Mật khẩu
                          </label>
                          <div className="relative flex items-center">
                            <input 
                              value={regPassword}
                              onChange={e => setRegPassword(e.target.value)}
                              disabled={isLoading}
                              className="w-full h-[40px] pl-3 pr-9 rounded-xl bg-surface-container-lowest text-on-surface text-xs sm:text-sm shadow-sm outline-none border border-outline-variant/30 focus:border-primary focus:shadow-md transition-all placeholder:text-outline-variant disabled:opacity-60" 
                              placeholder="Tối thiểu 8 ký tự" 
                              required 
                              type={showRegPassword ? "text" : "password"} 
                            />
                            <button
                              type="button"
                              onClick={() => setShowRegPassword(prev => !prev)}
                              className="absolute right-2 p-1 text-outline hover:text-on-surface transition-colors flex items-center justify-center cursor-pointer focus:outline-none"
                              tabIndex={-1}
                              title={showRegPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                            >
                              <span className="material-symbols-outlined text-[18px]">
                                {showRegPassword ? "visibility_off" : "visibility"}
                              </span>
                            </button>
                          </div>
                        </div>

                        <div className="space-y-1">
                          <label className="text-xs font-medium text-on-surface">
                            Nhập lại
                          </label>
                          <div className="relative flex items-center">
                            <input 
                              value={regConfirmPassword}
                              onChange={e => setRegConfirmPassword(e.target.value)}
                              disabled={isLoading}
                              className="w-full h-[40px] pl-3 pr-9 rounded-xl bg-surface-container-lowest text-on-surface text-xs sm:text-sm shadow-sm outline-none border border-outline-variant/30 focus:border-primary focus:shadow-md transition-all placeholder:text-outline-variant disabled:opacity-60" 
                              placeholder="Xác nhận lại" 
                              required 
                              type={showRegConfirmPassword ? "text" : "password"} 
                            />
                            <button
                              type="button"
                              onClick={() => setShowRegConfirmPassword(prev => !prev)}
                              className="absolute right-2 p-1 text-outline hover:text-on-surface transition-colors flex items-center justify-center cursor-pointer focus:outline-none"
                              tabIndex={-1}
                              title={showRegConfirmPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                            >
                              <span className="material-symbols-outlined text-[18px]">
                                {showRegConfirmPassword ? "visibility_off" : "visibility"}
                              </span>
                            </button>
                          </div>
                        </div>
                      </div>

                      {/* Password Requirements Guidance */}
                      <p className="text-[10px] text-on-surface-variant flex items-center gap-1">
                        <span className="material-symbols-outlined text-[13px] text-primary flex-shrink-0">info</span>
                        <span>Mật khẩu 8+ ký tự, gồm chữ Hoa, Thường, Số và Ký tự đặc biệt (@$!%*?&).</span>
                      </p>

                      <button 
                        className="w-full h-[42px] rounded-xl bg-primary-container text-on-primary text-sm font-semibold flex items-center justify-center gap-2 hover:bg-primary transition-all duration-200 cursor-pointer shadow-md mt-1 disabled:opacity-60 disabled:cursor-not-allowed" 
                        type="submit"
                        disabled={isLoading}
                      >
                        {isLoading ? (
                          <>
                            <span className="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                            <span>Đang xử lý đăng ký...</span>
                          </>
                        ) : (
                          <span>Đăng ký</span>
                        )}
                      </button>
                    </form>

                    {/* Divider */}
                    <div className="relative my-3 flex items-center">
                      <div className="flex-1 border-t border-outline-variant/30" />
                      <span className="flex-shrink-0 px-3 text-[11px] text-outline font-medium uppercase tracking-wider whitespace-nowrap">
                        hoặc đăng ký bằng
                      </span>
                      <div className="flex-1 border-t border-outline-variant/30" />
                    </div>

                    {/* Google Sign In */}
                    <GoogleLoginButton
                      mode="register"
                      isLoading={isLoading}
                      disabled={isLoading}
                      onSuccess={handleGoogleLoginSuccess}
                      onError={(msg) => {
                        setBanner('custom_error');
                        setErrorMessage(msg);
                      }}
                    />

                    <div className="pt-0.5 text-center">
                      <p className="text-xs text-on-surface-variant">
                        Đã có tài khoản DANASEA? 
                        <button type="button" className="text-primary-container font-semibold hover:underline bg-transparent border-0 cursor-pointer ml-1" onClick={() => { setActiveTab("login"); setBanner(null); setErrorMessage(null); }}>Đăng nhập ngay</button>
                      </p>
                    </div>
                  </>
                )}

                {/* SUB-STEP 2: OTP Verification */}
                {regStep === "otp" && (
                  <div className="space-y-3">
                    <div className="text-center space-y-1">
                      <div className="w-10 h-10 rounded-full bg-cyan-100 dark:bg-cyan-900/40 text-cyan-600 dark:text-cyan-400 mx-auto flex items-center justify-center">
                        <span className="material-symbols-outlined text-[22px]">mark_email_read</span>
                      </div>
                      <h3 className="text-sm font-bold text-on-surface">Kích hoạt tài khoản</h3>
                      <p className="text-xs text-on-surface-variant leading-relaxed">
                        Mã xác thực OTP (6 chữ số) đã được gửi tới địa chỉ: <br />
                        <span className="font-semibold text-primary-container">{regEmail}</span>
                      </p>
                    </div>

                    <form className="space-y-3" onSubmit={handleVerifyOtpSubmit}>
                      <div className="space-y-1.5">
                        <label className="text-xs font-medium text-on-surface text-center block">
                          Nhập mã xác thực 6 chữ số
                        </label>
                        <input 
                          value={otpCode}
                          onChange={e => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                          disabled={isLoading}
                          maxLength={6}
                          autoFocus
                          className="w-full h-[46px] text-center font-mono text-xl font-bold tracking-[0.45em] rounded-xl bg-surface-container-lowest text-on-surface shadow-sm outline-none border border-outline-variant/40 focus:border-primary focus:shadow-md transition-all placeholder:text-outline-variant/40 placeholder:tracking-normal disabled:opacity-60" 
                          placeholder="••••••" 
                          required 
                          type="text" 
                        />
                      </div>

                      {/* Resend OTP Action & 60s Cooldown Countdown */}
                      <div className="flex items-center justify-between text-xs pt-0.5">
                        <span className="text-on-surface-variant">Chưa nhận được mã?</span>
                        {otpCooldown > 0 ? (
                          <span className="font-medium text-outline flex items-center gap-1 font-mono">
                            <span className="material-symbols-outlined text-[14px]">timer</span>
                            Gửi lại sau ({otpCooldown}s)
                          </span>
                        ) : (
                          <button
                            type="button"
                            disabled={isLoading}
                            onClick={handleResendOtp}
                            className="text-primary-container font-semibold hover:underline bg-transparent border-0 cursor-pointer disabled:opacity-50"
                          >
                            Gửi lại mã OTP
                          </button>
                        )}
                      </div>

                      <button 
                        className="w-full h-[42px] rounded-xl bg-primary-container text-on-primary text-sm font-semibold flex items-center justify-center gap-2 hover:bg-primary transition-all duration-200 cursor-pointer shadow-md disabled:opacity-60 disabled:cursor-not-allowed" 
                        type="submit"
                        disabled={isLoading || otpCode.length !== 6}
                      >
                        {isLoading ? (
                          <>
                            <span className="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                            <span>Đang xác nhận...</span>
                          </>
                        ) : (
                          <span>Xác nhận</span>
                        )}
                      </button>

                      <div className="text-center pt-1">
                        <button
                          type="button"
                          onClick={() => {
                            setRegStep("form");
                            setOtpCode("");
                            setPendingUser(null);
                            setOtpSessionExpiresAt(null);
                            useAuthStore.getState().logout();
                            setBanner(null);
                            setErrorMessage(null);
                          }}
                          className="text-xs text-outline hover:text-on-surface transition-colors bg-transparent border-0 cursor-pointer flex items-center justify-center gap-1 mx-auto"
                        >
                          <span className="material-symbols-outlined text-[15px]">arrow_back</span>
                          <span>Đổi địa chỉ email khác</span>
                        </button>
                      </div>
                    </form>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}

