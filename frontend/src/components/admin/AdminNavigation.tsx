import { NavLink, useLocation } from 'react-router-dom';
import { adminNavigation } from './adminRoutes';

interface AdminNavigationProps {
  isExpanded: boolean;
  onNavigate: () => void;
}

export function AdminNavigation({ isExpanded, onNavigate }: AdminNavigationProps) {
  const { search } = useLocation();
  const demo = import.meta.env.DEV && new URLSearchParams(search).get('demo') === '1';

  return (
    <nav className="p-3 flex flex-col gap-2.5 overflow-y-auto overflow-x-hidden flex-1" aria-label="Các trang quản trị">
      {adminNavigation.map((group, gIdx) => (
        <div key={group.id} className="flex flex-col gap-1">
          {/* Group Title / Divider */}
          {isExpanded ? (
            <div className="pt-2 pb-1 px-3">
              <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 select-none">
                {group.label}
              </span>
            </div>
          ) : (
            gIdx > 0 && <div className="my-1.5 mx-2 border-t border-slate-100" />
          )}

          {group.items.map((item) => (
            <NavLink
              key={item.to}
              to={`${item.to}${demo ? '?demo=1' : ''}`}
              end={item.to === '/admin'}
              title={!isExpanded ? item.label : undefined}
              aria-label={item.label}
              onClick={onNavigate}
              className={({ isActive }) =>
                `group flex items-center h-10 rounded-xl text-sm font-medium transition-all duration-150 relative ${
                  isExpanded
                    ? 'px-3 w-full'
                    : 'w-10 justify-center mx-auto'
                } ${
                  isActive
                    ? 'bg-[#0B5D6F] text-white shadow-xs font-semibold'
                    : 'text-slate-600 hover:text-[#0B5D6F] hover:bg-teal-50/70'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <item.icon
                    className={`w-5 h-5 shrink-0 transition-colors ${
                      isActive ? 'text-white' : 'text-slate-500 group-hover:text-[#0B5D6F]'
                    }`}
                    strokeWidth={1.75}
                  />
                  <span
                    className={`truncate whitespace-nowrap transition-all duration-200 ${
                      isExpanded
                        ? 'opacity-100 max-w-[180px] ml-3'
                        : 'opacity-0 max-w-0 ml-0 overflow-hidden'
                    }`}
                  >
                    {item.label}
                  </span>
                </>
              )}
            </NavLink>
          ))}
        </div>
      ))}
    </nav>
  );
}
