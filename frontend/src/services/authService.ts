import { apiClient } from './apiClient';
import type { Role } from '../types';

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface AuthenticationResponse {
  accessToken: string;
  userId: string;
  email: string;
  role: Role;
}

export interface UserProfileResponse {
  id: string;
  email: string;
  fullName: string | null;
  avatarUrl: string | null;
  locale: string | null;
  role: Role;
  isEmailVerified: boolean;
}

export interface RegisterCredentials {
  email: string;
  password: string;
}

export interface VerifyOtpPayload {
  code: string;
}

export interface GoogleLoginPayload {
  credential?: string;
  idToken?: string;
}

export const authService = {
  /**
   * Log in with email and password
   */
  async login(credentials: LoginCredentials): Promise<AuthenticationResponse> {
    console.log('[authService] Initiating login for:', credentials.email);
    const response = await apiClient.post<AuthenticationResponse>('/auth/login', credentials);
    return response.data;
  },

  /**
   * Register a new user account (CUSTOMER role)
   */
  async register(credentials: RegisterCredentials): Promise<AuthenticationResponse> {
    console.log('[authService] Initiating registration for:', credentials.email);
    const response = await apiClient.post<AuthenticationResponse>('/auth/register', credentials);
    return response.data;
  },

  /**
   * Log in or auto-register via Google OAuth2 credential / ID token (POST /api/auth/oauth2/google)
   */
  async loginWithGoogle(credentialOrIdToken: string): Promise<AuthenticationResponse> {
    console.log('[authService] Initiating Google OAuth2 login (/auth/oauth2/google)...');
    const response = await apiClient.post<AuthenticationResponse>('/auth/oauth2/google', {
      credential: credentialOrIdToken,
      idToken: credentialOrIdToken,
    });
    return response.data;
  },

  /**
   * Send or resend OTP verification code to authenticated user's email
   */
  async sendVerificationOtp(): Promise<void> {
    console.log('[authService] Requesting verification OTP (/auth/otp/send)...');
    await apiClient.post('/auth/otp/send');
  },

  /**
   * Verify 6-digit OTP code to activate email
   */
  async verifyOtp(payload: VerifyOtpPayload): Promise<void> {
    console.log('[authService] Verifying OTP code:', payload.code);
    await apiClient.post('/auth/otp/verify', payload);
  },

  /**
   * Log out current user and clear server-side session/cookies
   */
  async logout(): Promise<void> {
    console.log('[authService] Logging out...');
    try {
      await apiClient.post('/auth/logout');
    } catch (error) {
      console.warn('[authService] Server logout notification failed, continuing local cleanup:', error);
    }
  },

  /**
   * Fetch current authenticated user's profile
   */
  async getMyProfile(): Promise<UserProfileResponse> {
    console.log('[authService] Fetching profile (/users/me)...');
    const response = await apiClient.get<UserProfileResponse>('/users/me');
    return response.data;
  },

  /**
   * Update current authenticated user\'s profile (fullName, avatarUrl, locale)
   */
  async updateProfile(payload: { fullName?: string; avatarUrl?: string; locale?: string }): Promise<UserProfileResponse> {
    const response = await apiClient.patch<UserProfileResponse>('/users/me', payload);
    return response.data;
  },

  /**
   * Change password for current authenticated user
   */
  async changePassword(payload: { currentPassword: string; newPassword: string }): Promise<void> {
    await apiClient.post('/users/me/change-password', payload);
  },

  /**
   * Request password reset OTP code
   */
  async forgotPassword(email: string): Promise<{ message: string }> {
    console.log('[authService] Requesting forgot password OTP for:', email);
    const response = await apiClient.post<{ message: string }>('/auth/forgot-password', { email });
    return response.data;
  },

  /**
   * Reset password with 6-digit OTP and new password
   */
  async resetPassword(payload: { email: string; otp: string; newPassword: string }): Promise<{ message: string }> {
    console.log('[authService] Submitting reset password for:', payload.email);
    const response = await apiClient.post<{ message: string }>('/auth/reset-password', payload);
    return response.data;
  },
};
