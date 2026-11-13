import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { getAdminAiError, AI_TIMEOUT_CONFIG } from '../adminAiService';

describe('Admin AI Service Configuration & Error Handling', () => {
  it('has calibrated timeout values matching technical architecture', () => {
    assert.equal(AI_TIMEOUT_CONFIG.EXECUTIVE_DIGEST_MS, 15000);
    assert.equal(AI_TIMEOUT_CONFIG.DISPUTE_ARBITRATION_MS, 20000);
    assert.equal(AI_TIMEOUT_CONFIG.COPILOT_CHAT_MS, 90000);
    assert.equal(AI_TIMEOUT_CONFIG.COPILOT_CRUD_MS, 15000);
  });

  it('handles ECONNABORTED timeout errors with user-friendly message', () => {
    const timeoutErr = {
      isAxiosError: true,
      code: 'ECONNABORTED',
      message: 'timeout of 90000ms exceeded',
    };
    const msg = getAdminAiError(timeoutErr);
    assert.ok(msg.includes('vượt quá thời gian phản hồi'));
  });

  it('extracts server-provided error messages if available', () => {
    const apiErr = {
      isAxiosError: true,
      response: {
        status: 400,
        data: { message: 'Dispute not found with ID: 12345' },
      },
    };
    const msg = getAdminAiError(apiErr);
    assert.equal(msg, 'Dispute not found with ID: 12345');
  });

  it('surfaces 429 rate limit errors properly', () => {
    const rateLimitErr = {
      isAxiosError: true,
      response: {
        status: 429,
        data: {},
      },
    };
    const msg = getAdminAiError(rateLimitErr);
    assert.ok(msg.includes('nhiều yêu cầu'));
  });

  it('surfaces 403 access denied errors properly', () => {
    const authErr = {
      isAxiosError: true,
      response: {
        status: 403,
        data: {},
      },
    };
    const msg = getAdminAiError(authErr);
    assert.ok(msg.includes('quyền Quản trị viên'));
  });

  it('surfaces 500 server errors transparently without mock hiding', () => {
    const serverErr = {
      isAxiosError: true,
      response: {
        status: 500,
        data: {},
      },
    };
    const msg = getAdminAiError(serverErr);
    assert.ok(msg.includes('Máy chủ Backend phản hồi lỗi (500)'));
  });
});

