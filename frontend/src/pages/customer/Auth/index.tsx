import type { HttpRequestError } from "../../../types/http";
import { useState, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuthStore } from "../../../store/useAuthStore";
import { authService } from "../../../services/authService";
import { AuthCinematicBackdrop } from "../../../components/customer/AuthCinematicBackdrop";
import { GoogleLoginButton } from "../../../components/customer/GoogleLoginButton";
import type { User, Role } from "../../../types";
import {
  LogIn,
  Lock,
  Mail,
  Eye,
  EyeOff,
  CheckCircle2,
  AlertCircle,
  WifiOff,
  Timer,
  ArrowLeft,
  Loader2,
  MailCheck,
  Info,
} from "lucide-react";

const DEFAULT_AVATARS: Record<Role, string> = {
  ADMIN: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150',
  VENDOR: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150',
  CUSTOMER: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
};

export function Auth() {
  const [activeTab, setActiveTab] = useState<"login" | "register" | "forgot_password">("login");
  
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

  // In-Place Forgot Password States (Facebook-style recovery)
  const [forgotStep, setForgotStep] = useState<"email" | "otp" | "success">("email");
  const [forgotEmail, setForgotEmail] = useState("");
  const [forgotOtp, setForgotOtp] = useState("");
  const [forgotNewPass, setForgotNewPass] = useState("");
  const [forgotConfirmPass, setForgotConfirmPass] = useState("");
  const [showForgotNewPass, setShowForgotNewPass] = useState(false);
  const [showForgotConfirmPass, setShowForgotConfirmPass] = useState(false);
  const [forgotCooldown, setForgotCooldown] = useState(0);

  // General Feedback States
  const [banner, setBanner] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [showSuccessToast, setShowSuccessToast] = useState(false);
  const [toastMessage, setToastMessage] = useState("Đăng nhập thành công");
  
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    // Clean residual query parameters (e.g. lingering reason or email from previous session)
    if (searchParams.has('reason') || searchParams.has('email')) {
      const nextParams = new URLSearchParams(searchParams);
      nextParams.delete('reason');
      nextParams.delete('email');
      const newQuery = nextParams.toString();
      const cleanUrl = window.location.pathname + (newQuery ? `?${newQuery}` : '');
      window.history.replaceState({}, '', cleanUrl);
    }
  }, [searchParams]);
  const setAuth = useAuthStore(state => state.setAuth);
  const setPendingToken = useAuthStore(state => state.setPendingToken);

  // 60-second Registration OTP cooldown ticker
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    if (otpCooldown > 0) {
      timer = setTimeout(() => setOtpCooldown(prev => prev - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [otpCooldown]);

  // 60-second Forgot Password OTP cooldown ticker
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    if (forgotCooldown > 0) {
      timer = setTimeout(() => setForgotCooldown(prev => prev - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [forgotCooldown]);

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

  // Password criteria checking for forgot password
  const forgotHasMinLength = forgotNewPass.length >= 8;
  const forgotHasUppercase = /[A-Z]/.test(forgotNewPass);
  const forgotHasNumber = /[0-9]/.test(forgotNewPass);
  const forgotHasSpecial = /[@$!%*?&]/.test(forgotNewPass);
  const forgotCriteriaCount = [forgotHasMinLength, forgotHasUppercase, forgotHasNumber, forgotHasSpecial].filter(Boolean).length;

  // -------------------------------------------------------------
  // LOGIN FLOW
  // -------------------------------------------------------------
  const executeLogin = async (emailInput: string, passwordInput: string) => {
    const trimmedEmail = emailInput.trim();
    if (!trimmedEmail || !passwordInput) {
      setBanner('wrong_creds');
      setErrorMessage('Vui lòng nhập đầy đủ email và mật khẩu.');
      return;
    }

    // Check if this account is registered as locked in system
    let isLocallyLocked = false;
    let lockReasonText = '';
    try {
      const lockedMap = JSON.parse(localStorage.getItem('danasea_locked_users') || '{}');
      const lower = trimmedEmail.toLowerCase();
      if (lockedMap[lower]) {
        isLocallyLocked = true;
        lockReasonText = lockedMap[lower].reason || 'Tài khoản đang bị tạm ngưng bởi Ban Quản trị DANASEA';
      }
    } catch (err) { console.debug("[Auth] Local storage check ignored:", err); }

    if (isLocallyLocked) {
      setBanner('locked');
      setErrorMessage(
        `Tài khoản (${trimmedEmail}) hiện đang bị KHÓA bởi Ban Quản trị DANASEA. Lý do: ${lockReasonText}. Vui lòng liên hệ bộ phận hỗ trợ (hotline: 0905 112 233) để được giải quyết.`
      );
      return;
    }

    setIsLoading(true);
    setBanner(null);
    setErrorMessage(null);

    try {
      const authRes = await authService.login({
        email: trimmedEmail,
        password: passwordInput,
      });

      const role = authRes.role;
      const avatarUrl = DEFAULT_AVATARS[role] || DEFAULT_AVATARS.CUSTOMER;

      const userModel: User = {
        id: authRes.userId,
        email: authRes.email,
        role: role,
        fullName: trimmedEmail === 'customer_1@danasea.test'
          ? 'Nguyễn Hoàng Long'
          : trimmedEmail === 'customer_2@danasea.test'
          ? 'Trần Thị Mai Anh'
          : trimmedEmail === 'admin_demo@danasea.test'
          ? 'Quản trị viên Hệ thống DANASEA'
          : trimmedEmail.split('@')[0],
        avatarUrl: avatarUrl,
        isEmailVerified: true,
        isLocked: false,
        phone: '',
        passwordHash: '',
        locale: 'vi',
      };

      setAuth(userModel, authRes.accessToken);

      const destination = role === 'ADMIN' ? '/admin' : role === 'VENDOR' ? '/vendor' : '/';
      setToastMessage("Đăng nhập thành công");
      setShowSuccessToast(true);

      navigate(destination);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
      console.error('[Auth Flow] Lỗi đăng nhập thất bại:', err);

      const status = err.response?.status;
      const serverMessage = err.response?.data?.message || err.message;

      if (status === 401) {
        if (isLocallyLocked) {
          setBanner('locked');
          setErrorMessage(
            `Tài khoản (${trimmedEmail}) đã bị khóa bởi Quản trị viên. Hệ thống từ chối đăng nhập cho đến khi tài khoản được mở khóa.`
          );
        } else {
          setBanner('wrong_creds');
          setErrorMessage(
            'Email hoặc mật khẩu không chính xác. Vui lòng kiểm tra lại thông tin.'
          );
        }
      } else if (status === 403) {
        const errCode = (err.response?.data as { code?: string })?.code;
        if (errCode === 'EMAIL_NOT_VERIFIED') {
          console.warn('[Auth Flow] Tài khoản chưa kích hoạt email (403 Forbidden - EMAIL_NOT_VERIFIED)');
          const resData = err.response?.data as { accessToken?: string; email?: string; userId?: string };
          if (resData?.accessToken) {
            setPendingToken(resData.accessToken);
          }
          const userModel: User = {
            id: resData?.userId || '',
            email: resData?.email || trimmedEmail,
            role: 'CUSTOMER',
            fullName: (resData?.email || trimmedEmail).split('@')[0],
            avatarUrl: DEFAULT_AVATARS.CUSTOMER,
            isEmailVerified: false,
            isLocked: false,
            phone: '',
            passwordHash: '',
            locale: 'vi',
          };
          setPendingUser(userModel);
          setRegEmail(resData?.email || trimmedEmail);
          setActiveTab('register');
          setRegStep('otp');
          setOtpCooldown(60);
          setOtpSessionExpiresAt(Date.now() + 15 * 60 * 1000);
          authService.sendVerificationOtp().catch((otpErr) => {
            console.warn('[Auth Flow] Tự động gửi lại OTP thất bại:', otpErr);
          });
          return;
        }
        const isLocked = isLocallyLocked ||
          errCode === 'USER_LOCKED' ||
          serverMessage?.toLowerCase().includes('khóa') ||
          serverMessage?.toLowerCase().includes('locked') ||
          serverMessage?.toLowerCase().includes('bị vô hiệu');
        if (isLocked) {
          setBanner('locked');
          setErrorMessage(
            `Tài khoản (${trimmedEmail}) đã bị khóa bởi Quản trị viên. Hệ thống từ chối đăng nhập cho đến khi tài khoản được mở khóa.`
          );
        } else {
          setBanner('custom_error');
          setErrorMessage(
            serverMessage || 'Tài khoản của bạn đã bị khóa hoặc không có quyền truy cập.'
          );
        }
      } else if (!err.response || err.code === 'ERR_NETWORK') {
        setBanner('network_error');
        setErrorMessage('Không thể kết nối đến máy chủ Backend (http://localhost:8080). Hãy đảm bảo Backend đã khởi động.');
      } else {
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
      return;
    }

    setIsLoading(true);
    setBanner(null);
    setErrorMessage(null);

    try {
      const authRes = await authService.loginWithGoogle(credential);

      let userModel: User;
      try {
        useAuthStore.getState().setPendingToken(authRes.accessToken);
        const profile = await authService.getMyProfile();
        userModel = {
          id: profile.id,
          email: profile.email,
          role: profile.role,
          fullName: profile.fullName || authRes.email.split('@')[0],
          avatarUrl: profile.avatarUrl || DEFAULT_AVATARS[profile.role] || DEFAULT_AVATARS.CUSTOMER,
          isEmailVerified: profile.isEmailVerified,
          isLocked: false,
          phone: '',
          passwordHash: '',
          locale: profile.locale || 'vi',
        };
      } catch {
        userModel = {
          id: authRes.userId,
          email: authRes.email,
          role: authRes.role,
          fullName: authRes.email.split('@')[0],
          avatarUrl: DEFAULT_AVATARS[authRes.role] || DEFAULT_AVATARS.CUSTOMER,
          isEmailVerified: true,
          isLocked: false,
          phone: '',
          passwordHash: '',
          locale: 'vi',
        };
      }

      setAuth(userModel, authRes.accessToken);

      const destination = userModel.role === 'ADMIN' ? '/admin' : userModel.role === 'VENDOR' ? '/vendor' : '/';
      setToastMessage("Đăng nhập Google thành công");
      setShowSuccessToast(true);

      setTimeout(() => {
        navigate(destination);
      }, 1200);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
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
  // REGISTER & OTP FLOW
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

    const strongPasswordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    if (!strongPasswordRegex.test(regPassword)) {
      setBanner('custom_error');
      setErrorMessage('Mật khẩu tối thiểu 8 ký tự, gồm ít nhất 1 chữ thường, 1 chữ hoa, 1 số và 1 ký tự đặc biệt (@$!%*?&).');
      return;
    }

    setIsLoading(true);
    setBanner(null);
    setErrorMessage(null);

    try {
      const authRes = await authService.register({
        email: trimmedEmail,
        password: regPassword,
      });

      const userModel: User = {
        id: authRes.userId,
        email: authRes.email,
        role: authRes.role,
        fullName: trimmedFullName || trimmedEmail.split('@')[0],
        avatarUrl: DEFAULT_AVATARS[authRes.role] || DEFAULT_AVATARS.CUSTOMER,
        isEmailVerified: false,
        isLocked: false,
        phone: '',
        passwordHash: '',
        locale: 'vi',
      };

      setPendingUser(userModel);
      setPendingToken(authRes.accessToken);

      try {
        await authService.sendVerificationOtp();
      } catch (otpSendErr) {
        console.warn('[Auth Register] Cảnh báo gửi OTP bước đầu:', otpSendErr);
      }

      setOtpCooldown(60);
      setOtpSessionExpiresAt(Date.now() + 15 * 60 * 1000);
      setRegStep('otp');
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
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
    setOtpCode("");

    try {
      await authService.sendVerificationOtp();
      setOtpCooldown(60);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
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

    try {
      await authService.verifyOtp({ code: cleanOtp });

      if (pendingUser) {
        setAuth({ ...pendingUser, isEmailVerified: true }, useAuthStore.getState().accessToken || '');
      }

      setToastMessage('Đăng ký & Kích hoạt thành công!');
      setShowSuccessToast(true);

      setTimeout(() => {
        navigate('/');
      }, 1200);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
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

  // -------------------------------------------------------------
  // IN-PLACE FORGOT PASSWORD FLOW (Facebook/Google Pattern)
  // -------------------------------------------------------------
  const handleForgotRequestSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanEmail = forgotEmail.trim();
    if (!cleanEmail) {
      setBanner('custom_error');
      setErrorMessage('Vui lòng nhập địa chỉ email tài khoản.');
      return;
    }

    setIsLoading(true);
    setBanner(null);
    setErrorMessage(null);

    try {
      await authService.forgotPassword(cleanEmail);
      setForgotStep('otp');
      setForgotCooldown(60);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
      console.error('[Auth ForgotPassword] Gửi yêu cầu thất bại:', err);
      const status = err.response?.status;
      if (status === 429) {
        setBanner('custom_error');
        setErrorMessage('Bạn đã gửi yêu cầu quá nhanh. Vui lòng chờ 1 phút trước khi thử lại.');
      } else {
        setBanner('custom_error');
        setErrorMessage(err.response?.data?.message || 'Không thể gửi yêu cầu đặt lại mật khẩu lúc này. Vui lòng thử lại sau.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleForgotResendOtp = async () => {
    if (forgotCooldown > 0 || isLoading) return;
    setIsLoading(true);
    setBanner(null);
    setErrorMessage(null);
    setForgotOtp('');

    try {
      await authService.forgotPassword(forgotEmail.trim());
      setForgotCooldown(60);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
      console.error('[Auth ForgotPassword] Gửi lại mã thất bại:', err);
      const status = err.response?.status;
      if (status === 429) {
        setBanner('custom_error');
        setErrorMessage('Vui lòng đợi kết thúc thời gian chờ 60 giây.');
      } else {
        setBanner('custom_error');
        setErrorMessage(err.response?.data?.message || 'Không thể gửi lại mã OTP. Vui lòng thử lại sau.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleResetPasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBanner(null);
    setErrorMessage(null);

    const cleanOtp = forgotOtp.trim();
    if (!/^\d{6}$/.test(cleanOtp)) {
      setBanner('custom_error');
      setErrorMessage('Mã OTP phải bao gồm chính xác 6 chữ số.');
      return;
    }

    if (forgotCriteriaCount < 4) {
      setBanner('custom_error');
      setErrorMessage('Mật khẩu mới chưa đáp ứng đầy đủ 4 tiêu chí an toàn bắt buộc.');
      return;
    }

    if (forgotNewPass !== forgotConfirmPass) {
      setBanner('custom_error');
      setErrorMessage('Mật khẩu xác nhận không khớp với mật khẩu mới.');
      return;
    }

    setIsLoading(true);

    try {
      await authService.resetPassword({
        email: forgotEmail.trim(),
        otp: cleanOtp,
        newPassword: forgotNewPass,
      });

      setForgotStep('success');
      setToastMessage('Đặt lại mật khẩu thành công!');
      setShowSuccessToast(true);
    } catch (caught: unknown) {
      const err = caught as HttpRequestError;
      console.error('[Auth ForgotPassword] Đặt lại mật khẩu thất bại:', err);
      const status = err.response?.status;
      const code = err.response?.data?.code;

      if (status === 429) {
        setBanner('custom_error');
        setErrorMessage('Bạn đã thao tác quá nhiều lần. Vui lòng chờ ít phút.');
      } else if (code === 'AUTH_008' || err.response?.data?.message?.includes('AUTH_008')) {
        setBanner('custom_error');
        setErrorMessage('Mã OTP đã bị hủy do nhập sai quá 5 lần. Vui lòng yêu cầu mã mới.');
        setForgotStep('email');
      } else if (code === 'AUTH_007' || err.response?.data?.message?.includes('AUTH_007')) {
        setBanner('custom_error');
        setErrorMessage('Mã OTP không chính xác hoặc đã hết hạn (tối đa 5 lần thử).');
      } else {
        setBanner('custom_error');
        setErrorMessage(err.response?.data?.message || 'Đặt lại mật khẩu thất bại. Vui lòng thử lại.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="w-full min-h-screen flex items-center justify-center relative px-4 pt-24 pb-12 sm:pt-28 sm:pb-16 overflow-y-auto">
      {/* Dynamic Auto-Rotating Coastal Scene Backdrop with Dark Cinematic Scrim */}
      <AuthCinematicBackdrop autoRotateInterval={6500} />

      {/* Solid Green Success Toast */}
      {showSuccessToast && (
        <aside
          role="status"
          aria-live="polite"
          className="fixed top-6 right-6 z-[9999] px-5 py-3 rounded-xl bg-emerald-600 text-white shadow-lg animate-toast-slide-in flex items-center gap-2.5 pointer-events-auto border border-white/20"
        >
          <CheckCircle2 className="w-5 h-5 text-white" />
          <span className="text-sm font-semibold tracking-wide text-white">
            {toastMessage}
          </span>
        </aside>
      )}

      {/* Focused Center Auth Card */}
      <div className="w-full max-w-[440px] relative z-10 mx-auto my-auto">
        <div className="w-full bg-white rounded-2xl shadow-2xl shadow-slate-950/40 border border-slate-200/90 p-6 sm:p-7 space-y-4">
          {/* Top Brand & Context Header */}
          <div className="flex items-center justify-between pb-1">
            <div className="flex items-center gap-2.5">
              <img
                alt="DANASEA"
                width={32}
                height={32}
                fetchPriority="high"
                decoding="async"
                className="h-8 w-8 object-contain shrink-0"
                src="/images/danasea-logo.png"
              />
              <span className="text-lg font-black tracking-tight text-slate-900">
                DANASEA
              </span>
            </div>

            {activeTab === "forgot_password" && (
              <button
                type="button"
                onClick={() => {
                  setActiveTab("login");
                  setForgotStep("email");
                  setBanner(null);
                  setErrorMessage(null);
                }}
                className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors cursor-pointer group"
              >
                <ArrowLeft className="w-3.5 h-3.5 transition-transform group-hover:-translate-x-0.5" />
                <span>Quay lại đăng nhập</span>
              </button>
            )}
          </div>

          {/* Authoritative Single H1 Title & Subtitle */}
          <div className="space-y-1">
            <h1 className="text-xl font-bold text-slate-900 tracking-tight">
              {activeTab === "login"
                ? "Đăng nhập tài khoản"
                : activeTab === "register"
                ? "Tạo tài khoản DANASEA"
                : forgotStep === "email"
                ? "Tìm tài khoản của bạn"
                : forgotStep === "otp"
                ? "Đặt lại mật khẩu"
                : "Hoàn tất đặt lại mật khẩu"}
            </h1>
            <p className="text-xs text-slate-500 leading-relaxed">
              {activeTab === "login"
                ? "Truy cập hệ thống đặt trải nghiệm biển đảo Đà Nẵng."
                : activeTab === "register"
                ? "Đăng ký thành viên để nhận ưu đãi và quản lý chuyến đi."
                : forgotStep === "email"
                ? "Nhập địa chỉ email liên kết để nhận mã OTP đặt lại mật khẩu."
                : forgotStep === "otp"
                ? `Nhập mã 6 chữ số gửi đến ${forgotEmail} và mật khẩu mới.`
                : "Mật khẩu của bạn đã được cập nhật thành công."}
            </p>
          </div>

          {/* Banner Messages */}
          {banner && (
            <div className="space-y-2">
              {banner === 'locked' && (
                <div className="p-3.5 rounded-xl bg-rose-50 text-rose-900 border border-rose-200 flex items-start gap-2.5 shadow-2xs">
                  <Lock className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                  <div>
                    <p className="text-xs font-bold text-rose-900">Tài khoản bị khóa bởi Quản trị viên</p>
                    <p className="text-xs text-rose-700 mt-0.5 leading-relaxed">
                      {errorMessage || "Tài khoản của bạn đã bị tạm khóa. Vui lòng liên hệ Ban Quản trị DANASEA (hotline: 0905 112 233) để được hỗ trợ mở khóa."}
                    </p>
                  </div>
                </div>
              )}
              {banner === 'wrong_creds' && (
                <div className="p-3.5 rounded-xl bg-rose-50 text-rose-900 border border-rose-200 flex items-start gap-2.5 shadow-2xs">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                  <div>
                    <p className="text-xs font-bold text-rose-900">Không thể đăng nhập</p>
                    <p className="text-xs text-rose-700 mt-0.5 leading-relaxed">
                      {errorMessage || "Email hoặc mật khẩu không chính xác, hoặc tài khoản đã bị khóa bởi Quản trị viên. Vui lòng kiểm tra lại thông tin."}
                    </p>
                  </div>
                </div>
              )}
              {banner === 'register_error' && (
                <div className="p-3 rounded-xl bg-rose-50 text-rose-800 border border-rose-200 flex items-start gap-2.5">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                  <div>
                    <p className="text-xs font-bold">Email này đã được sử dụng</p>
                    <p className="text-xs mt-0.5">Địa chỉ email đã tồn tại trong hệ thống DANASEA. Vui lòng đăng nhập hoặc dùng email khác.</p>
                  </div>
                </div>
              )}
              {banner === 'network_error' && (
                <div className="p-3 rounded-xl bg-rose-50 text-rose-800 border border-rose-200 flex items-start gap-2.5">
                  <WifiOff className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                  <div>
                    <p className="text-xs font-bold">Không thể kết nối đến máy chủ Backend</p>
                    <p className="text-xs mt-0.5">{errorMessage || 'Vui lòng kiểm tra Backend Spring Boot (cổng 8080) đã được khởi động chưa.'}</p>
                  </div>
                </div>
              )}
              {banner === 'custom_error' && (
                <div className="p-3 rounded-xl bg-rose-50 text-rose-800 border border-rose-200">
                  <p className="text-xs font-medium">{errorMessage}</p>
                </div>
              )}
            </div>
          )}

          {/* TAB 1: LOGIN */}
          {activeTab === "login" && (
            <div className="space-y-3.5 block">
              <form className="space-y-3" onSubmit={handleLoginSubmit}>
                <div className="space-y-1.5">
                  <label className="text-[13px] font-semibold text-slate-700">
                    Địa chỉ Email
                  </label>
                  <div className="relative flex items-center">
                    <Mail className="w-4 h-4 absolute left-3.5 text-slate-400 pointer-events-none" />
                    <input 
                      value={email} 
                      onChange={e => setEmail(e.target.value)} 
                      disabled={isLoading}
                      className="w-full h-11 pl-10 pr-3 rounded-xl bg-slate-50 text-slate-900 text-sm outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors placeholder:text-slate-400 disabled:opacity-60" 
                      placeholder="customer_1@danasea.test" 
                      required 
                      type="email" 
                    />
                  </div>
                </div>
                
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <label className="text-[13px] font-semibold text-slate-700">Mật khẩu</label>
                    <button
                      type="button"
                      onClick={() => {
                        setActiveTab("forgot_password");
                        setForgotStep("email");
                        setForgotEmail(email);
                        setBanner(null);
                        setErrorMessage(null);
                      }}
                      className="text-[13px] text-slate-600 hover:text-slate-900 hover:underline cursor-pointer bg-transparent border-0 p-0 font-medium"
                    >
                      Quên mật khẩu?
                    </button>
                  </div>
                  <div className="relative flex items-center">
                    <Lock className="w-4 h-4 absolute left-3.5 text-slate-400 pointer-events-none" />
                    <input 
                      value={password} 
                      onChange={e => setPassword(e.target.value)} 
                      disabled={isLoading}
                      className="w-full h-11 pl-10 pr-11 rounded-xl bg-slate-50 text-slate-900 text-sm outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors placeholder:text-slate-400 disabled:opacity-60" 
                      placeholder="Nhập mật khẩu" 
                      required 
                      type={showPassword ? "text" : "password"} 
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(prev => !prev)}
                      className="absolute right-3 p-1 text-slate-400 hover:text-slate-700 transition-colors flex items-center justify-center cursor-pointer focus:outline-none"
                      tabIndex={-1}
                      title={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
                
                <button 
                  className="w-full h-11 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-sm font-semibold flex items-center justify-center gap-2 shadow-xs transition-colors cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed mt-1" 
                  type="submit"
                  disabled={isLoading}
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Đang đăng nhập...</span>
                    </>
                  ) : (
                    <span>Đăng nhập</span>
                  )}
                </button>
              </form>

              {/* Divider */}
              <div className="relative my-3 flex items-center">
                <div className="flex-1 border-t border-slate-200" />
                <span className="shrink-0 px-3 text-xs text-slate-500 font-medium">
                  hoặc tiếp tục với
                </span>
                <div className="flex-1 border-t border-slate-200" />
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

              <div className="pt-2 text-center">
                <p className="text-xs text-slate-600">
                  Chưa có tài khoản tham gia trải nghiệm?
                  <button
                    type="button"
                    className="text-sky-600 hover:text-sky-700 font-bold hover:underline bg-transparent border-0 cursor-pointer ml-1"
                    onClick={() => {
                      setActiveTab("register");
                      setRegStep("form");
                      setBanner(null);
                      setErrorMessage(null);
                    }}
                  >
                    Đăng ký ngay
                  </button>
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
                  <form className="space-y-2.5" onSubmit={handleRegisterSubmit}>
                    <div className="space-y-1">
                      <label className="text-xs font-semibold text-slate-700">
                        Họ và tên
                      </label>
                      <input 
                        value={regFullName}
                        onChange={e => setRegFullName(e.target.value)}
                        disabled={isLoading}
                        required
                        className="w-full h-10 px-3 rounded-xl bg-slate-50 text-slate-900 text-xs sm:text-sm outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors placeholder:text-slate-400 disabled:opacity-60" 
                        placeholder="Ví dụ: Nguyễn Hải Đăng" 
                        type="text" 
                      />
                    </div>

                    <div className="space-y-1">
                      <label className="text-xs font-semibold text-slate-700">
                        Địa chỉ Email
                      </label>
                      <input 
                        value={regEmail}
                        onChange={e => setRegEmail(e.target.value)}
                        disabled={isLoading}
                        className="w-full h-10 px-3 rounded-xl bg-slate-50 text-slate-900 text-xs sm:text-sm outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors placeholder:text-slate-400 disabled:opacity-60" 
                        placeholder="ban@email.com" 
                        required 
                        type="email" 
                      />
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      <div className="space-y-1">
                        <label className="text-xs font-semibold text-slate-700">
                          Mật khẩu
                        </label>
                        <div className="relative flex items-center">
                          <input 
                            value={regPassword}
                            onChange={e => setRegPassword(e.target.value)}
                            disabled={isLoading}
                            className="w-full h-10 pl-3 pr-8 rounded-xl bg-slate-50 text-slate-900 text-xs sm:text-sm outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors placeholder:text-slate-400 disabled:opacity-60" 
                            placeholder="Tối thiểu 8 ký tự" 
                            required 
                            type={showRegPassword ? "text" : "password"} 
                          />
                          <button
                            type="button"
                            onClick={() => setShowRegPassword(prev => !prev)}
                            className="absolute right-2 p-1 text-slate-400 hover:text-slate-700 transition-colors flex items-center justify-center cursor-pointer focus:outline-none"
                            tabIndex={-1}
                            title={showRegPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                          >
                            {showRegPassword ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                          </button>
                        </div>
                      </div>

                      <div className="space-y-1">
                        <label className="text-xs font-semibold text-slate-700">
                          Nhập lại mật khẩu
                        </label>
                        <div className="relative flex items-center">
                          <input 
                            value={regConfirmPassword}
                            onChange={e => setRegConfirmPassword(e.target.value)}
                            disabled={isLoading}
                            className="w-full h-10 pl-3 pr-8 rounded-xl bg-slate-50 text-slate-900 text-xs sm:text-sm outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors placeholder:text-slate-400 disabled:opacity-60" 
                            placeholder="Xác nhận lại" 
                            required 
                            type={showRegConfirmPassword ? "text" : "password"} 
                          />
                          <button
                            type="button"
                            onClick={() => setShowRegConfirmPassword(prev => !prev)}
                            className="absolute right-2 p-1 text-slate-400 hover:text-slate-700 transition-colors flex items-center justify-center cursor-pointer focus:outline-none"
                            tabIndex={-1}
                            title={showRegConfirmPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                          >
                            {showRegConfirmPassword ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                          </button>
                        </div>
                      </div>
                    </div>

                    {/* Password Requirements Guidance */}
                    <p className="text-[11px] text-slate-500 flex items-center gap-1 leading-tight">
                      <Info className="w-3 h-3 text-slate-400 shrink-0" />
                      <span>Mật khẩu 8+ ký tự (gồm chữ hoa, thường, số và ký tự đặc biệt).</span>
                    </p>

                    <button 
                      className="w-full h-11 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs sm:text-sm font-semibold flex items-center justify-center gap-2 shadow-xs transition-colors cursor-pointer mt-0.5 disabled:opacity-60 disabled:cursor-not-allowed" 
                      type="submit"
                      disabled={isLoading}
                    >
                      {isLoading ? (
                        <>
                          <Loader2 className="w-4 h-4 animate-spin" />
                          <span>Đang xử lý đăng ký...</span>
                        </>
                      ) : (
                        <span>Đăng ký</span>
                      )}
                    </button>
                  </form>

                  {/* Divider */}
                  <div className="relative my-2 flex items-center">
                    <div className="flex-1 border-t border-slate-200" />
                    <span className="shrink-0 px-2.5 text-[11px] text-slate-500 font-medium">
                      hoặc đăng ký bằng
                    </span>
                    <div className="flex-1 border-t border-slate-200" />
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

                  <div className="pt-2 text-center">
                    <p className="text-xs text-slate-600">
                      Đã có tài khoản DANASEA?
                      <button
                        type="button"
                        className="text-sky-600 hover:text-sky-700 font-bold hover:underline bg-transparent border-0 cursor-pointer ml-1"
                        onClick={() => {
                          setActiveTab("login");
                          setBanner(null);
                          setErrorMessage(null);
                        }}
                      >
                        Đăng nhập ngay
                      </button>
                    </p>
                  </div>
                </>
              )}

              {/* SUB-STEP 2: OTP Verification */}
              {regStep === "otp" && (
                <div className="space-y-3">
                  <div className="text-center space-y-1">
                    <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 mx-auto flex items-center justify-center border border-emerald-200">
                      <MailCheck className="w-5 h-5" />
                    </div>
                    <h3 className="text-sm font-bold text-slate-900">Kích hoạt tài khoản</h3>
                    <p className="text-xs text-slate-600 leading-relaxed">
                      Mã xác thực OTP (6 chữ số) đã được gửi tới địa chỉ: <br />
                      <span className="font-semibold text-slate-900">{regEmail}</span>
                    </p>
                  </div>

                  <form className="space-y-3" onSubmit={handleVerifyOtpSubmit}>
                    <div className="space-y-1.5">
                      <label className="text-xs font-semibold text-slate-700 text-center block">
                        Nhập mã xác thực 6 chữ số
                      </label>
                      <input 
                        value={otpCode}
                        onChange={e => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                        disabled={isLoading}
                        maxLength={6}
                        autoFocus
                        className="w-full h-11 text-center font-mono text-xl font-bold tracking-[0.45em] rounded-xl bg-slate-50 text-slate-900 outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors disabled:opacity-60" 
                        placeholder="••••••" 
                        required 
                        type="text" 
                      />
                    </div>

                    {/* Resend OTP Action */}
                    <div className="flex items-center justify-between text-xs pt-0.5">
                      <span className="text-slate-600">Chưa nhận được mã?</span>
                      {otpCooldown > 0 ? (
                        <span className="font-medium text-slate-500 flex items-center gap-1 font-mono">
                          <Timer className="w-3.5 h-3.5" />
                          Gửi lại sau ({otpCooldown}s)
                        </span>
                      ) : (
                        <button
                          type="button"
                          disabled={isLoading}
                          onClick={handleResendOtp}
                          className="text-slate-900 font-bold hover:underline bg-transparent border-0 cursor-pointer disabled:opacity-50"
                        >
                          Gửi lại mã OTP
                        </button>
                      )}
                    </div>

                    <button 
                      className="w-full h-11 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-sm font-semibold flex items-center justify-center gap-2 shadow-xs transition-colors cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed" 
                      type="submit"
                      disabled={isLoading || otpCode.length !== 6}
                    >
                      {isLoading ? (
                        <>
                          <Loader2 className="w-4 h-4 animate-spin" />
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
                        className="text-xs text-slate-500 hover:text-slate-900 transition-colors bg-transparent border-0 cursor-pointer flex items-center justify-center gap-1 mx-auto"
                      >
                        <ArrowLeft className="w-3.5 h-3.5" />
                        <span>Đổi địa chỉ email khác</span>
                      </button>
                    </div>
                  </form>
                </div>
              )}
            </div>
          )}

          {/* TAB 3: IN-PLACE FORGOT PASSWORD (Facebook Pattern) */}
          {activeTab === "forgot_password" && (
            <div className="space-y-3.5 block">
              {/* SUB-STEP 1: Enter Email to Find Account */}
              {forgotStep === "email" && (
                <form className="space-y-3" onSubmit={handleForgotRequestSubmit}>
                  <div className="space-y-1.5">
                    <label className="text-[13px] font-semibold text-slate-700">
                      Địa chỉ Email tài khoản
                    </label>
                    <div className="relative flex items-center">
                      <Mail className="w-4 h-4 absolute left-3.5 text-slate-400 pointer-events-none" />
                      <input
                        value={forgotEmail}
                        onChange={e => setForgotEmail(e.target.value)}
                        disabled={isLoading}
                        className="w-full h-11 pl-10 pr-3 rounded-xl bg-slate-50 text-slate-900 text-sm outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors placeholder:text-slate-400 disabled:opacity-60"
                        placeholder="customer_1@danasea.test"
                        required
                        type="email"
                        autoFocus
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => {
                        setActiveTab("login");
                        setBanner(null);
                        setErrorMessage(null);
                      }}
                      disabled={isLoading}
                      className="w-1/3 h-11 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-sm font-semibold transition-colors cursor-pointer disabled:opacity-60"
                    >
                      Hủy
                    </button>
                    <button
                      type="submit"
                      disabled={isLoading || !forgotEmail.trim()}
                      className="flex-1 h-11 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-sm font-semibold flex items-center justify-center gap-2 shadow-xs transition-colors cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      {isLoading ? (
                        <>
                          <Loader2 className="w-4 h-4 animate-spin" />
                          <span>Đang gửi mã...</span>
                        </>
                      ) : (
                        <span>Gửi mã xác nhận</span>
                      )}
                    </button>
                  </div>
                </form>
              )}

              {/* SUB-STEP 2: Enter OTP & New Password */}
              {forgotStep === "otp" && (
                <form className="space-y-3" onSubmit={handleResetPasswordSubmit}>
                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80 flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-[11px] text-slate-500 font-medium">Mã xác thực gửi tới:</p>
                      <p className="text-xs font-semibold text-slate-900 truncate">{forgotEmail}</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setForgotStep("email");
                        setForgotOtp("");
                        setBanner(null);
                        setErrorMessage(null);
                      }}
                      className="text-[11px] font-semibold text-sky-600 hover:text-sky-700 hover:underline shrink-0 cursor-pointer bg-transparent border-0"
                    >
                      Đổi email
                    </button>
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-700 block">
                      Mã OTP xác thực 6 chữ số
                    </label>
                    <input
                      value={forgotOtp}
                      onChange={e => setForgotOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                      disabled={isLoading}
                      maxLength={6}
                      autoFocus
                      className="w-full h-11 text-center font-mono text-xl font-bold tracking-[0.45em] rounded-xl bg-slate-50 text-slate-900 outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors disabled:opacity-60"
                      placeholder="••••••"
                      required
                      type="text"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-700 block">
                      Mật khẩu mới
                    </label>
                    <div className="relative flex items-center">
                      <Lock className="w-4 h-4 absolute left-3.5 text-slate-400 pointer-events-none" />
                      <input
                        value={forgotNewPass}
                        onChange={e => setForgotNewPass(e.target.value)}
                        disabled={isLoading}
                        className="w-full h-10 pl-10 pr-10 rounded-xl bg-slate-50 text-slate-900 text-sm outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors placeholder:text-slate-400 disabled:opacity-60"
                        placeholder="Tối thiểu 8 ký tự"
                        required
                        type={showForgotNewPass ? "text" : "password"}
                      />
                      <button
                        type="button"
                        onClick={() => setShowForgotNewPass(prev => !prev)}
                        className="absolute right-2.5 p-1 text-slate-400 hover:text-slate-700 transition-colors flex items-center justify-center cursor-pointer focus:outline-none"
                        tabIndex={-1}
                        title={showForgotNewPass ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                      >
                        {showForgotNewPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </button>
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-700 block">
                      Xác nhận mật khẩu mới
                    </label>
                    <div className="relative flex items-center">
                      <Lock className="w-4 h-4 absolute left-3.5 text-slate-400 pointer-events-none" />
                      <input
                        value={forgotConfirmPass}
                        onChange={e => setForgotConfirmPass(e.target.value)}
                        disabled={isLoading}
                        className="w-full h-10 pl-10 pr-10 rounded-xl bg-slate-50 text-slate-900 text-sm outline-none border border-slate-200 focus:border-slate-400 focus:bg-white transition-colors placeholder:text-slate-400 disabled:opacity-60"
                        placeholder="Nhập lại mật khẩu mới"
                        required
                        type={showForgotConfirmPass ? "text" : "password"}
                      />
                      <button
                        type="button"
                        onClick={() => setShowForgotConfirmPass(prev => !prev)}
                        className="absolute right-2.5 p-1 text-slate-400 hover:text-slate-700 transition-colors flex items-center justify-center cursor-pointer focus:outline-none"
                        tabIndex={-1}
                        title={showForgotConfirmPass ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                      >
                        {showForgotConfirmPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </button>
                    </div>
                  </div>

                  {/* Password Requirements Checklist */}
                  <div className="grid grid-cols-2 gap-1.5 py-1">
                    <div className={`flex items-center gap-1.5 text-[11px] font-medium ${forgotHasMinLength ? 'text-emerald-700' : 'text-slate-400'}`}>
                      <span className={`w-3.5 h-3.5 rounded-full flex items-center justify-center text-[9px] font-bold ${forgotHasMinLength ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-500'}`}>
                        ✓
                      </span>
                      <span>8+ ký tự</span>
                    </div>
                    <div className={`flex items-center gap-1.5 text-[11px] font-medium ${forgotHasUppercase ? 'text-emerald-700' : 'text-slate-400'}`}>
                      <span className={`w-3.5 h-3.5 rounded-full flex items-center justify-center text-[9px] font-bold ${forgotHasUppercase ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-500'}`}>
                        ✓
                      </span>
                      <span>1 chữ hoa</span>
                    </div>
                    <div className={`flex items-center gap-1.5 text-[11px] font-medium ${forgotHasNumber ? 'text-emerald-700' : 'text-slate-400'}`}>
                      <span className={`w-3.5 h-3.5 rounded-full flex items-center justify-center text-[9px] font-bold ${forgotHasNumber ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-500'}`}>
                        ✓
                      </span>
                      <span>1 chữ số</span>
                    </div>
                    <div className={`flex items-center gap-1.5 text-[11px] font-medium ${forgotHasSpecial ? 'text-emerald-700' : 'text-slate-400'}`}>
                      <span className={`w-3.5 h-3.5 rounded-full flex items-center justify-center text-[9px] font-bold ${forgotHasSpecial ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-500'}`}>
                        ✓
                      </span>
                      <span>Ký tự đặc biệt</span>
                    </div>
                  </div>

                  {/* Resend OTP */}
                  <div className="flex items-center justify-between text-xs pt-0.5">
                    <span className="text-slate-600">Chưa nhận được mã?</span>
                    {forgotCooldown > 0 ? (
                      <span className="font-medium text-slate-500 flex items-center gap-1 font-mono">
                        <Timer className="w-3.5 h-3.5" />
                        Gửi lại sau ({forgotCooldown}s)
                      </span>
                    ) : (
                      <button
                        type="button"
                        disabled={isLoading}
                        onClick={handleForgotResendOtp}
                        className="text-slate-900 font-bold hover:underline bg-transparent border-0 cursor-pointer disabled:opacity-50"
                      >
                        Gửi lại mã OTP
                      </button>
                    )}
                  </div>

                  <button
                    type="submit"
                    disabled={isLoading || forgotOtp.length !== 6 || forgotCriteriaCount < 4}
                    className="w-full h-11 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-sm font-semibold flex items-center justify-center gap-2 shadow-xs transition-colors cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed mt-1"
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        <span>Đang xác nhận...</span>
                      </>
                    ) : (
                      <span>Đặt lại mật khẩu</span>
                    )}
                  </button>
                </form>
              )}

              {/* SUB-STEP 3: Password Reset Success */}
              {forgotStep === "success" && (
                <div className="py-3 text-center space-y-3.5">
                  <div className="w-12 h-12 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-600 mx-auto flex items-center justify-center shadow-xs">
                    <CheckCircle2 className="w-7 h-7" />
                  </div>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold text-slate-900">Mật khẩu đã được cập nhật thành công</h3>
                    <p className="text-xs text-slate-600 leading-relaxed max-w-xs mx-auto">
                      Tài khoản của bạn đã được thiết lập mật khẩu mới. Bạn có thể đăng nhập ngay với mật khẩu vừa tạo.
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setEmail(forgotEmail);
                      setPassword("");
                      setActiveTab("login");
                      setForgotStep("email");
                      setBanner(null);
                      setErrorMessage(null);
                    }}
                    className="w-full h-11 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white text-sm font-semibold flex items-center justify-center gap-2 shadow-xs transition-colors cursor-pointer"
                  >
                    <LogIn className="w-4 h-4" />
                    <span>Đăng nhập ngay</span>
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
