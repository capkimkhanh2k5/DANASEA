import { useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { adminNavigation } from './adminRoutes';
import { AdminAccordionSection } from './AdminWorkspace';

export function AdminNavigation({ collapsed, onNavigate }: { collapsed: boolean; onNavigate: () => void }) {
  const { pathname, search } = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(search).get('demo') === '1';
  const activeGroup = adminNavigation.find(group => group.items.some(item => item.to === pathname || (item.to !== '/admin' && pathname.startsWith(`${item.to}/`))));
  const [state, setState] = useState(() => ({ pathname, groups: Object.fromEntries(adminNavigation.map(group => [group.id, true])) }));
  if (state.pathname !== pathname) {
    setState({ pathname, groups: { ...state.groups, ...(activeGroup ? { [activeGroup.id]: true } : {}) } });
  }
  return <nav className={`admin-nav ${collapsed ? 'admin-nav--icons' : ''}`} aria-label="Các trang quản trị">
    {adminNavigation.map(group => {
      const active = group.items.some(item => item.to === pathname || (item.to !== '/admin' && pathname.startsWith(`${item.to}/`)));
      const links = <div className="admin-nav-children">
        {group.items.map(item => <NavLink key={item.to} to={`${item.to}${demo ? '?demo=1' : ''}`} end={item.to === '/admin'} title={item.label} aria-label={item.label} onClick={onNavigate}
          className={({ isActive }) => `admin-nav-link ${isActive ? 'is-active' : ''}`}>
          <span className="admin-nav-icon"><item.icon size={18} strokeWidth={1.7} /></span>
          <span className="admin-nav-text">{item.label}</span>
        </NavLink>)}
      </div>;
      return <AdminAccordionSection key={group.id} id={`admin-group-${group.id}`} label={group.label}
        title={<><span className="admin-nav-icon"><group.icon size={18} strokeWidth={1.7} /></span><span className="admin-nav-text">{group.label}</span></>}
        open={collapsed || state.groups[group.id]} headerHidden={collapsed} className={`admin-nav-group ${active ? 'has-active-route' : ''}`}
        onToggle={() => setState(current => ({ ...current, groups: { ...current.groups, [group.id]: !current.groups[group.id] } }))}>
        {links}
      </AdminAccordionSection>;
    })}
  </nav>;
}
