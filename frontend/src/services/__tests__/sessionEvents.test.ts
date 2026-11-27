import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  getTokenExpiryMs,
  isProtectedPath,
  resolveSafeReturnTo,
} from '../sessionEvents.ts';

function makeJwt(payload: Record<string, unknown>): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  return `${header}.${body}.signature`;
}

describe('sessionEvents helpers', () => {
  it('decodes JWT exp claim into epoch milliseconds', () => {
    const expSec = Math.floor(Date.now() / 1000) + 3600;
    const token = makeJwt({ sub: 'user@danasea.vn', exp: expSec });
    assert.equal(getTokenExpiryMs(token), expSec * 1000);
  });

  it('returns null for mock, malformed, or missing tokens', () => {
    assert.equal(getTokenExpiryMs(null), null);
    assert.equal(getTokenExpiryMs(undefined), null);
    assert.equal(getTokenExpiryMs('mock_jwt_token_demo'), null);
    assert.equal(getTokenExpiryMs('not.a.valid.jwt.token'), null);
    assert.equal(getTokenExpiryMs('bad.!!!.sig'), null);
  });

  it('identifies protected customer, vendor, and admin paths accurately', () => {
    assert.equal(isProtectedPath('/profile'), true);
    assert.equal(isProtectedPath('/profile/orders'), true);
    assert.equal(isProtectedPath('/vendor'), true);
    assert.equal(isProtectedPath('/vendor/orders'), true);
    assert.equal(isProtectedPath('/admin/users'), true);
    assert.equal(isProtectedPath('/checkout'), true);
    assert.equal(isProtectedPath('/inbox'), true);
    assert.equal(isProtectedPath('/'), false);
    assert.equal(isProtectedPath('/search'), false);
    assert.equal(isProtectedPath('/auth'), false);
    assert.equal(isProtectedPath('/profiles-public'), false);
  });

  it('validates returnTo paths against open redirects, auth loops, and role boundaries', () => {
    assert.equal(resolveSafeReturnTo('https://evil.com/phish', 'CUSTOMER'), null);
    assert.equal(resolveSafeReturnTo('//evil.com/phish', 'CUSTOMER'), null);
    assert.equal(resolveSafeReturnTo('/\\evil.com', 'CUSTOMER'), null);
    assert.equal(resolveSafeReturnTo('/auth', 'CUSTOMER'), null);
    assert.equal(resolveSafeReturnTo('/auth/forgot-password', 'CUSTOMER'), null);

    // Role boundaries
    assert.equal(resolveSafeReturnTo('/admin/users', 'CUSTOMER'), null);
    assert.equal(resolveSafeReturnTo('/admin/users', 'VENDOR'), null);
    assert.equal(resolveSafeReturnTo('/admin/users?page=2', 'ADMIN'), '/admin/users?page=2');

    assert.equal(resolveSafeReturnTo('/vendor/orders', 'CUSTOMER'), null);
    assert.equal(resolveSafeReturnTo('/vendor/orders', 'VENDOR'), '/vendor/orders');
    assert.equal(resolveSafeReturnTo('/vendor/orders', 'ADMIN'), '/vendor/orders');

    // Allowed customer destinations
    assert.equal(resolveSafeReturnTo('/profile/orders?tab=upcoming', 'CUSTOMER'), '/profile/orders?tab=upcoming');
  });
});
