import { useAuthStore } from '../store/useAuthStore';
import { authService } from './authService';

const DEFAULT_AVATARS = {
  ADMIN: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150',
  VENDOR: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150',
  CUSTOMER: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
} as const;

function isMockToken(token: string | null) {
  return Boolean(token?.startsWith('mock_jwt_token_'));
}

/**
 * Sign out everywhere: revoke refresh token + clear the HttpOnly cookie on the server
 * (best effort), then always clear local state.
 */
export async function logoutSession(): Promise<void> {
  const { accessToken } = useAuthStore.getState();
  try {
    if (accessToken && !isMockToken(accessToken)) {
      await authService.logout();
    }
  } finally {
    useAuthStore.getState().logout();
  }
}

let profileSyncInFlight: Promise<void> | null = null;

/**
 * Pull the authoritative profile (/users/me) into the auth store so name, avatar,
 * role and verification status never drift from the database.
 */
export function syncProfile(): Promise<void> {
  const { isAuthenticated, accessToken } = useAuthStore.getState();
  if (!isAuthenticated || !accessToken || isMockToken(accessToken)) return Promise.resolve();

  if (!profileSyncInFlight) {
    profileSyncInFlight = authService
      .getMyProfile()
      .then((profile) => {
        const { user, updateUser } = useAuthStore.getState();
        if (!user || user.id !== profile.id) return; // account switched meanwhile
        updateUser({
          email: profile.email,
          role: profile.role,
          fullName: profile.fullName || user.fullName,
          avatarUrl: profile.avatarUrl || user.avatarUrl || DEFAULT_AVATARS[profile.role],
          isEmailVerified: profile.isEmailVerified,
          locale: profile.locale || user.locale,
        });
      })
      .catch(() => {
        // Non-fatal: keep the cached user. 401s are handled by apiClient (refresh/expire).
      })
      .finally(() => {
        profileSyncInFlight = null;
      });
  }
  return profileSyncInFlight;
}
