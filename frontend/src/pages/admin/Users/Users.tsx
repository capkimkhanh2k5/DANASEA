import React, { useState, useMemo, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { adminUserService, getAdminUserError } from '../../../services/adminUserService';
import { useAuthStore } from '../../../store/useAuthStore';
import type { Role } from '../../../types';
import type { AdminUserSummaryDto, AdminUserDetailDto } from '../../../types/adminUser';

export function Users() {
  const accessToken = useAuthStore(state => state.accessToken);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const userRole = useAuthStore(state => state.user?.role);
  const canAccess = isAuthenticated && !!accessToken && userRole === 'ADMIN';

  // State: Data & Pagination
  const [users, setUsers] = useState<AdminUserSummaryDto[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(0);
  const pageSize = 10;

  // Filters
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('all');
  const [emailFilter, setEmailFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<string>('all');

  // Status & Lifecycle
  const [loading, setLoading] = useState(true);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [needsReload, setNeedsReload] = useState(false);
  const [reloadVersion, setReloadVersion] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const inFlight = useRef<symbol | null>(null);
  const pageActive = useRef(false);
  const generation = useRef(0);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const controlsDisabled = !canAccess || loading || saving || needsReload;

  // Selected user for Detail Drawer
  const [selectedUser, setSelectedUser] = useState<AdminUserDetailDto | AdminUserSummaryDto | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  // Lock / Unlock Dialog
  const [lockTargetUser, setLockTargetUser] = useState<AdminUserSummaryDto | null>(null);
  const [lockReason, setLockReason] = useState('');
  const [isLockModalOpen, setIsLockModalOpen] = useState(false);

  // Add Admin Modal
  const [isAddAdminOpen, setIsAddAdminOpen] = useState(false);
  const [newAdminName, setNewAdminName] = useState('');
  const [newAdminEmail, setNewAdminEmail] = useState('');
  const [newAdminPhone, setNewAdminPhone] = useState('');

  // Toast notification
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToastMessage(msg);
    toastTimer.current = setTimeout(() => setToastMessage(null), 3000);
  };

  // Debounce search query (350ms)
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(searchQuery.trim());
      setPage(0);
    }, 350);
    return () => clearTimeout(handler);
  }, [searchQuery]);

  // Load paginated users from Backend
  useEffect(() => {
    pageActive.current = true;
    const controller = new AbortController();
    const current = ++generation.current;

    const load = async () => {
      setLoading(true);
      setErrorMessage(null);

      if (!canAccess) {
        setErrorMessage(!isAuthenticated || !accessToken
          ? 'Vui lòng đăng nhập bằng tài khoản Admin để quản trị người dùng.'
          : 'Bạn không có quyền quản lý người dùng. Cần tài khoản Admin.');
        setLoading(false);
        return;
      }

      try {
        const queryParams = {
          page,
          size: pageSize,
          role: roleFilter === 'all' ? undefined : (roleFilter as Role),
          isLocked: statusFilter === 'all' ? undefined : statusFilter === 'locked',
          search: debouncedSearch || undefined,
        };

        const pageData = await adminUserService.getUsers(queryParams, controller.signal);
        if (controller.signal.aborted || generation.current !== current) return;

        setUsers(pageData.content || []);
        setTotalElements(pageData.totalElements || 0);
        setTotalPages(Math.max(1, pageData.totalPages || 1));
        setHasLoaded(true);
        setNeedsReload(false);
      } catch (err) {
        if (!controller.signal.aborted && generation.current === current) {
          setErrorMessage(getAdminUserError(err).message);
        }
      } finally {
        if (!controller.signal.aborted && generation.current === current) {
          setLoading(false);
        }
      }
    };

    void load();

    return () => {
      pageActive.current = false;
      controller.abort();
      generation.current = current + 1;
      if (toastTimer.current) clearTimeout(toastTimer.current);
    };
  }, [canAccess, isAuthenticated, accessToken, page, roleFilter, statusFilter, debouncedSearch, reloadVersion]);

  // Client-side filtering for emailFilter on top of the server page
  const displayedUsers = useMemo(() => {
    return users.filter(u => {
      const matchEmail =
        emailFilter === 'all' ||
        (emailFilter === 'verified' && u.isEmailVerified) ||
        (emailFilter === 'unverified' && !u.isEmailVerified);
      return matchEmail;
    });
  }, [users, emailFilter]);

  // View detail in Drawer
  const handleOpenDetail = async (user: AdminUserSummaryDto) => {
    setSelectedUser(user);
    setIsDrawerOpen(true);
    setDetailLoading(true);
    try {
      const detail = await adminUserService.getUserDetail(user.id);
      setSelectedUser(detail);
    } catch {
      // Keep summary data if detail endpoint fails
    } finally {
      setDetailLoading(false);
    }
  };

  // Open Lock / Unlock Modal
  const handleOpenLock = (user: AdminUserSummaryDto) => {
    setLockTargetUser(user);
    setLockReason('');
    setIsLockModalOpen(true);
  };

  // Confirm Lock / Unlock Mutation
  const handleConfirmLockToggle = async () => {
    if (!lockTargetUser || controlsDisabled || inFlight.current) return;
    const isNowLocking = !lockTargetUser.isLocked;
    const targetId = lockTargetUser.id;
    const targetName = lockTargetUser.fullName || lockTargetUser.email;

    const requestId = Symbol('lockMutation');
    inFlight.current = requestId;
    const currentGen = generation.current;
    setSaving(true);
    setErrorMessage(null);

    try {
      if (isNowLocking) {
        await adminUserService.lockUser(targetId, lockReason);
      } else {
        await adminUserService.unlockUser(targetId);
      }

      if (generation.current !== currentGen) return;

      // Update state locally upon server confirmation
      setUsers(prev =>
        prev.map(u => (u.id === targetId ? { ...u, isLocked: isNowLocking } : u))
      );

      if (selectedUser?.id === targetId) {
        setSelectedUser(prev => prev ? { ...prev, isLocked: isNowLocking } : null);
      }

      setIsLockModalOpen(false);
      showToast(
        isNowLocking
          ? `Đã khóa tài khoản ${targetName} thành công.`
          : `Đã mở khóa tài khoản ${targetName} thành công.`
      );
    } catch (err) {
      if (generation.current !== currentGen) return;
      const failure = getAdminUserError(err, true);
      setErrorMessage(failure.message);
      setNeedsReload(failure.outcomeUnknown);
    } finally {
      if (inFlight.current === requestId) {
        inFlight.current = null;
        if (pageActive.current) setSaving(false);
      }
    }
  };

  // Export CSV of currently displayed users
  const handleExportCSV = () => {
    const headers = "ID,Họ Tên,Email,Số Điện Thoại,Vai Trò,Xác Thực Email,Trạng Thái Khóa,Ngày Tạo\n";
    const rows = displayedUsers.map(u =>
      `"${u.id}","${u.fullName || ''}","${u.email}","${u.phone || ''}","${u.role}","${u.isEmailVerified ? 'Có' : 'Chưa'}","${u.isLocked ? 'Khóa' : 'Hoạt động'}","${u.createdAt || ''}"`
    ).join("\n");

    const blob = new Blob([headers + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `DANASEA_Users_Page${page + 1}_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast("Đã xuất danh sách người dùng trang hiện tại ra tập tin CSV!");
  };

  return (
    <main className="w-full pt-16 bg-surface px-space-xl pb-space-2xl min-h-screen">
      <div className="flex flex-col w-full animate-fade-in-up">
        {/* Toast Notification */}
        {toastMessage && (
          <div role="status" className="fixed top-20 right-8 z-50 flex items-center gap-3 px-4 py-3 rounded-xl bg-primary text-on-primary shadow-xl animate-fade-in-up">
            <span className="material-symbols-outlined text-[20px]">check_circle</span>
            <span className="font-label-md text-label-md font-semibold">{toastMessage}</span>
          </div>
        )}

        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 mb-4 border-b border-slate-200">
          <div>
            <h1 className="text-xl md:text-2xl font-bold text-slate-800 tracking-tight">
              Quản lý Người dùng
            </h1>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <button
              onClick={handleExportCSV}
              disabled={displayedUsers.length === 0}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 text-sm font-medium border border-slate-200 shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="material-symbols-outlined text-[18px]">file_download</span>
              <span>Xuất CSV (Trang {page + 1})</span>
            </button>
            <button
              onClick={() => setIsAddAdminOpen(true)}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-primary text-white text-sm font-semibold hover:bg-primary-container transition-colors shadow-xs"
            >
              <span className="material-symbols-outlined text-[18px]">person_add</span>
              <span>Thêm Quản trị viên</span>
            </button>
          </div>
        </div>

        {/* Error Alert */}
        {errorMessage && (
          <div role="alert" className="mb-4 p-space-md bg-white rounded-xl border border-red-200 text-sm text-red-700 flex items-center justify-between gap-4">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[20px] text-red-500">error</span>
              <p>{errorMessage}</p>
            </div>
            {canAccess && (
              <button
                type="button"
                disabled={loading || saving}
                onClick={() => setReloadVersion(v => v + 1)}
                className="px-3 py-1 rounded-lg bg-primary text-white text-xs font-semibold hover:bg-primary-container transition-colors"
              >
                Tải lại danh sách
              </button>
            )}
          </div>
        )}

        {/* Filters Bar with Role Quick Tabs */}
        <div className="flex flex-col gap-3 mb-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            {/* Quick Role Tabs */}
            <div className="flex items-center gap-1 p-1 bg-slate-100 rounded-xl border border-slate-200 text-xs font-medium">
              <button
                onClick={() => { setRoleFilter('all'); setPage(0); }}
                className={`px-3 py-1.5 rounded-lg transition-colors ${roleFilter === 'all' ? 'bg-primary text-white font-bold shadow-xs' : 'text-slate-600 hover:text-slate-900'}`}
              >
                Tất cả
              </button>
              <button
                onClick={() => { setRoleFilter('CUSTOMER'); setPage(0); }}
                className={`px-3 py-1.5 rounded-lg transition-colors ${roleFilter === 'CUSTOMER' ? 'bg-primary text-white font-bold shadow-xs' : 'text-slate-600 hover:text-slate-900'}`}
              >
                Khách hàng
              </button>
              <button
                onClick={() => { setRoleFilter('VENDOR'); setPage(0); }}
                className={`px-3 py-1.5 rounded-lg transition-colors ${roleFilter === 'VENDOR' ? 'bg-primary text-white font-bold shadow-xs' : 'text-slate-600 hover:text-slate-900'}`}
              >
                Đối tác
              </button>
              <button
                onClick={() => { setRoleFilter('ADMIN'); setPage(0); }}
                className={`px-3 py-1.5 rounded-lg transition-colors ${roleFilter === 'ADMIN' ? 'bg-primary text-white font-bold shadow-xs' : 'text-slate-600 hover:text-slate-900'}`}
              >
                Quản trị
              </button>
            </div>

            <div className="flex items-center gap-2 text-xs text-slate-500">
              <span>Tổng: <strong className="text-slate-800">{hasLoaded ? totalElements : '—'}</strong> tài khoản</span>
              <span>•</span>
              <span>Hiển thị trang: <strong>{hasLoaded ? displayedUsers.length : '—'}</strong> kết quả</span>
            </div>
          </div>

          {/* Search and Secondary Dropdowns */}
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[220px]">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-[18px]">search</span>
              <input
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full h-9 pl-9 pr-3 rounded-lg bg-slate-50 text-slate-800 text-sm placeholder:text-slate-400 focus:outline-none focus:bg-white focus:ring-1 focus:ring-primary border border-slate-200"
                placeholder="Tìm theo tên, email, số điện thoại..."
                type="text"
              />
            </div>

            <select
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
              className="h-9 px-3 rounded-lg bg-slate-50 text-slate-700 text-sm border border-slate-200 focus:outline-none focus:ring-1 focus:ring-primary cursor-pointer"
            >
              <option value="all">Trạng thái: Tất cả</option>
              <option value="active">Đang hoạt động</option>
              <option value="locked">Bị khóa</option>
            </select>

            <select
              value={emailFilter}
              onChange={(e) => setEmailFilter(e.target.value)}
              className="h-9 px-3 rounded-lg bg-slate-50 text-slate-700 text-sm border border-slate-200 focus:outline-none focus:ring-1 focus:ring-primary cursor-pointer"
            >
              <option value="all">Email: Tất cả</option>
              <option value="verified">Đã xác thực</option>
              <option value="unverified">Chưa xác thực</option>
            </select>
          </div>
        </div>

        {/* Main Users Table Section */}
        <div className="bg-white rounded-xl shadow-xs overflow-hidden mb-4 border border-slate-200">
          <div className="overflow-x-auto">
            <table aria-busy={loading || saving} className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50 text-slate-700 text-xs font-semibold border-b border-slate-200">
                  <th className="py-3 px-4 border-r border-slate-200 whitespace-nowrap">Người dùng (Họ tên / Email)</th>
                  <th className="py-3 px-3 border-r border-slate-200 whitespace-nowrap">Số điện thoại</th>
                  <th className="py-3 px-3 border-r border-slate-200 whitespace-nowrap">Vai trò (Role)</th>
                  <th className="py-3 px-3 border-r border-slate-200 whitespace-nowrap">Xác thực Email</th>
                  <th className="py-3 px-3 border-r border-slate-200 whitespace-nowrap">Tình trạng</th>
                  <th className="py-3 px-3 border-r border-slate-200 whitespace-nowrap">Ngày đăng ký</th>
                  <th className="py-3 px-4 text-center whitespace-nowrap">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 text-xs text-slate-700">
                {loading && (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-slate-500" role="status">
                      <div className="flex items-center justify-center gap-2">
                        <span className="material-symbols-outlined animate-spin text-primary text-[20px]">progress_activity</span>
                        <span>Đang đồng bộ dữ liệu người dùng từ máy chủ...</span>
                      </div>
                    </td>
                  </tr>
                )}
                {!loading && displayedUsers.length === 0 && (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-slate-400" role="status">
                      {searchQuery ? 'Không tìm thấy tài khoản người dùng phù hợp với từ khóa.' : 'Chưa có dữ liệu người dùng.'}
                    </td>
                  </tr>
                )}
                {!loading && displayedUsers.map(user => (
                  <tr key={user.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3.5 px-4 border-r border-slate-200">
                      <div className="flex items-center gap-space-md">
                        <img
                          src={user.avatarUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(user.fullName || user.email)}&background=087F8C&color=fff`}
                          alt={user.fullName || user.email}
                          className="w-10 h-10 rounded-full object-cover shadow-xs flex-shrink-0 border border-slate-200"
                        />
                        <div className="flex flex-col min-w-0">
                          <span className="font-bold text-slate-800 truncate">{user.fullName || 'Chưa cập nhật'}</span>
                          <span className="text-slate-500 text-[11px] truncate">{user.email}</span>
                        </div>
                      </div>
                    </td>

                    <td className="py-3.5 px-3 font-mono text-xs text-slate-700 border-r border-slate-200 whitespace-nowrap">
                      {user.phone || '—'}
                    </td>

                    <td className="py-3.5 px-3 border-r border-slate-200 whitespace-nowrap">
                      <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold ${
                        user.role === 'ADMIN'
                          ? 'bg-purple-50 text-purple-700 border border-purple-200'
                          : user.role === 'VENDOR'
                          ? 'bg-primary/10 text-primary border border-primary/20'
                          : 'bg-slate-100 text-slate-700 border border-slate-200'
                      }`}>
                        <span className="material-symbols-outlined text-[13px]">
                          {user.role === 'ADMIN' ? 'shield_person' : user.role === 'VENDOR' ? 'sailing' : 'person'}
                        </span>
                        {user.role}
                      </span>
                    </td>

                    <td className="py-3.5 px-3 border-r border-slate-200 whitespace-nowrap">
                      {user.isEmailVerified ? (
                        <span className="inline-flex items-center gap-1 text-primary text-[11px] font-bold bg-primary/10 border border-primary/20 px-2 py-0.5 rounded">
                          <span className="material-symbols-outlined text-[14px]">verified</span> ĐÃ XÁC THỰC
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-slate-500 text-[11px] bg-slate-100 border border-slate-200 px-2 py-0.5 rounded">
                          CHƯA XÁC THỰC
                        </span>
                      )}
                    </td>

                    <td className="py-3.5 px-3 border-r border-slate-200 whitespace-nowrap">
                      {user.isLocked ? (
                        <span className="inline-flex items-center gap-1.5 text-[11px] text-red-700 font-bold bg-red-50 border border-red-200 px-2.5 py-0.5 rounded-full">
                          <span className="w-1.5 h-1.5 rounded-full bg-red-500"></span> ĐÃ KHÓA
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 text-[11px] text-primary font-bold bg-primary/10 border border-primary/20 px-2.5 py-0.5 rounded-full">
                          <span className="w-1.5 h-1.5 rounded-full bg-primary"></span> HOẠT ĐỘNG
                        </span>
                      )}
                    </td>

                    <td className="py-3.5 px-3 text-slate-500 text-xs border-r border-slate-200 whitespace-nowrap">
                      {user.createdAt ? new Date(user.createdAt).toLocaleDateString('vi-VN') : '—'}
                    </td>

                    <td className="py-3.5 px-4 text-center whitespace-nowrap">
                      <div className="flex items-center justify-center gap-1.5">
                        <button
                          onClick={() => handleOpenDetail(user)}
                          disabled={controlsDisabled}
                          className="px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 text-xs font-semibold transition-colors disabled:opacity-50"
                        >
                          Xem chi tiết
                        </button>
                        <button
                          onClick={() => handleOpenLock(user)}
                          disabled={controlsDisabled}
                          className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition-colors border disabled:opacity-50 ${
                            user.isLocked
                              ? 'bg-primary text-white border-primary hover:bg-primary-container'
                              : 'bg-red-50 hover:bg-red-100 text-red-700 border-red-200'
                          }`}
                        >
                          {user.isLocked ? 'Mở khóa' : 'Khóa'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Server-Side Pagination Bar */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-3 bg-slate-50 border-t border-slate-200 text-xs text-slate-600">
            <div>
              Hiển thị <strong>{totalElements > 0 ? page * pageSize + 1 : 0}</strong> - <strong>{Math.min((page + 1) * pageSize, totalElements)}</strong> trong tổng số <strong>{totalElements}</strong> tài khoản
            </div>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0 || loading || saving}
                className="px-3 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 font-medium disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Trang trước
              </button>
              <span className="px-3 py-1.5 font-semibold text-slate-800">
                Trang {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1 || loading || saving}
                className="px-3 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 font-medium disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Trang sau
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* USER DETAIL DRAWER (Portaled to body) */}
      {isDrawerOpen && selectedUser && createPortal(
        <div className="fixed inset-0 z-[9999] overflow-hidden bg-scrim/40 backdrop-blur-xs flex justify-end animate-fade-in-up">
          <div className="bg-surface-container-lowest w-full max-w-xl h-full shadow-2xl flex flex-col justify-between border-l border-outline-variant/30">
            {/* Header */}
            <div className="p-space-lg border-b border-outline-variant/20 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <img
                  src={selectedUser.avatarUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(selectedUser.fullName || selectedUser.email)}&background=087F8C&color=fff`}
                  alt={selectedUser.fullName || selectedUser.email}
                  className="w-12 h-12 rounded-full object-cover border border-outline-variant"
                />
                <div>
                  <h3 className="font-headline-sm text-headline-sm font-bold text-on-surface">{selectedUser.fullName || 'Người dùng'}</h3>
                  <span className="font-body-sm text-body-sm text-outline">{selectedUser.email}</span>
                </div>
              </div>
              <button
                onClick={() => setIsDrawerOpen(false)}
                className="w-9 h-9 rounded-full bg-surface-container flex items-center justify-center text-outline hover:text-on-surface"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            {/* Content Details */}
            <div className="p-space-lg overflow-y-auto flex-1 space-y-space-lg font-body-md text-body-md">
              {detailLoading && (
                <div className="text-center py-2 text-xs text-primary font-medium flex items-center justify-center gap-1">
                  <span className="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>
                  <span>Đang tải thông tin chi tiết tài khoản...</span>
                </div>
              )}

              <div className="bg-surface p-space-md rounded-2xl border border-outline-variant/30 flex items-center justify-between">
                <div>
                  <span className="text-xs text-outline uppercase font-label-sm block">Định danh User ID</span>
                  <span className="font-mono text-sm font-bold text-on-surface">{selectedUser.id}</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className={`px-3 py-1 rounded-full font-label-sm text-label-sm font-bold ${
                    selectedUser.role === 'ADMIN' ? 'bg-primary/20 text-primary' :
                    selectedUser.role === 'VENDOR' ? 'bg-secondary/20 text-secondary' : 'bg-surface-container text-on-surface'
                  }`}>
                    {selectedUser.role}
                  </span>
                  <span className={`px-2.5 py-1 rounded-full font-label-sm text-label-sm font-bold ${
                    selectedUser.isLocked ? 'bg-red-100 text-red-700 border border-red-200' : 'bg-primary-container text-on-primary-container'
                  }`}>
                    {selectedUser.isLocked ? 'Đã khóa' : 'Hoạt động'}
                  </span>
                </div>
              </div>

              {/* Thông tin tài khoản */}
              <div className="space-y-space-sm">
                <h4 className="font-label-lg text-label-lg font-bold text-on-surface flex items-center gap-2">
                  <span className="material-symbols-outlined text-primary text-[20px]">person</span>
                  Thông tin tài khoản
                </h4>
                <div className="grid grid-cols-2 gap-space-md bg-surface p-space-md rounded-2xl border border-outline-variant/30">
                  <div>
                    <span className="text-xs text-outline block">Số điện thoại</span>
                    <span className="font-semibold text-on-surface">{selectedUser.phone || 'Chưa cung cấp'}</span>
                  </div>
                  <div>
                    <span className="text-xs text-outline block">Xác thực email</span>
                    <span className="font-semibold text-primary">
                      {selectedUser.isEmailVerified ? 'Đã xác minh' : 'Chưa xác minh'}
                    </span>
                  </div>
                  <div>
                    <span className="text-xs text-outline block">Ngôn ngữ giao diện</span>
                    <span className="font-semibold text-on-surface uppercase">{('locale' in selectedUser && selectedUser.locale) || 'vi'}</span>
                  </div>
                  <div>
                    <span className="text-xs text-outline block">Ngày đăng ký</span>
                    <span className="font-semibold text-on-surface">
                      {selectedUser.createdAt ? new Date(selectedUser.createdAt).toLocaleDateString('vi-VN') : '—'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Phân quyền vai trò (Backend limitation note) */}
              <div className="space-y-space-sm">
                <div className="flex items-center justify-between">
                  <h4 className="font-label-lg text-label-lg font-bold text-on-surface flex items-center gap-2">
                    <span className="material-symbols-outlined text-primary text-[20px]">admin_panel_settings</span>
                    Phân quyền vai trò
                  </h4>
                  <span className="text-[11px] text-slate-500 italic bg-slate-100 px-2 py-0.5 rounded border border-slate-200">
                    Chỉ xem (Backend chưa hỗ trợ API đổi vai trò)
                  </span>
                </div>
                <div className="flex gap-2">
                  {(['CUSTOMER', 'VENDOR', 'ADMIN'] as const).map((r) => (
                    <button
                      key={r}
                      disabled
                      aria-label={`Vai trò ${r}`}
                      className={`flex-1 py-2 rounded-xl font-label-md text-label-md font-bold border cursor-not-allowed ${
                        selectedUser.role === r
                          ? 'bg-primary text-on-primary border-primary shadow-sm'
                          : 'bg-surface border-outline-variant/40 text-on-surface opacity-40'
                      }`}
                    >
                      {r}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Footer actions */}
            <div className="p-space-lg border-t border-outline-variant/20 flex items-center justify-between gap-space-md bg-surface">
              <button
                onClick={() => {
                  const summary = users.find(u => u.id === selectedUser.id) || {
                    id: selectedUser.id,
                    email: selectedUser.email,
                    phone: selectedUser.phone,
                    fullName: selectedUser.fullName,
                    role: selectedUser.role,
                    avatarUrl: selectedUser.avatarUrl,
                    isEmailVerified: selectedUser.isEmailVerified,
                    isLocked: selectedUser.isLocked,
                    createdAt: selectedUser.createdAt || '',
                    updatedAt: selectedUser.updatedAt || '',
                  };
                  setLockTargetUser(summary);
                  setIsLockModalOpen(true);
                  setIsDrawerOpen(false);
                }}
                className={`px-4 py-2.5 rounded-xl font-label-md text-label-md font-bold flex items-center gap-2 transition-colors ${
                  selectedUser.isLocked
                    ? 'bg-primary/10 text-primary hover:bg-primary/20'
                    : 'bg-red-50 text-red-700 hover:bg-red-100'
                }`}
              >
                <span className="material-symbols-outlined text-[18px]">
                  {selectedUser.isLocked ? 'lock_open' : 'lock'}
                </span>
                {selectedUser.isLocked ? 'Mở khóa tài khoản' : 'Khóa tài khoản này'}
              </button>

              <button
                onClick={() => setIsDrawerOpen(false)}
                className="px-6 py-2.5 rounded-xl bg-surface-container hover:bg-surface-container-high font-label-md text-label-md font-semibold text-on-surface"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* LOCK / UNLOCK MODAL (Portaled to body) */}
      {isLockModalOpen && lockTargetUser && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-scrim/50 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-3xl p-space-xl max-w-lg w-full shadow-2xl border border-outline-variant">
            <div className="flex items-center gap-space-md mb-space-md">
              <div className={`w-12 h-12 rounded-2xl flex items-center justify-center ${
                lockTargetUser.isLocked ? 'bg-primary/10 text-primary' : 'bg-red-100 text-red-700'
              }`}>
                <span className="material-symbols-outlined text-[28px]">
                  {lockTargetUser.isLocked ? 'lock_open' : 'lock'}
                </span>
              </div>
              <div>
                <h3 className="font-headline-md text-headline-md text-on-surface font-bold">
                  {lockTargetUser.isLocked ? 'Mở Khóa Tài Khoản' : 'Xác Nhận Khóa Tài Khoản'}
                </h3>
                <span className="font-body-sm text-body-sm text-outline">{lockTargetUser.email}</span>
              </div>
            </div>

            <p className="font-body-md text-body-md text-on-surface-variant mb-space-md">
              {lockTargetUser.isLocked
                ? 'Hành động này sẽ khôi phục quyền đăng nhập và sử dụng dịch vụ trên nền tảng DANASEA cho người dùng này.'
                : 'Hành động này sẽ vô hiệu hóa quyền truy cập vào DANASEA, ngăn chặn đăng nhập vào hệ thống.'}
            </p>

            <div className="mb-space-lg">
              <label className="block font-label-md text-label-md text-on-surface mb-1 font-semibold">
                Lý do thao tác {lockTargetUser.isLocked ? '(Tùy chọn)' : '(Ghi nhận kiểm toán)'}
              </label>
              <textarea
                value={lockReason}
                onChange={(e) => setLockReason(e.target.value)}
                disabled={saving}
                className="w-full p-3 rounded-xl bg-surface text-on-surface font-body-md text-body-md placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary shadow-sm resize-none border border-outline-variant/30"
                placeholder={lockTargetUser.isLocked ? "Nhập lý do mở khóa (nếu có)..." : "Nhập lý do khóa tài khoản..."}
                rows={3}
              />
            </div>

            <div className="flex items-center justify-end gap-space-sm">
              <button
                type="button"
                onClick={() => setIsLockModalOpen(false)}
                disabled={saving}
                className="px-space-lg py-2.5 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface font-label-md text-label-md font-semibold disabled:opacity-50"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleConfirmLockToggle}
                disabled={saving}
                className={`px-space-xl py-2.5 rounded-xl font-label-md text-label-md font-bold shadow-sm transition-colors flex items-center gap-1.5 disabled:opacity-50 ${
                  lockTargetUser.isLocked
                    ? 'bg-primary text-on-primary hover:bg-primary-container'
                    : 'bg-red-600 text-white hover:bg-red-700'
                }`}
              >
                {saving && <span className="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>}
                <span>{saving ? 'Đang xử lý...' : lockTargetUser.isLocked ? 'Xác nhận Mở khóa' : 'Xác nhận Khóa'}</span>
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* ADD ADMIN MODAL (Portaled to body - Note on Backend limitation) */}
      {isAddAdminOpen && createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-scrim/50 backdrop-blur-sm p-4 animate-scale-in">
          <div className="bg-surface-container-lowest rounded-3xl p-space-xl max-w-lg w-full shadow-2xl border border-outline-variant">
            <div className="flex items-center justify-between mb-space-md">
              <div className="flex items-center gap-space-sm">
                <div className="w-10 h-10 rounded-xl bg-primary text-on-primary flex items-center justify-center">
                  <span className="material-symbols-outlined text-[24px]">shield_person</span>
                </div>
                <div>
                  <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">Thêm Quản trị viên Hệ thống</h3>
                  <span className="font-body-sm text-body-sm text-outline">Tài khoản ADMIN quản trị điều hành</span>
                </div>
              </div>
              <button
                onClick={() => setIsAddAdminOpen(false)}
                className="w-8 h-8 rounded-full bg-surface-container flex items-center justify-center text-outline hover:text-on-surface"
              >
                <span className="material-symbols-outlined text-[18px]">close</span>
              </button>
            </div>

            <div className="p-3 mb-4 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-800 flex items-start gap-2">
              <span className="material-symbols-outlined text-[18px] text-amber-600 flex-shrink-0">info</span>
              <div>
                <strong>Giới hạn Backend hiện tại:</strong> API Backend chưa hỗ trợ tạo tài khoản Admin trực tiếp qua màn hình này. Tài khoản Quản trị viên được khởi tạo và cấp quyền qua hệ thống Xác thực nội bộ (Auth Server / Database seed).
              </div>
            </div>

            <form className="space-y-space-md" onSubmit={(e) => { e.preventDefault(); }}>
              <div>
                <label className="block font-label-md text-label-md text-on-surface mb-1 font-semibold">Họ và tên</label>
                <input
                  value={newAdminName}
                  onChange={(e) => setNewAdminName(e.target.value)}
                  disabled
                  className="w-full h-11 px-3.5 rounded-xl bg-slate-100 text-slate-500 font-body-md text-body-md border border-slate-200 cursor-not-allowed"
                  placeholder="ví dụ: Nguyễn Đức Thắng"
                  type="text"
                />
              </div>

              <div>
                <label className="block font-label-md text-label-md text-on-surface mb-1 font-semibold">Email công vụ (@danasea.vn)</label>
                <input
                  value={newAdminEmail}
                  onChange={(e) => setNewAdminEmail(e.target.value)}
                  disabled
                  className="w-full h-11 px-3.5 rounded-xl bg-slate-100 text-slate-500 font-body-md text-body-md border border-slate-200 cursor-not-allowed"
                  placeholder="admin.cangvu@danasea.vn"
                  type="email"
                />
              </div>

              <div>
                <label className="block font-label-md text-label-md text-on-surface mb-1 font-semibold">Số điện thoại trực ban</label>
                <input
                  value={newAdminPhone}
                  onChange={(e) => setNewAdminPhone(e.target.value)}
                  disabled
                  className="w-full h-11 px-3.5 rounded-xl bg-slate-100 text-slate-500 font-body-md text-body-md border border-slate-200 cursor-not-allowed"
                  placeholder="09xx xxx xxx"
                  type="tel"
                />
              </div>

              <div className="flex items-center justify-end gap-space-sm pt-space-md">
                <button
                  onClick={() => setIsAddAdminOpen(false)}
                  className="px-space-lg py-2.5 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface font-label-md text-label-md font-semibold"
                  type="button"
                >
                  Đóng
                </button>
                <button
                  disabled
                  className="px-space-xl py-2.5 rounded-xl bg-slate-300 text-slate-500 font-label-md text-label-md font-bold cursor-not-allowed"
                  type="submit"
                >
                  Tạo tài khoản ADMIN
                </button>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}
    </main>
  );
}

export default Users;
