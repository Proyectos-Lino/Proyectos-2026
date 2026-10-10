import { Beaker, LogOut, Menu, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../../features/auth/hooks/useAuth';
import { useOrganization } from '../../../features/organization/hooks/useOrganization';
import { roleLabels } from '../../lib/labels';
import { SidebarNavigation } from './SidebarNavigation';

export function AppShell() {
  const { logout, user } = useAuth();
  const { organization } = useOrganization();
  const navigate = useNavigate();
  const [isNavigationOpen, setIsNavigationOpen] = useState(false);

  useEffect(() => {
    if (!isNavigationOpen) return;

    const desktopViewport = window.matchMedia('(min-width: 1024px)');
    if (desktopViewport.matches) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsNavigationOpen(false);
    };
    const closeOnDesktop = (event: MediaQueryListEvent) => {
      if (event.matches) setIsNavigationOpen(false);
    };
    window.addEventListener('keydown', closeOnEscape);
    desktopViewport.addEventListener('change', closeOnDesktop);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', closeOnEscape);
      desktopViewport.removeEventListener('change', closeOnDesktop);
    };
  }, [isNavigationOpen]);

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="min-h-screen overflow-x-hidden bg-canvas">
      <header
        className="sticky top-0 z-30 flex h-16 items-center justify-between gap-3 border-b border-white/10 px-4 text-white shadow-sm lg:hidden"
        style={{ backgroundColor: organization?.secondaryColor }}
      >
        <div className="flex min-w-0 items-center gap-3">
          <span
            className="grid size-9 shrink-0 place-items-center overflow-hidden rounded-xl shadow"
            style={{ backgroundColor: organization?.primaryColor }}
          >
            {organization?.logoDataUrl ? (
              <img alt="" className="size-full object-contain p-1" src={organization.logoDataUrl} />
            ) : (
              <Beaker size={18} />
            )}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-bold">{organization?.commercialName}</p>
            <p className="truncate text-[0.6875rem] text-slate-400">Gestión de laboratorio</p>
          </div>
        </div>
        <button
          aria-controls="application-navigation"
          aria-expanded={isNavigationOpen}
          aria-label="Abrir menú de navegación"
          className="grid size-10 shrink-0 place-items-center rounded-xl border border-white/10 bg-white/5 text-slate-100 transition hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lab-100"
          onClick={() => setIsNavigationOpen(true)}
          type="button"
        >
          <Menu aria-hidden="true" size={21} />
        </button>
      </header>

      {isNavigationOpen ? (
        <button
          aria-label="Cerrar menú de navegación"
          className="fixed inset-0 z-40 bg-slate-950/55 backdrop-blur-[2px] lg:hidden"
          onClick={() => setIsNavigationOpen(false)}
          type="button"
        />
      ) : null}

      <aside
        aria-label="Menú principal"
        className={`fixed inset-y-0 left-0 z-50 flex h-dvh w-[min(20rem,calc(100vw-3rem))] flex-col px-4 py-5 text-slate-200 shadow-2xl transition-transform duration-300 ease-out lg:visible lg:w-70 lg:translate-x-0 lg:shadow-none ${
          isNavigationOpen ? 'visible translate-x-0' : 'invisible -translate-x-full'
        }`}
        id="application-navigation"
        style={{ backgroundColor: organization?.secondaryColor }}
      >
        <div className="mb-6 flex shrink-0 items-center justify-between gap-3 px-2 text-white">
          <div className="flex min-w-0 items-center gap-3">
            <span
              className="grid size-10 shrink-0 place-items-center overflow-hidden rounded-2xl shadow-lg"
              style={{ backgroundColor: organization?.primaryColor }}
            >
              {organization?.logoDataUrl ? (
                <img
                  alt=""
                  className="size-full object-contain p-1"
                  src={organization.logoDataUrl}
                />
              ) : (
                <Beaker size={20} />
              )}
            </span>
            <div className="min-w-0">
              <p className="max-w-44 truncate text-base font-bold tracking-tight">
                {organization?.commercialName}
              </p>
              <p className="text-xs text-slate-400">Gestión de laboratorio</p>
            </div>
          </div>
          <button
            aria-label="Cerrar menú de navegación"
            className="grid size-9 shrink-0 place-items-center rounded-xl text-slate-300 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lab-100 lg:hidden"
            onClick={() => setIsNavigationOpen(false)}
            type="button"
          >
            <X aria-hidden="true" size={20} />
          </button>
        </div>

        <SidebarNavigation onNavigate={() => setIsNavigationOpen(false)} />

        <div className="mt-3 shrink-0 rounded-2xl border border-white/10 bg-white/5 p-3">
          <p className="truncate text-sm font-semibold text-white">{user?.fullName}</p>
          <p className="mt-1 truncate text-xs text-slate-400">
            {user?.roles.map((role) => roleLabels[role]).join(' · ')}
          </p>
          <button
            className="mt-4 flex w-full items-center gap-3 rounded-xl px-2 py-2 text-sm font-medium text-slate-300 transition hover:bg-white/8 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lab-100"
            onClick={handleLogout}
            type="button"
          >
            <LogOut size={17} />
            Cerrar sesión
          </button>
        </div>
      </aside>

      <main className="min-w-0 p-5 sm:p-7 lg:ml-70 lg:p-9">
        <Outlet />
      </main>
    </div>
  );
}
