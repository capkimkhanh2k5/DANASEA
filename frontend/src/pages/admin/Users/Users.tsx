import React, { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Download,
  Lock,
  Mail,
  Shield,
  Unlock,
  UserCheck,
  UserPlus,
  Users as UsersIcon,
  Search,
} from 'lucide-react';
import { MOCK_ADMIN_USERS } from '../../../data/adminMockData';
import { adminUserService, getAdminUserError } from '../../../services/adminUserService';
import type { Role, User } from '../../../types';
import { AdminBadge, AdminState } from '../../../components/admin/AdminUI';
import {
  AdminButton,
  AdminFilterTabs,
  AdminNotice,
  AdminPreviewBanner,
} from '../../../components/admin/AdminWorkspace';
import { AdminDialog } from '../../../components/admin/AdminDialog';
import {
  createAdminAccount,
  filterUsers,
  toggleUserLock,
  updateUserRole,
  usersToCsv,
  type UserEmailFilter,
  type UserRoleFilter,
  type UserStatusFilter,
} from './usersModel';
import './Users.css';

export function Users() {
  const location = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1';

  const [users, setUsers] = useState<User[]>(() => demo ? MOCK_ADMIN_USERS : []);
  const [loading, setLoading] = useState(() => !demo);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState<UserRoleFilter>('all');
  const [statusFilter, setStatusFilter] = useState<UserStatusFilter>('all');
  const [emailFilter, setEmailFilter] = useState<UserEmailFilter>('all');

  // Detail Drawer State
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  // Lock / Unlock Modal State
  const [lockTarget, setLockTarget] = useState<User | null>(null);
  const [lockReason, setLockReason] = useState('');
  const [isLockModalOpen, setIsLockModalOpen] = useState(false);
  const [isSubmittingLock, setIsSubmittingLock] = useState(false);

  // Add Admin Modal State
  const [isAddAdminOpen, setIsAddAdminOpen] = useState(false);
  const [newAdminName, setNewAdminName] = useState('');
  const [newAdminEmail, setNewAdminEmail] = useState('');
  const [newAdminPhone, setNewAdminPhone] = useState('');
  const [addAdminError, setAddAdminError] = useState<string | null>(null);

  // Notice
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (demo) return;

    let isMounted = true;
    const controller = new AbortController();

    adminUserService.getUsers({ page: 0, size: 100 }, controller.signal)
      .then(res => {
        if (!isMounted) return;
        const mapped: User[] = (res.content || []).map(u => ({
          id: u.id,
          fullName: u.fullName || u.email.split('@')[0],
          email: u.email,
          phone: u.phone || '',
          role: u.role,
          avatarUrl: u.avatarUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(u.fullName || u.email)}&background=00646F&color=fff`,
          isEmailVerified: u.isEmailVerified,
          isLocked: u.isLocked,
          locale: 'vi',
          passwordHash: '',
          createdAt: u.createdAt,
          updatedAt: u.updatedAt,
        }));
        setUsers(mapped);
        setLoading(false);
      })
      .catch(err => {
        if (!isMounted) return;
        const info = getAdminUserError(err);
        setErrorMessage(info.message);
        setLoading(false);
      });

    return () => {
      isMounted = false;
      controller.abort();
    };
  }, [demo]);

  const visibleUsers = users;

  const filtered = useMemo(
    () => filterUsers(visibleUsers, searchQuery, roleFilter, statusFilter, emailFilter),
    [visibleUsers, searchQuery, roleFilter, statusFilter, emailFilter]
  );

  const customerCount = visibleUsers.filter(u => u.role === 'CUSTOMER').length;
  const vendorCount = visibleUsers.filter(u => u.role === 'VENDOR').length;
  const adminCount = visibleUsers.filter(u => u.role === 'ADMIN').length;
  const lockedCount = visibleUsers.filter(u => u.isLocked).length;

  const resetFilters = () => {
    setSearchQuery('');
    setRoleFilter('all');
    setStatusFilter('all');
    setEmailFilter('all');
  };

  const openDrawer = (user: User) => {
    setSelectedUser(user);
    setIsDrawerOpen(true);
  };

  const handleRoleChange = (newRole: Role) => {
    if (!selectedUser) return;
    if (!demo) {
      setNotice('Bật thao tác demo (?demo=1) để thử nghiệm thay đổi vai trò tài khoản.');
      return;
    }

    setUsers(prev => updateUserRole(prev, selectedUser.id, newRole, demo));
    setSelectedUser(prev => (prev ? { ...prev, role: newRole } : null));
    setNotice(`Đã chuyển vai trò của tài khoản "${selectedUser.fullName}" sang ${newRole}.`);
  };

  const promptLockToggle = (user: User, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    setLockTarget(user);
    setLockReason('');
    setIsLockModalOpen(true);
  };

  const confirmLockToggle = async () => {
    if (!lockTarget) return;
    if (demo) {
      const { updatedList, targetUser, nextLockedState } = toggleUserLock(
        users,
        lockTarget.id,
        lockReason,
        demo
      );

      setUsers(updatedList);
      if (selectedUser?.id === lockTarget.id && targetUser) {
        setSelectedUser(targetUser);
      }
      setIsLockModalOpen(false);
      setNotice(
        nextLockedState
          ? `Đã khóa tài khoản "${lockTarget.fullName}" trong bản demo.`
          : `Đã mở khóa tài khoản "${lockTarget.fullName}" trong bản demo.`
      );
      setLockTarget(null);
      return;
    }

    try {
      setIsSubmittingLock(true);
      if (lockTarget.isLocked) {
        await adminUserService.unlockUser(lockTarget.id);
      } else {
        await adminUserService.lockUser(lockTarget.id, lockReason);
      }
      const nextLocked = !lockTarget.isLocked;
      const updatedList = users.map(u =>
        u.id === lockTarget.id
          ? { ...u, isLocked: nextLocked, updatedAt: new Date().toISOString() }
          : u
      );
      setUsers(updatedList);
      if (selectedUser?.id === lockTarget.id) {
        setSelectedUser(prev => prev ? { ...prev, isLocked: nextLocked } : null);
      }
      setIsLockModalOpen(false);
      setNotice(
        nextLocked
          ? `Đã khóa tài khoản "${lockTarget.fullName}".`
          : `Đã mở khóa tài khoản "${lockTarget.fullName}".`
      );
      setLockTarget(null);
    } catch (err) {
      const info = getAdminUserError(err, true);
      setNotice(`Lỗi: ${info.message}`);
      setIsLockModalOpen(false);
    } finally {
      setIsSubmittingLock(false);
    }
  };

  const handleAddAdminSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!demo) {
      setAddAdminError('Bật thao tác demo (?demo=1) để thử tạo tài khoản quản trị viên.');
      return;
    }

    if (!newAdminName.trim() || !newAdminEmail.trim() || !newAdminPhone.trim()) {
      setAddAdminError('Vui lòng điền đầy đủ họ tên, email và số điện thoại.');
      return;
    }

    const { updatedList, createdUser } = createAdminAccount(
      users,
      { fullName: newAdminName, email: newAdminEmail, phone: newAdminPhone },
      demo
    );

    setUsers(updatedList);
    setIsAddAdminOpen(false);
    setNewAdminName('');
    setNewAdminEmail('');
    setNewAdminPhone('');
    setAddAdminError(null);

    if (createdUser) {
      setNotice(`Đã tạo tài khoản Quản trị viên mới: ${createdUser.fullName} (${createdUser.email}).`);
    }
  };

  const handleExportCSV = () => {
    try {
      const csvContent = usersToCsv(filtered);
      const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `DANASEA_Users_${new Date().toISOString().slice(0, 10)}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      setNotice(`Đã xuất ${filtered.length} tài khoản ra tệp CSV.`);
    } catch {
      setNotice('Không thể tạo tệp CSV. Vui lòng thử lại.');
    }
  };

  return (
    <div className="admin-workspace-page admin-users">
      {/* 1. Preview Banner */}
      <AdminPreviewBanner demo={demo} href={`${location.pathname}?demo=1`} />

      {/* 3. Summary Metrics */}
      <div className="admin-workspace-summary" aria-label="Tổng quan người dùng">
        {[
          { label: 'Tổng tài khoản', value: visibleUsers.length, detail: 'Trong hồ sơ mẫu', tab: 'all' as const },
          { label: 'Khách hàng', value: customerCount, detail: 'Tài khoản đặt dịch vụ', tab: 'CUSTOMER' as const },
          { label: 'Đối tác (Vendor)', value: vendorCount, detail: 'Đơn vị cung cấp tour', tab: 'VENDOR' as const },
          { label: 'Quản trị viên', value: adminCount, detail: 'Điều hành hệ thống', tab: 'ADMIN' as const },
          { label: 'Đang bị khóa', value: lockedCount, detail: 'Vô hiệu hóa truy cập', tab: 'locked' as const },
        ].map(metric => (
          <button
            key={metric.tab}
            type="button"
            className="admin-workspace-metric"
            aria-pressed={
              metric.tab === 'locked'
                ? statusFilter === 'locked'
                : roleFilter === metric.tab && statusFilter === 'all'
            }
            onClick={() => {
              if (metric.tab === 'locked') {
                setStatusFilter(statusFilter === 'locked' ? 'all' : 'locked');
                setRoleFilter('all');
              } else {
                setRoleFilter(metric.tab);
                setStatusFilter('all');
              }
            }}
          >
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
            <small>{metric.detail}</small>
          </button>
        ))}
      </div>

      {/* 4. Main List Section */}
      <section className="admin-users-list" aria-labelledby="users-list-title">
        <div className="admin-users-list-heading">
          <div>
            <h2 id="users-list-title">Danh sách tài khoản hệ thống</h2>
            <p>Xem thông tin người dùng, trạng thái bảo mật tài khoản và phân quyền vai trò.</p>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>
              {filtered.length} / {visibleUsers.length} tài khoản
            </span>
            <AdminButton variant="secondary" onClick={handleExportCSV}>
              <Download size={15} aria-hidden="true" /> Xuất CSV
            </AdminButton>
            <AdminButton variant="primary" onClick={() => setIsAddAdminOpen(true)}>
              <UserPlus size={16} aria-hidden="true" /> Thêm Quản trị viên
            </AdminButton>
          </div>
        </div>

        {/* Filter Tabs */}
        <AdminFilterTabs
          label="Lọc theo vai trò hoặc trạng thái"
          value={statusFilter === 'locked' ? 'locked' : roleFilter}
          onChange={val => {
            if (val === 'locked') {
              setStatusFilter('locked');
              setRoleFilter('all');
            } else {
              setStatusFilter('all');
              setRoleFilter(val as UserRoleFilter);
            }
          }}
          items={[
            { value: 'all', label: 'Tất cả', count: visibleUsers.length },
            { value: 'CUSTOMER', label: 'Khách hàng', count: customerCount },
            { value: 'VENDOR', label: 'Đối tác', count: vendorCount },
            { value: 'ADMIN', label: 'Quản trị viên', count: adminCount },
            { value: 'locked', label: 'Bị khóa', count: lockedCount },
          ]}
        />

        {/* Toolbar */}
        <div className="admin-workspace-toolbar">
          <label className="admin-search-field">
            <Search size={18} aria-hidden="true" />
            <input
              type="search"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              aria-label="Tìm theo tên, email, số điện thoại hoặc ID"
              placeholder="Tìm theo tên, email, số điện thoại hoặc mã ID..."
            />
          </label>

          <label className="admin-field admin-users-email-filter">
            <Mail size={16} aria-hidden="true" />
            <span>Xác thực email</span>
            <select
              value={emailFilter}
              onChange={e => setEmailFilter(e.target.value as UserEmailFilter)}
              aria-label="Lọc theo xác thực email"
            >
              <option value="all">Tất cả tình trạng</option>
              <option value="verified">Đã xác minh</option>
              <option value="unverified">Chưa xác minh</option>
            </select>
          </label>

          {(searchQuery || roleFilter !== 'all' || statusFilter !== 'all' || emailFilter !== 'all') && (
            <AdminButton onClick={resetFilters}>Xóa bộ lọc</AdminButton>
          )}
        </div>

        {/* Loading & Error States */}
        {loading && (
          <AdminState
            kind="empty"
            compact
            title="Đang tải danh sách người dùng..."
            description="Vui lòng đợi trong giây lát khi hệ thống kết nối máy chủ."
          />
        )}
        {errorMessage && !loading && (
          <AdminState
            kind="error"
            compact
            title="Không thể tải danh sách người dùng"
            description={errorMessage}
          />
        )}

        {/* Data Table */}
        {!loading && !errorMessage && (
        <div className="admin-users-table-scroll" role="region" aria-label="Bảng người dùng" tabIndex={0}>
          <table className="admin-workspace-table admin-users-table">
            <caption className="sr-only">Danh sách tài khoản người dùng DANASEA</caption>
            <thead>
              <tr>
                <th scope="col">Người dùng</th>
                <th scope="col">Số điện thoại</th>
                <th scope="col">Vai trò</th>
                <th scope="col">Xác thực Email</th>
                <th scope="col">Tình trạng</th>
                <th scope="col">Ngày đăng ký</th>
                <th scope="col">
                  <span className="sr-only">Thao tác</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(user => {
                const avatar =
                  user.avatarUrl ||
                  `https://ui-avatars.com/api/?name=${encodeURIComponent(user.fullName)}&background=00646F&color=fff`;
                return (
                  <tr key={user.id}>
                    <td>
                      <div className="admin-users-identity">
                        <img src={avatar} alt="" className="admin-users-avatar" />
                        <div>
                          <strong>{user.fullName}</strong>
                          <span>{user.email}</span>
                          <span className="admin-users-id-chip">#{user.id}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className="admin-users-phone">{user.phone || 'Chưa cung cấp'}</span>
                    </td>
                    <td>
                      <span className="admin-users-role-badge" data-role={user.role}>
                        {user.role === 'ADMIN' ? (
                          <Shield size={12} aria-hidden="true" />
                        ) : user.role === 'VENDOR' ? (
                          <UsersIcon size={12} aria-hidden="true" />
                        ) : (
                          <UserCheck size={12} aria-hidden="true" />
                        )}
                        {user.role}
                      </span>
                    </td>
                    <td>
                      {user.isEmailVerified ? (
                        <AdminBadge tone="teal">Đã xác minh</AdminBadge>
                      ) : (
                        <AdminBadge tone="neutral">Chưa xác minh</AdminBadge>
                      )}
                    </td>
                    <td>
                      <span className="admin-users-status-pill" data-locked={user.isLocked}>
                        <span className="admin-users-status-dot" aria-hidden="true" />
                        {user.isLocked ? 'Đã khóa' : 'Hoạt động'}
                      </span>
                    </td>
                    <td>
                      <small className="text-slate-500">
                        {user.createdAt
                          ? new Date(user.createdAt).toLocaleDateString('vi-VN', {
                              timeZone: 'Asia/Ho_Chi_Minh',
                            })
                          : '—'}
                      </small>
                    </td>
                    <td>
                      <div className="admin-users-actions-group">
                        <AdminButton
                          variant="secondary"
                          aria-label={`Xem chi tiết tài khoản ${user.fullName}`}
                          onClick={() => openDrawer(user)}
                        >
                          Chi tiết
                        </AdminButton>
                        <AdminButton
                          variant={user.isLocked ? 'secondary' : 'danger'}
                          aria-label={user.isLocked ? `Mở khóa ${user.fullName}` : `Khóa ${user.fullName}`}
                          onClick={e => promptLockToggle(user, e)}
                        >
                          {user.isLocked ? (
                            <>
                              <Unlock size={14} aria-hidden="true" /> Mở khóa
                            </>
                          ) : (
                            <>
                              <Lock size={14} aria-hidden="true" /> Khóa
                            </>
                          )}
                        </AdminButton>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        )}

        {/* Empty State */}
        {!loading && !errorMessage && filtered.length === 0 && (
          <AdminState
            kind="empty"
            compact
            title="Không tìm thấy tài khoản phù hợp"
            description="Hãy thử từ khóa tìm kiếm khác hoặc đặt lại bộ lọc để xem toàn bộ danh sách."
            action={<AdminButton onClick={resetFilters}>Xóa bộ lọc</AdminButton>}
          />
        )}

        <div className="admin-users-list-footer">
          Hiển thị {filtered.length} tài khoản {demo ? 'mẫu · Mọi thao tác cập nhật vai trò hoặc khóa tài khoản được giới hạn an toàn trong phiên demo.' : 'từ hệ thống cơ sở dữ liệu DANASEA.'}
        </div>
      </section>

      {/* Floating Notice */}
      {!isDrawerOpen && <AdminNotice message={notice} onDismiss={() => setNotice(null)} />}

      {/* 5. User Detail Drawer */}
      <AdminDialog
        open={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        variant="drawer"
        title={selectedUser?.fullName || 'Hồ sơ người dùng'}
        description="Thông tin tài khoản & Phân quyền quản trị"
        footer={
          selectedUser && (
            <div className="admin-users-drawer-footer">
              <AdminButton
                variant={selectedUser.isLocked ? 'secondary' : 'danger'}
                onClick={() => {
                  setIsDrawerOpen(false);
                  promptLockToggle(selectedUser);
                }}
              >
                {selectedUser.isLocked ? (
                  <>
                    <Unlock size={15} aria-hidden="true" /> Mở khóa tài khoản
                  </>
                ) : (
                  <>
                    <Lock size={15} aria-hidden="true" /> Khóa tài khoản
                  </>
                )}
              </AdminButton>
              <AdminButton onClick={() => setIsDrawerOpen(false)}>Đóng ngăn</AdminButton>
            </div>
          )
        }
      >
        {selectedUser && (
          <div className="admin-users-profile-detail">
            {/* Profile Header */}
            <div className="admin-users-profile-header">
              <img
                src={
                  selectedUser.avatarUrl ||
                  `https://ui-avatars.com/api/?name=${encodeURIComponent(selectedUser.fullName)}&background=00646F&color=fff`
                }
                alt=""
                className="admin-users-profile-avatar-lg"
              />
              <div>
                <h3>{selectedUser.fullName}</h3>
                <p>{selectedUser.email}</p>
                <div className="admin-users-profile-tags">
                  <span className="admin-users-role-badge" data-role={selectedUser.role}>
                    {selectedUser.role}
                  </span>
                  <span className="admin-users-status-pill" data-locked={selectedUser.isLocked}>
                    {selectedUser.isLocked ? 'Đã khóa' : 'Hoạt động'}
                  </span>
                </div>
              </div>
            </div>

            {/* Account Specs Grid */}
            <section className="admin-detail-section">
              <dl className="admin-detail-grid">
                <div>
                  <dt>Mã định danh User ID</dt>
                  <dd className="font-mono text-xs">{selectedUser.id}</dd>
                </div>
                <div>
                  <dt>Số điện thoại</dt>
                  <dd>{selectedUser.phone || 'Chưa cập nhật'}</dd>
                </div>
                <div>
                  <dt>Tình trạng Email</dt>
                  <dd>{selectedUser.isEmailVerified ? 'Đã xác minh an toàn' : 'Chưa xác minh'}</dd>
                </div>
                <div>
                  <dt>Ngôn ngữ hiển thị</dt>
                  <dd className="uppercase">{selectedUser.locale || 'vi'}</dd>
                </div>
                <div>
                  <dt>Ngày đăng ký tài khoản</dt>
                  <dd>
                    {selectedUser.createdAt
                      ? new Date(selectedUser.createdAt).toLocaleDateString('vi-VN', {
                          timeZone: 'Asia/Ho_Chi_Minh',
                        })
                      : '—'}
                  </dd>
                </div>
                <div>
                  <dt>Cập nhật gần nhất</dt>
                  <dd>
                    {selectedUser.updatedAt
                      ? new Date(selectedUser.updatedAt).toLocaleDateString('vi-VN', {
                          timeZone: 'Asia/Ho_Chi_Minh',
                        })
                      : '—'}
                  </dd>
                </div>
              </dl>
            </section>

            {/* Role Switcher Section */}
            <section className="admin-detail-section">
              <h4>Thay đổi phân quyền vai trò (Role)</h4>
              <p className="admin-detail-note">
                Chỉ thay đổi trong phiên demo. Không lưu vào cơ sở dữ liệu hoặc hệ thống xác thực Auth thật.
              </p>
              <div className="admin-users-role-selector">
                {[
                  {
                    role: 'CUSTOMER' as const,
                    title: 'CUSTOMER',
                    desc: 'Du khách đặt dịch vụ, thanh toán và gửi đánh giá.',
                  },
                  {
                    role: 'VENDOR' as const,
                    title: 'VENDOR',
                    desc: 'Nhà cung cấp tour biển, mở ca và soát vé check-in.',
                  },
                  {
                    role: 'ADMIN' as const,
                    title: 'ADMIN',
                    desc: 'Quản trị viên toàn quyền duyệt hồ sơ và điều hành sàn.',
                  },
                ].map(item => {
                  const isCurrent = selectedUser.role === item.role;
                  return (
                    <button
                      key={item.role}
                      type="button"
                      className="admin-users-role-opt"
                      data-selected={isCurrent}
                      onClick={() => handleRoleChange(item.role)}
                    >
                      <strong>{item.title}</strong>
                      <small>{item.desc}</small>
                    </button>
                  );
                })}
              </div>
            </section>
          </div>
        )}
      </AdminDialog>

      {/* 6. Lock / Unlock Confirmation Modal */}
      <AdminDialog
        open={isLockModalOpen && Boolean(lockTarget)}
        onClose={() => setIsLockModalOpen(false)}
        variant="modal"
        title={lockTarget?.isLocked ? 'Xác nhận mở khóa tài khoản' : 'Xác nhận khóa tài khoản'}
        description={demo ? 'Thao tác mô phỏng trong phiên xem thử demo' : 'Thao tác cập nhật trạng thái tài khoản vào hệ thống'}
        footer={
          <>
            <AdminButton disabled={isSubmittingLock} onClick={() => setIsLockModalOpen(false)}>Hủy bỏ</AdminButton>
            <AdminButton
              variant={lockTarget?.isLocked ? 'primary' : 'danger'}
              disabled={isSubmittingLock}
              onClick={confirmLockToggle}
            >
              {isSubmittingLock ? 'Đang xử lý...' : lockTarget?.isLocked ? 'Mở khóa tài khoản' : 'Xác nhận khóa'}
            </AdminButton>
          </>
        }
      >
        {lockTarget && (
          <div className="admin-users-confirm-dialog">
            <p>
              Bạn đang thao tác với người dùng: <strong>{lockTarget.fullName}</strong> ({lockTarget.email}).
            </p>
            <p>
              {lockTarget.isLocked
                ? 'Khi mở khóa, tài khoản sẽ có thể đăng nhập và tiếp tục thực hiện giao dịch bình thường.'
                : 'Khi khóa, người dùng sẽ bị tạm dừng quyền truy cập và thực hiện thao tác trên hệ thống.'}
            </p>
            <label className="admin-field">
              <span>Lý do thao tác (ghi nhận cục bộ)</span>
              <textarea
                value={lockReason}
                onChange={e => setLockReason(e.target.value)}
                placeholder="Nhập lý do khóa hoặc mở khóa..."
                rows={3}
              />
            </label>
          </div>
        )}
      </AdminDialog>

      {/* 7. Add Admin Modal */}
      <AdminDialog
        open={isAddAdminOpen}
        onClose={() => setIsAddAdminOpen(false)}
        variant="modal"
        title="Thêm Quản trị viên Hệ thống"
        description="Tạo tài khoản ADMIN có quyền điều hành trong bản demo"
        footer={
          <>
            <AdminButton onClick={() => setIsAddAdminOpen(false)}>Hủy</AdminButton>
            <AdminButton variant="primary" onClick={handleAddAdminSubmit}>
              Tạo tài khoản ADMIN
            </AdminButton>
          </>
        }
      >
        <form onSubmit={handleAddAdminSubmit} className="admin-users-form-dialog">
          {addAdminError && <div className="admin-categories-form-error">{addAdminError}</div>}
          <label className="admin-field">
            <span>Họ và tên *</span>
            <input
              type="text"
              value={newAdminName}
              onChange={e => setNewAdminName(e.target.value)}
              placeholder="VD: Nguyễn Văn Hoàng"
              required
            />
          </label>
          <label className="admin-field">
            <span>Email công vụ (@danasea.vn) *</span>
            <input
              type="email"
              value={newAdminEmail}
              onChange={e => setNewAdminEmail(e.target.value)}
              placeholder="VD: hoang.admin@danasea.vn"
              required
            />
          </label>
          <label className="admin-field">
            <span>Số điện thoại *</span>
            <input
              type="tel"
              value={newAdminPhone}
              onChange={e => setNewAdminPhone(e.target.value)}
              placeholder="VD: 0905123456"
              required
            />
          </label>
        </form>
      </AdminDialog>
    </div>
  );
}

export default Users;
