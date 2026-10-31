import React, { useEffect, useRef, useState } from 'react';
import type { GoogleCredentialResponse } from '../../types/google';

interface GoogleLoginButtonProps {
  onSuccess: (credential: string) => void;
  onError?: (errorMsg: string) => void;
  isLoading?: boolean;
  disabled?: boolean;
  mode?: 'login' | 'register';
}

export const GoogleLoginButton: React.FC<GoogleLoginButtonProps> = ({
  onSuccess,
  onError,
  isLoading = false,
  disabled = false,
  mode = 'login',
}) => {
  const buttonContainerRef = useRef<HTMLDivElement>(null);
  const isInitializedRef = useRef(false);
  const [isGsiLoaded, setIsGsiLoaded] = useState(false);
  const [clientIdNotice, setClientIdNotice] = useState<string | null>(null);

  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';

  useEffect(() => {
    // Check if Google GSI SDK script has loaded
    const checkGsi = () => {
      if (typeof window !== 'undefined' && window.google?.accounts?.id) {
        setIsGsiLoaded(true);
        return true;
      }
      return false;
    };

    if (checkGsi()) return;

    // Retry checking every 200ms up to 5 seconds
    const interval = setInterval(() => {
      if (checkGsi()) {
        clearInterval(interval);
      }
    }, 200);

    const timeout = setTimeout(() => {
      clearInterval(interval);
    }, 5000);

    return () => {
      clearInterval(interval);
      clearTimeout(timeout);
    };
  }, []);

  useEffect(() => {
    if (!isGsiLoaded || !googleClientId || !buttonContainerRef.current) return;

    try {
      if (!isInitializedRef.current) {
        console.log('[GoogleLoginButton] Initializing Google Identity Services with Client ID:', googleClientId);
        window.google!.accounts.id.initialize({
          client_id: googleClientId,
          callback: (response: GoogleCredentialResponse) => {
            if (response?.credential) {
              onSuccess(response.credential);
            } else {
              console.warn('[GoogleLoginButton] No credential in Google response');
              onError?.('Không nhận được thông tin xác thực từ Google.');
            }
          },
          auto_select: false,
          cancel_on_tap_outside: true,
          use_fedcm_for_prompt: true,
        });
        isInitializedRef.current = true;
      }

      // Clear any previous rendered button
      if (buttonContainerRef.current) {
        buttonContainerRef.current.innerHTML = '';
        window.google!.accounts.id.renderButton(buttonContainerRef.current, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          text: mode === 'register' ? 'signup_with' : 'continue_with',
          shape: 'rectangular',
          logo_alignment: 'left',
          width: 380,
          locale: 'vi',
        });
      }

      // Trigger One Tap if appropriate
      // Trigger One Tap if appropriate
      // Google One Tap prompt is disabled in local dev to avoid unregistered_origin 403 network errors
      // The standard Google Sign-In button renders and works cleanly
    } catch (err: unknown) {
      console.error('[GoogleLoginButton] Failed to initialize Google button:', err);
    }
  }, [isGsiLoaded, googleClientId, mode, onSuccess, onError]);

  const handleCustomButtonClick = () => {
    if (disabled || isLoading) return;

    if (!googleClientId) {
      console.warn('[GoogleLoginButton] VITE_GOOGLE_CLIENT_ID is not configured in .env');
      setClientIdNotice(
        'Chưa cấu hình VITE_GOOGLE_CLIENT_ID trong file .env. Vui lòng thiết lập Google OAuth Client ID từ Google Cloud Console để đăng nhập thực tế.'
      );
      onError?.('Vui lòng cấu hình VITE_GOOGLE_CLIENT_ID trong .env để kết nối Google OAuth.');
      return;
    }

    if (window.google?.accounts?.id) {
      window.google.accounts.id.prompt();
    } else {
      onError?.('Thư viện Google Identity Services chưa tải xong. Vui lòng thử lại sau giây lát.');
    }
  };

  return (
    <div className="w-full flex flex-col items-center gap-2">
      {/* Container for official Google GSI button if clientId is provided */}
      {googleClientId && (
        <div
          ref={buttonContainerRef}
          className={`w-full flex justify-center min-h-[44px] ${isLoading || disabled ? 'pointer-events-none opacity-60' : ''}`}
        />
      )}

      {/* Fallback & Custom Styled Button */}
      {(!googleClientId || !isGsiLoaded) && (
        <button
          type="button"
          onClick={handleCustomButtonClick}
          disabled={disabled || isLoading}
          className="w-full h-[44px] px-4 rounded-xl bg-surface-container-lowest hover:bg-surface-container text-on-surface text-xs sm:text-sm font-semibold flex items-center justify-center gap-3 border border-outline-variant/40 shadow-xs hover:shadow-sm transition-all duration-200 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {/* Official Google 4-Color SVG Icon */}
          <svg className="w-4 h-4 flex-shrink-0" viewBox="0 0 24 24">
            <path
              fill="#4285F4"
              d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.66-5.17 3.66-9.17z"
            />
            <path
              fill="#34A853"
              d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.25v3.15C3.26 21.36 7.33 24 12 24z"
            />
            <path
              fill="#FBBC05"
              d="M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.58H1.25C.45 8.18 0 9.99 0 12s.45 3.82 1.25 5.42l4.03-3.15z"
            />
            <path
              fill="#EA4335"
              d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.26 2.64 1.25 6.58l4.03 3.15c.95-2.83 3.6-4.98 6.72-4.98z"
            />
          </svg>
          <span>{mode === 'register' ? 'Đăng ký nhanh với Google' : 'Tiếp tục với Google'}</span>
        </button>
      )}

      {/* Developer Notice when Client ID is missing */}
      {clientIdNotice && (
        <div className="w-full p-2.5 rounded-xl bg-amber-50 border border-amber-200/80 text-amber-800 text-[11px] leading-relaxed flex items-start gap-1.5 animate-fade-in">
          <span className="material-symbols-outlined text-[16px] text-amber-600 flex-shrink-0 mt-0.5">info</span>
          <span>{clientIdNotice}</span>
        </div>
      )}
    </div>
  );
};
