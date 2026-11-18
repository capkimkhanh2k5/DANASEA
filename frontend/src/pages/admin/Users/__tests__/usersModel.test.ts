import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import { filterUsers, toggleUserLock, getUserInitials } from '../usersModel.ts';
import type { User } from '../../../../types/index.ts';

describe('usersModel & Users — avatar initials generation', () => {
  test('generates expected initials for PO test cases', () => {
    // PO case: Parenthetical note removal
    assert.equal(getUserInitials('Da Nang Outdoor Adventures (DNOA)'), 'OA');

    // PO case: Go N Chill SUP Club -> SC
    assert.equal(getUserInitials('Go N Chill SUP Club'), 'SC');

    // PO case: DaNaFi -> D
    assert.equal(getUserInitials('DaNaFi'), 'D');

    // PO case: Loca Travel Đà Nẵng -> ĐN
    assert.equal(getUserInitials('Loca Travel Đà Nẵng'), 'ĐN');

    // PO case: Nguyễn Hoàng Long -> HL
    assert.equal(getUserInitials('Nguyễn Hoàng Long'), 'HL');

    // Nguyễn Văn Khách -> VK
    assert.equal(getUserInitials('Nguyễn Văn Khách'), 'VK');

    // Danabeach Club (Điểm dịch vụ thể thao biển) -> DC
    assert.equal(getUserInitials('Danabeach Club (Điểm dịch vụ thể thao biển)'), 'DC');

    // Fallback on email when name is empty
    assert.equal(getUserInitials('', 'admin@danasea.vn'), 'A');
    assert.equal(getUserInitials('', ''), 'U');
  });
});

describe('usersModel — filterUsers and toggleUserLock', () => {
  const sampleUsers: User[] = [
    {
      id: '11111111-1111-1111-1111-111111111111',
      fullName: 'Quản Trị Viên Danasea',
      email: 'admin@danasea.vn',
      phone: '0905000001',
      passwordHash: '',
      role: 'ADMIN',
      avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150',
      isEmailVerified: true,
      isLocked: false,
      locale: 'vi',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    {
      id: '22222222-2222-2222-2222-222222222222',
      fullName: 'Công Ty Biển Xanh Danasea',
      email: 'vendor@danasea.vn',
      phone: '0905000002',
      passwordHash: '',
      role: 'VENDOR',
      avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150',
      isEmailVerified: true,
      isLocked: false,
      locale: 'vi',
      createdAt: '2026-01-02T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
    },
    {
      id: '33333333-3333-3333-3333-333333333333',
      fullName: 'Nguyễn Văn Khách',
      email: 'customer@danasea.vn',
      phone: '0905000003',
      passwordHash: '',
      role: 'CUSTOMER',
      avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
      isEmailVerified: true,
      isLocked: true,
      locale: 'vi',
      createdAt: '2026-01-03T00:00:00Z',
      updatedAt: '2026-01-03T00:00:00Z',
    },
  ];

  test('filters users by search query', () => {
    const res = filterUsers(sampleUsers, 'Quản Trị', 'all', 'all', 'all');
    assert.equal(res.length, 1);
    assert.equal(res[0].email, 'admin@danasea.vn');
  });

  test('filters users by role and lock status', () => {
    const admins = filterUsers(sampleUsers, '', 'ADMIN', 'all', 'all');
    assert.equal(admins.length, 1);

    const locked = filterUsers(sampleUsers, '', 'all', 'locked', 'all');
    assert.equal(locked.length, 1);
    assert.equal(locked[0].email, 'customer@danasea.vn');

    const active = filterUsers(sampleUsers, '', 'all', 'active', 'all');
    assert.equal(active.length, 2);
  });

  test('toggles lock state in demo mode', () => {
    const { updatedList, nextLockedState } = toggleUserLock(
      sampleUsers,
      '11111111-1111-1111-1111-111111111111',
      'Testing lock',
      true
    );
    assert.equal(nextLockedState, true);
    const updatedUser = updatedList.find(u => u.id === '11111111-1111-1111-1111-111111111111');
    assert.equal(updatedUser?.isLocked, true);
  });
});
