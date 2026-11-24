import { useEffect, useState } from 'react';
import { useAuthStore } from '../store/useAuthStore';

/**
 * Returns true once Zustand persist has finished rehydrating auth state from localStorage.
 * Prevents guarded layouts and pages from flashing or prematurely redirecting to /auth on reload.
 */
export function useAuthHydrated(): boolean {
  const [hydrated, setHydrated] = useState(() => {
    if (typeof window === 'undefined') return true;
    return useAuthStore.persist?.hasHydrated?.() ?? true;
  });

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (useAuthStore.persist?.hasHydrated?.()) {
      setHydrated(true);
      return;
    }
    const unsub = useAuthStore.persist?.onFinishHydration?.(() => {
      setHydrated(true);
    });
    return () => {
      unsub?.();
    };
  }, []);

  return hydrated;
}
