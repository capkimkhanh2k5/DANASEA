import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import { apiClient, refreshClient } from '../apiClient';
import { useAuthStore } from '../../store/useAuthStore';
import { useToastStore } from '../../store/useToastStore';
import type { User } from '../../types';

function makeJwt(offsetSec: number): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(
    JSON.stringify({ sub: 'user@danasea.vn', exp: Math.floor(Date.now() / 1000) + offsetSec })
  ).toString('base64url');
  return `${header}.${body}.sig`;
}

const TEST_USER: User = {
  id: 'u-1',
  email: 'user@danasea.vn',
  fullName: 'Test User',
  avatarUrl: '',
  role: 'CUSTOMER',
  isEmailVerified: true,
  isLocked: false,
  phone: '',
  passwordHash: '',
  locale: 'vi',
};

function makeAxiosError(
  config: InternalAxiosRequestConfig,
  status: number,
  data: unknown
): AxiosError {
  return new AxiosError(
    `Request failed with status code ${status}`,
    AxiosError.ERR_BAD_REQUEST,
    config,
    undefined,
    {
      status,
      statusText: String(status),
      headers: {},
      config,
      data,
    }
  );
}

describe('apiClient silent refresh & 401 handling', () => {
  const originalApiAdapter = apiClient.defaults.adapter;
  const originalRefreshAdapter = refreshClient.defaults.adapter;

  beforeEach(() => {
    useAuthStore.getState().logout();
    useToastStore.getState().clearAll();
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalApiAdapter;
    refreshClient.defaults.adapter = originalRefreshAdapter;
    useAuthStore.getState().logout();
    useToastStore.getState().clearAll();
  });

  it('coalesces concurrent 401s into a single /auth/refresh call and replays both requests', async () => {
    const expiredToken = makeJwt(300); // not expired locally, but rejected by server with 401
    const rotatedToken = makeJwt(3600);
    useAuthStore.getState().setAuth(TEST_USER, expiredToken);

    let refreshCalls = 0;
    refreshClient.defaults.adapter = (async (config) => {
      refreshCalls += 1;
      await new Promise((r) => setTimeout(r, 20));
      return {
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
        data: { accessToken: rotatedToken },
      };
    }) as AxiosAdapter;

    apiClient.defaults.adapter = (async (config) => {
      const authHeader = String(config.headers?.Authorization ?? '');
      if (authHeader !== `Bearer ${rotatedToken}`) {
        throw makeAxiosError(config, 401, { code: 'UNAUTHORIZED', message: 'Expired' });
      }
      return {
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
        data: { ok: true, url: config.url },
      };
    }) as AxiosAdapter;

    const [r1, r2] = await Promise.all([
      apiClient.get('/orders/me', { silent: true }),
      apiClient.get('/notifications/unread-count', { silent: true }),
    ]);

    assert.equal(refreshCalls, 1);
    assert.equal(r1.data.ok, true);
    assert.equal(r2.data.ok, true);
    assert.equal(useAuthStore.getState().accessToken, rotatedToken);
    assert.equal(useAuthStore.getState().isAuthenticated, true);
  });

  it('does NOT log out or refresh when /users/me/change-password returns 401 INVALID_CREDENTIALS', async () => {
    const validToken = makeJwt(3600);
    useAuthStore.getState().setAuth(TEST_USER, validToken);

    let refreshCalls = 0;
    refreshClient.defaults.adapter = (async (config) => {
      refreshCalls += 1;
      return { status: 200, statusText: 'OK', headers: {}, config, data: { accessToken: makeJwt(3600) } };
    }) as AxiosAdapter;

    apiClient.defaults.adapter = (async (config) => {
      throw makeAxiosError(config, 401, { code: 'INVALID_CREDENTIALS', message: 'Wrong current password' });
    }) as AxiosAdapter;

    await assert.rejects(
      () => apiClient.post('/users/me/change-password', { currentPassword: 'bad', newPassword: 'NewPassword1!' }, { silent: true }),
      (err: unknown) => err instanceof AxiosError && err.response?.status === 401
    );

    assert.equal(refreshCalls, 0);
    assert.equal(useAuthStore.getState().isAuthenticated, true);
    assert.equal(useAuthStore.getState().accessToken, validToken);
  });

  it('expires the session and shows a toast when /auth/refresh returns 401', async () => {
    const expiredToken = makeJwt(300);
    useAuthStore.getState().setAuth(TEST_USER, expiredToken);

    refreshClient.defaults.adapter = (async (config) => {
      throw makeAxiosError(config, 401, { code: 'INVALID_CREDENTIALS', message: 'Refresh revoked' });
    }) as AxiosAdapter;

    apiClient.defaults.adapter = (async (config) => {
      throw makeAxiosError(config, 401, { code: 'UNAUTHORIZED', message: 'Expired' });
    }) as AxiosAdapter;

    await assert.rejects(
      () => apiClient.get('/users/me', { silent: true }),
      (err: unknown) => err instanceof AxiosError && err.response?.status === 401
    );

    assert.equal(useAuthStore.getState().isAuthenticated, false);
    assert.equal(useAuthStore.getState().accessToken, null);
    assert.equal(useToastStore.getState().toasts.length, 1);
  });

  it('discards refreshed token if the user logs out while /auth/refresh is in flight', async () => {
    const staleToken = makeJwt(300);
    const rotatedToken = makeJwt(3600);
    useAuthStore.getState().setAuth(TEST_USER, staleToken);

    refreshClient.defaults.adapter = (async (config) => {
      await new Promise((r) => setTimeout(r, 30));
      return { status: 200, statusText: 'OK', headers: {}, config, data: { accessToken: rotatedToken } };
    }) as AxiosAdapter;

    apiClient.defaults.adapter = (async (config) => {
      throw makeAxiosError(config, 401, { code: 'UNAUTHORIZED' });
    }) as AxiosAdapter;

    const pendingReq = apiClient.get('/orders/me', { silent: true });
    // User logs out before the refresh resolves
    await new Promise((r) => setTimeout(r, 5));
    useAuthStore.getState().logout();

    await assert.rejects(() => pendingReq);
    assert.equal(useAuthStore.getState().isAuthenticated, false);
    assert.equal(useAuthStore.getState().accessToken, null);
  });
});
