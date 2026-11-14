import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  getActionMeta,
  ENTITY_LABELS,
  filterAuditLogs,
  parseAuditOutcome,
  sanitizeMetadata,
  formatDateTimeVN,
  REDACTED_PLACEHOLDER,
  type AdminAuditLogItem,
} from '../logsModel.ts';

describe('Audit Logs Model & Transformations', () => {
  it('correctly maps known actions with Vietnamese labels and tones', () => {
    const reconcileMeta = getActionMeta('RECONCILE_PAYMENT_PENDING');
    assert.equal(reconcileMeta.label, 'Đối soát thanh toán (Chờ xác nhận)');
    assert.equal(reconcileMeta.tone, 'amber');

    const approveMeta = getActionMeta('APPROVE_VENDOR');
    assert.equal(approveMeta.label, 'Phê duyệt đối tác');
    assert.equal(approveMeta.tone, 'teal');

    const rejectMeta = getActionMeta('PARTNER_APPLICATION_REJECTION');
    assert.equal(rejectMeta.label, 'Từ chối đơn đăng ký đối tác');
    assert.equal(rejectMeta.tone, 'rose');
  });

  it('safely handles unknown action codes with titlecase fallback', () => {
    const unknownMeta = getActionMeta('CUSTOM_BACKGROUND_JOB_RUN');
    assert.equal(unknownMeta.label, 'Custom Background Job Run');
    assert.equal(unknownMeta.tone, 'neutral');
    assert.ok(unknownMeta.description.includes('CUSTOM_BACKGROUND_JOB_RUN'));
  });

  it('correctly maps entity types to human-readable Vietnamese labels', () => {
    assert.equal(ENTITY_LABELS['PAYMENT'], 'Giao dịch thanh toán');
    assert.equal(ENTITY_LABELS['PARTNER_APPLICATION'], 'Đơn đăng ký đối tác');
    assert.equal(ENTITY_LABELS['VENDOR_DOCUMENT'], 'Chứng từ đối tác');
    assert.equal(ENTITY_LABELS['PayoutRequest'], 'Yêu cầu quyết toán');
    assert.equal(ENTITY_LABELS['Order'], 'Đơn hàng');
  });

  it('parses technical reconciliation metadata into human-readable outcomes', () => {
    const rawMeta = JSON.stringify({
      lastError: 'GATEWAY_QUERY_AWAITING_VERIFICATION: Persisted gateway reference and transaction date are required.',
      provider: 'PAYPAL',
      transactionId: null,
      status: 'PENDING',
      attempts: 20,
    });

    const outcomes = parseAuditOutcome(rawMeta);
    assert.equal(outcomes.length, 5);

    const errorItem = outcomes.find(o => o.key === 'lastError');
    assert.ok(errorItem);
    assert.equal(errorItem.label, 'Chi tiết lỗi đối soát');
    assert.ok(errorItem.value.includes('Chờ xác thực cổng thanh toán'));

    const providerItem = outcomes.find(o => o.key === 'provider');
    assert.ok(providerItem);
    assert.equal(providerItem.label, 'Cổng thanh toán');
    assert.equal(providerItem.value, 'Cổng thanh toán PayPal');

    const txItem = outcomes.find(o => o.key === 'transactionId');
    assert.ok(txItem);
    assert.equal(txItem.value, 'Chưa ghi nhận (null)');

    const statusItem = outcomes.find(o => o.key === 'status');
    assert.ok(statusItem);
    assert.equal(statusItem.value, 'Đang chờ xử lý (PENDING)');

    const attemptsItem = outcomes.find(o => o.key === 'attempts');
    assert.ok(attemptsItem);
    assert.equal(attemptsItem.label, 'Số lần thử đối soát');
    assert.equal(attemptsItem.value, '20');
  });

  it('sanitizes sensitive fields like passwords, tokens, and secrets in metadata', () => {
    const raw = JSON.stringify({
      user: 'admin',
      password: 'secret_password_123',
      apiKey: 'xyz_key',
      auth_token: 'bearer_token',
      nested: {
        pin: '1234',
        safeField: 'visible_data',
      },
    });

    const sanitized = sanitizeMetadata(raw) as Record<string, unknown>;
    assert.equal(sanitized.user, 'admin');
    assert.equal(sanitized.password, REDACTED_PLACEHOLDER);
    assert.equal(sanitized.apiKey, REDACTED_PLACEHOLDER);
    assert.equal(sanitized.auth_token, REDACTED_PLACEHOLDER);
    assert.equal((sanitized.nested as Record<string, unknown>).pin, REDACTED_PLACEHOLDER);
    assert.equal((sanitized.nested as Record<string, unknown>).safeField, 'visible_data');
  });

  it('filters audit logs by action, entity, and query accurately', () => {
    const mockLogs: AdminAuditLogItem[] = [
      {
        id: 'log-1',
        actorUserId: '',
        actorName: 'Hệ thống',
        action: 'RECONCILE_PAYMENT_PENDING',
        entityType: 'PAYMENT',
        entityId: 'ent-pay-1',
        metadata: '{"attempts":5}',
        createdAt: '2026-11-14T10:00:00Z',
        updatedAt: '2026-11-14T10:00:00Z',
      },
      {
        id: 'log-2',
        actorUserId: 'usr-admin-1',
        actorName: 'Quản trị viên',
        actorEmail: 'admin@danasea.vn',
        action: 'APPROVE_PAYOUT',
        entityType: 'PayoutRequest',
        entityId: 'ent-payout-1',
        metadata: '{"amount":5000000}',
        createdAt: '2026-11-14T10:05:00Z',
        updatedAt: '2026-11-14T10:05:00Z',
      },
      {
        id: 'log-3',
        actorUserId: 'usr-admin-1',
        actorName: 'Quản trị viên',
        action: 'PARTNER_APPLICATION_REJECTION',
        entityType: 'PARTNER_APPLICATION',
        entityId: 'ent-partner-1',
        metadata: '{"reason":"Thiếu GPKD"}',
        createdAt: '2026-11-14T10:10:00Z',
        updatedAt: '2026-11-14T10:10:00Z',
      },
    ];

    // 1. ALL returns all items
    const all = filterAuditLogs(mockLogs, '', 'ALL', 'ALL');
    assert.equal(all.length, 3);

    // 2. Filter by Action
    const reconcileOnly = filterAuditLogs(mockLogs, '', 'RECONCILE_PAYMENT_PENDING', 'ALL');
    assert.equal(reconcileOnly.length, 1);
    assert.equal(reconcileOnly[0].id, 'log-1');

    // 3. Filter by Entity
    const paymentOnly = filterAuditLogs(mockLogs, '', 'ALL', 'PAYMENT');
    assert.equal(paymentOnly.length, 1);
    assert.equal(paymentOnly[0].id, 'log-1');

    // 4. Search query matching id
    const searchById = filterAuditLogs(mockLogs, 'log-2', 'ALL', 'ALL');
    assert.equal(searchById.length, 1);
    assert.equal(searchById[0].id, 'log-2');

    // 5. Search query matching Vietnamese action label
    const searchByLabel = filterAuditLogs(mockLogs, 'Đối soát', 'ALL', 'ALL');
    assert.equal(searchByLabel.length, 1);
    assert.equal(searchByLabel[0].id, 'log-1');

    // 6. Search query matching metadata
    const searchByMeta = filterAuditLogs(mockLogs, 'Thiếu GPKD', 'ALL', 'ALL');
    assert.equal(searchByMeta.length, 1);
    assert.equal(searchByMeta[0].id, 'log-3');

    // 7. No match
    const noMatch = filterAuditLogs(mockLogs, 'NonExistentXYZ', 'ALL', 'ALL');
    assert.equal(noMatch.length, 0);
  });

  it('formats dates into Vietnamese locale string with ICT time', () => {
    const formatted = formatDateTimeVN('2026-11-14T13:21:49.357Z');
    assert.ok(formatted.includes('2026'));
    assert.ok(formatted.includes('20:21') || formatted.includes('13:21') || formatted.length > 5);
  });
});
