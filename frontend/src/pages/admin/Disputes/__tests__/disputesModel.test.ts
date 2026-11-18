import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import { filterDisputes, type DisputeExtended } from '../disputesModel.ts';

describe('disputesModel — filterDisputes with display codes', () => {
  const sampleDisputes: DisputeExtended[] = [
    {
      id: '77777777-7777-7777-7777-777777777701',
      orderId: '00010000-0000-0000-0000-000000000001',
      subOrderId: '00020000-0000-0000-0000-000000000001',
      raisedBy: 'user-1',
      customerName: 'Nguyễn Hoàng Long',
      vendorName: 'DaNaFi – Tour Câu Cá Đà Nẵng',
      serviceName: 'Câu cá chuyên nghiệp Sơn Trà',
      category: 'SERVICE_QUALITY',
      description: 'Dịch vụ không đúng cam kết',
      status: 'OPEN' as unknown as DisputeExtended['status'],
      resolutionNote: '',
      orderAmount: 1000000,
      createdAt: '2026-10-25T10:00:00Z',
      updatedAt: '2026-10-25T10:00:00Z',
      resolvedBy: null as unknown as string,
      resolvedAt: null as unknown as string,
    },
    {
      id: '77777777-7777-7777-7777-777777777704',
      orderId: '00010000-0000-0000-0000-000000000004',
      subOrderId: '00020000-0000-0000-0000-000000000004',
      raisedBy: 'user-2',
      customerName: 'Trần Văn An',
      vendorName: 'Loca Travel Đà Nẵng',
      serviceName: 'Lặn ngắm san hô Sơn Trà',
      category: 'SAFETY_ISSUE',
      description: 'Thiếu trang thiết bị bảo hộ',
      status: 'RESOLVED' as unknown as DisputeExtended['status'],
      resolutionNote: '',
      orderAmount: 800000,
      createdAt: '2026-10-26T10:00:00Z',
      updatedAt: '2026-10-26T10:00:00Z',
      resolvedBy: 'admin-1',
      resolvedAt: '2026-10-26T12:00:00Z',
    },
  ];

  test('filters by display code DISP-77777701', () => {
    const res = filterDisputes(sampleDisputes, 'DISP-77777701', 'all');
    assert.equal(res.length, 1);
    assert.equal(res[0].id, '77777777-7777-7777-7777-777777777701');
  });

  test('filters by partial short code 7704', () => {
    const res = filterDisputes(sampleDisputes, '7704', 'all');
    assert.equal(res.length, 1);
    assert.equal(res[0].id, '77777777-7777-7777-7777-777777777704');
  });

  test('filters by raw UUID', () => {
    const res = filterDisputes(sampleDisputes, '77777777-7777-7777-7777-777777777704', 'all');
    assert.equal(res.length, 1);
  });

  test('filters by status OPEN and RESOLVED', () => {
    const open = filterDisputes(sampleDisputes, '', 'OPEN');
    assert.equal(open.length, 1);
    assert.equal(open[0].id, '77777777-7777-7777-7777-777777777701');

    const resolved = filterDisputes(sampleDisputes, '', 'RESOLVED');
    assert.equal(resolved.length, 1);
    assert.equal(resolved[0].id, '77777777-7777-7777-7777-777777777704');
  });
});
