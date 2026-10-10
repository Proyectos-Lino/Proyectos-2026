import {
  Boxes,
  ChevronDown,
  ClipboardList,
  ClipboardPenLine,
  FileCheck2,
  FlaskConical,
  History,
  LayoutDashboard,
  Settings,
  Building2,
  ShieldCheck,
  Sprout,
  UsersRound,
} from 'lucide-react';
import { useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../../../features/auth/hooks/useAuth';
import type { RoleCode } from '../../types/domain';

interface NavigationItem {
  to: string;
  label: string;
  icon: typeof LayoutDashboard;
  roles?: RoleCode[];
  end?: boolean;
}

interface NavigationGroup {
  id: string;
  label: string;
  icon: typeof LayoutDashboard;
  items: NavigationItem[];
}

const homeItem: NavigationItem = {
  to: '/',
  label: 'Inicio',
  icon: LayoutDashboard,
  end: true,
};

const navigationGroups: NavigationGroup[] = [
  {
    id: 'commercial',
    label: 'Gestión comercial',
    icon: ClipboardList,
    items: [
      {
        to: '/clients',
        label: 'Clientes y productores',
        icon: UsersRound,
        roles: ['ADMIN', 'COMMERCIAL'],
      },
      {
        to: '/orders',
        label: 'Órdenes de servicio',
        icon: ClipboardList,
        roles: ['ADMIN', 'COMMERCIAL'],
      },
    ],
  },
  {
    id: 'technical',
    label: 'Operación técnica',
    icon: FlaskConical,
    items: [
      {
        to: '/work-orders',
        label: 'Órdenes de trabajo',
        icon: FlaskConical,
        roles: ['ADMIN', 'TECHNICAL_MANAGER', 'ANALYST', 'VALIDATOR'],
      },
      {
        to: '/result-entry',
        label: 'Registro de resultados',
        icon: ClipboardPenLine,
        roles: ['ADMIN', 'ANALYST'],
      },
      {
        to: '/result-validation',
        label: 'Validación de resultados',
        icon: ShieldCheck,
        roles: ['ADMIN', 'VALIDATOR'],
      },
      {
        to: '/bud-results',
        label: 'Yemas y cargadores',
        icon: Sprout,
        roles: ['ADMIN', 'ANALYST'],
      },
    ],
  },
  {
    id: 'documents',
    label: 'Documentación y control',
    icon: FileCheck2,
    items: [
      {
        to: '/reports',
        label: 'Informes de resultados',
        icon: FileCheck2,
        roles: ['ADMIN', 'COMMERCIAL', 'TECHNICAL_MANAGER', 'VALIDATOR'],
      },
      {
        to: '/audit-log',
        label: 'Bitácora de auditoría',
        icon: History,
        roles: ['ADMIN'],
      },
    ],
  },
  {
    id: 'administration',
    label: 'Administración',
    icon: Settings,
    items: [
      {
        to: '/organization',
        label: 'Organización',
        icon: Building2,
        roles: ['ADMIN'],
      },
      {
        to: '/users',
        label: 'Usuarios y roles',
        icon: Settings,
        roles: ['ADMIN'],
      },
      {
        to: '/areas',
        label: 'Áreas de laboratorio',
        icon: FlaskConical,
        roles: ['ADMIN'],
      },
      {
        to: '/catalogs',
        label: 'Catálogos',
        icon: Boxes,
        roles: ['ADMIN'],
      },
    ],
  },
];

function matchesPath(item: NavigationItem, pathname: string) {
  return item.end
    ? pathname === item.to
    : pathname === item.to || pathname.startsWith(`${item.to}/`);
}

export function SidebarNavigation({ onNavigate }: { onNavigate?: () => void }) {
  const { hasRole } = useAuth();
  const { pathname } = useLocation();
  const visibleGroups = navigationGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.roles || hasRole(...item.roles)),
    }))
    .filter((group) => group.items.length > 0);
  const routeGroupId =
    visibleGroups.find((group) => group.items.some((item) => matchesPath(item, pathname)))?.id ??
    null;
  const [selection, setSelection] = useState({
    groupId: routeGroupId,
    pathname,
  });
  const openGroupId = selection.pathname === pathname ? selection.groupId : routeGroupId;

  return (
    <nav
      aria-label="Navegación principal"
      className="min-h-0 flex-1 space-y-1.5 overflow-y-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
    >
      <NavLink
        className={({ isActive }) => navigationLinkClass(isActive)}
        end={homeItem.end}
        onClick={() => {
          setSelection({ groupId: null, pathname });
          onNavigate?.();
        }}
        to={homeItem.to}
      >
        <LayoutDashboard aria-hidden="true" size={18} />
        {homeItem.label}
      </NavLink>

      {visibleGroups.map((group) => {
        const isOpen = openGroupId === group.id;
        const containsActiveRoute = group.items.some((item) => matchesPath(item, pathname));
        const GroupIcon = group.icon;

        return (
          <section key={group.id}>
            <button
              aria-controls={`navigation-group-${group.id}`}
              aria-expanded={isOpen}
              className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lab-100 ${
                containsActiveRoute
                  ? 'bg-white/12 text-white shadow-sm'
                  : 'text-slate-300 hover:bg-white/7 hover:text-white'
              }`}
              onClick={() =>
                setSelection({
                  groupId: isOpen ? null : group.id,
                  pathname,
                })
              }
              type="button"
            >
              <GroupIcon aria-hidden="true" size={18} />
              <span className="min-w-0 flex-1 truncate">{group.label}</span>
              <ChevronDown
                aria-hidden="true"
                className={`shrink-0 transition-transform duration-200 ${
                  isOpen ? 'rotate-180' : ''
                }`}
                size={16}
              />
            </button>

            <div
              aria-hidden={!isOpen}
              className={`grid transition-[grid-template-rows,opacity] duration-200 ease-out ${
                isOpen ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
              }`}
              id={`navigation-group-${group.id}`}
            >
              <div className="overflow-hidden">
                <div className="ml-5 space-y-1 border-l border-white/10 py-1 pl-3">
                  {group.items.map(({ end, icon: Icon, label, to }) => (
                    <NavLink
                      className={({ isActive }) => navigationChildLinkClass(isActive)}
                      end={end}
                      key={to}
                      onClick={onNavigate}
                      tabIndex={isOpen ? undefined : -1}
                      to={to}
                    >
                      <Icon aria-hidden="true" size={16} />
                      <span className="min-w-0 truncate">{label}</span>
                    </NavLink>
                  ))}
                </div>
              </div>
            </div>
          </section>
        );
      })}
    </nav>
  );
}

function navigationLinkClass(isActive: boolean) {
  return `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lab-100 ${
    isActive
      ? 'bg-white/12 text-white shadow-sm'
      : 'text-slate-300 hover:bg-white/7 hover:text-white'
  }`;
}

function navigationChildLinkClass(isActive: boolean) {
  return `flex items-center gap-2.5 rounded-lg px-3 py-2 text-[0.8125rem] font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lab-100 ${
    isActive ? 'bg-lab-500/18 text-white' : 'text-slate-400 hover:bg-white/7 hover:text-slate-100'
  }`;
}
