import type { Role, User } from '../../../types';

export type UserStatusFilter = 'all' | 'active' | 'locked';
export type UserEmailFilter = 'all' | 'verified' | 'unverified';
export type UserRoleFilter = 'all' | Role;

export function getUserInitials(name?: string, email?: string): string {
  let source = (name || '').trim();

  // 1. Remove parenthetical annotations like (DNOA), [note], etc.
  if (source.includes('(') || source.includes('[')) {
    const withoutParens = source
      .replace(/\([^)]*\)/g, ' ')
      .replace(/\[[^\]]*\]/g, ' ')
      .trim();
    if (withoutParens.length > 0) {
      source = withoutParens;
    } else {
      source = source.replace(/[()[\]{}]/g, ' ').trim();
    }
  }

  // 2. Extract words with valid unicode letters/digits, ignoring punctuation like commas, dashes, quotes
  const words = source
    .split(/\s+/)
    .map(w => w.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, ''))
    .filter(w => /[\p{L}\p{N}]/u.test(w));

  if (words.length === 0) {
    if (email && email.trim()) {
      const emailClean = email.trim().replace(/^[^\p{L}\p{N}]+/u, '');
      return (emailClean[0] || 'U').toUpperCase();
    }
    return 'U';
  }

  // 3. Take the last two words for clear, readable initials
  const selectedWords = words.slice(-2);
  const initials = selectedWords
    .map(w => {
      const match = w.match(/[\p{L}\p{N}]/u);
      return match ? match[0] : '';
    })
    .join('')
    .toUpperCase();

  return initials || 'U';
}

export function filterUsers(
  users: User[],
  query: string,
  role: UserRoleFilter,
  status: UserStatusFilter,
  emailFilter: UserEmailFilter
): User[] {
  const search = query.trim().toLowerCase();
  return users.filter(user => {
    const matchesSearch =
      !search ||
      user.fullName.toLowerCase().includes(search) ||
      user.email.toLowerCase().includes(search) ||
      user.phone.includes(search) ||
      user.id.toLowerCase().includes(search);

    const matchesRole = role === 'all' || user.role === role;

    const matchesStatus =
      status === 'all' ||
      (status === 'active' && !user.isLocked) ||
      (status === 'locked' && user.isLocked);

    const matchesEmail =
      emailFilter === 'all' ||
      (emailFilter === 'verified' && user.isEmailVerified) ||
      (emailFilter === 'unverified' && !user.isEmailVerified);

    return matchesSearch && matchesRole && matchesStatus && matchesEmail;
  });
}

export function updateUserRole(
  users: User[],
  userId: string,
  newRole: Role,
  demo: boolean
): User[] {
  if (!demo) return users;
  return users.map(u =>
    u.id === userId
      ? { ...u, role: newRole, updatedAt: new Date().toISOString() }
      : u
  );
}

export function toggleUserLock(
  users: User[],
  userId: string,
  reason: string,
  demo: boolean
): { updatedList: User[]; targetUser: User | null; nextLockedState: boolean } {
  const target = users.find(u => u.id === userId);
  if (!demo || !target) {
    return { updatedList: users, targetUser: target || null, nextLockedState: target ? !target.isLocked : false };
  }

  const nextLocked = !target.isLocked;
  const updatedList = users.map(u =>
    u.id === userId
      ? { ...u, isLocked: nextLocked, updatedAt: new Date().toISOString() }
      : u
  );

  return {
    updatedList,
    targetUser: { ...target, isLocked: nextLocked },
    nextLockedState: nextLocked,
  };
}

export function createAdminAccount(
  users: User[],
  input: { fullName: string; email: string; phone: string },
  demo: boolean
): { updatedList: User[]; createdUser: User | null } {
  if (!demo) return { updatedList: users, createdUser: null };

  const now = new Date().toISOString();
  const newAdmin: User = {
    id: `usr-adm-${Date.now()}`,
    fullName: input.fullName.trim(),
    email: input.email.trim(),
    phone: input.phone.trim(),
    passwordHash: 'argon2_temp_hash',
    role: 'ADMIN',
    avatarUrl: `https://ui-avatars.com/api/?name=${encodeURIComponent(input.fullName.trim())}&background=00646F&color=fff`,
    isEmailVerified: true,
    isLocked: false,
    locale: 'vi',
    createdAt: now,
    updatedAt: now,
  };

  return {
    updatedList: [newAdmin, ...users],
    createdUser: newAdmin,
  };
}

export function usersToCsv(users: User[]): string {
  const quote = (val: string | number) => `"${String(val).replaceAll('"', '""')}"`;
  const header = ['ID', 'Họ Tên', 'Email', 'Số Điện Thoại', 'Vai Trò', 'Xác Thực Email', 'Tình Trạng', 'Ngày Tạo'].map(quote).join(',');
  const rows = users.map(u => [
    u.id,
    u.fullName,
    u.email,
    u.phone,
    u.role,
    u.isEmailVerified ? 'Đã xác minh' : 'Chưa xác minh',
    u.isLocked ? 'Đã khóa' : 'Hoạt động',
    u.createdAt || '',
  ].map(quote).join(','));

  return [header, ...rows].join('\r\n');
}
