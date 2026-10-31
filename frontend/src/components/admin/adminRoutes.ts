import { Building2, FolderKanban, LayoutDashboard, Scale, ScrollText, Settings2, ShieldCheck, ShoppingBag, TicketPercent, Users, Wallet } from 'lucide-react';

export const adminNavigation = [
  { id: 'overview', label: 'Điều hành', icon: LayoutDashboard, items: [{ to: '/admin', label: 'Tổng quan', pageTitle: 'Dashboard', icon: LayoutDashboard }] },
  { id: 'partners', label: 'Đối tác & trải nghiệm', icon: Building2, items: [
    { to: '/admin/vendor-approval', label: 'Phê duyệt đối tác', pageTitle: 'Phê duyệt đối tác', icon: Building2 },
    { to: '/admin/service-approval', label: 'Kiểm duyệt dịch vụ', pageTitle: 'Kiểm duyệt dịch vụ', icon: ShieldCheck },
    { to: '/admin/categories', label: 'Danh mục trải nghiệm', pageTitle: 'Danh mục trải nghiệm', icon: FolderKanban },
  ] },
  { id: 'transactions', label: 'Giao dịch & khách hàng', icon: ShoppingBag, items: [
    { to: '/admin/orders', label: 'Đơn hàng & thanh toán', pageTitle: 'Đơn hàng & thanh toán', icon: ShoppingBag },
    { to: '/admin/users', label: 'Người dùng & vai trò', pageTitle: 'Người dùng & vai trò', icon: Users },
    { to: '/admin/promotions', label: 'Khuyến mãi', pageTitle: 'Khuyến mãi', icon: TicketPercent },
  ] },
  { id: 'finance', label: 'Tài chính & pháp lý', icon: Wallet, items: [
    { to: '/admin/payouts', label: 'Đối soát & quyết toán', pageTitle: 'Đối soát & quyết toán', icon: Wallet },
    { to: '/admin/disputes', label: 'Khiếu nại & tranh chấp', pageTitle: 'Khiếu nại & tranh chấp', icon: Scale },
  ] },
  { id: 'system', label: 'Hệ thống', icon: Settings2, items: [
    { to: '/admin/settings', label: 'Cấu hình & an toàn biển', pageTitle: 'Cấu hình hệ thống', icon: Settings2 },
    { to: '/admin/logs', label: 'Nhật ký hệ thống', pageTitle: 'Nhật ký hệ thống', icon: ScrollText },
  ] },
];
export const adminPages = adminNavigation.flatMap(group => group.items.map(item => ({ ...item, group: group.label })));
