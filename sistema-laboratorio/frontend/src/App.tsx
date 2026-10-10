import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './features/auth/providers/AuthProvider';
import { AreasPage } from './features/areas/pages/AreasPage';
import { useAuth } from './features/auth/hooks/useAuth';
import { LoginPage } from './features/auth/pages/LoginPage';
import { ClientsPage } from './features/clients/pages/ClientsPage';
import { CatalogsPage } from './features/catalogs/pages/CatalogsPage';
import { DashboardPage } from './features/dashboard/pages/DashboardPage';
import { OrdersPage } from './features/orders/pages/OrdersPage';
import { ResultEntryPage } from './features/results/pages/ResultEntryPage';
import { BudResultsPage } from './features/results/pages/BudResultsPage';
import { ResultValidationPage } from './features/results/pages/ResultValidationPage';
import { UsersPage } from './features/users/pages/UsersPage';
import { WorkOrdersPage } from './features/work-orders/pages/WorkOrdersPage';
import { ReportsPage } from './features/reports/pages/ReportsPage';
import { AuditLogPage } from './features/audit/pages/AuditLogPage';
import { OrganizationPage } from './features/organization/pages/OrganizationPage';
import { OrganizationProvider } from './features/organization/providers/OrganizationProvider';
import { AppShell } from './shared/components/layout/AppShell';
import type { RoleCode } from './shared/types/domain';

function ProtectedRoutes() {
  const { user } = useAuth();
  return user ? <AppShell /> : <Navigate to="/login" replace />;
}

function AuthorizedRoute({ roles }: { roles: RoleCode[] }) {
  const { hasRole } = useAuth();
  return hasRole(...roles) ? <Outlet /> : <Navigate replace to="/" />;
}

function App() {
  return (
    <OrganizationProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<LoginPage />} path="/login" />
            <Route element={<ProtectedRoutes />}>
              <Route element={<DashboardPage />} index />
              <Route element={<AuthorizedRoute roles={['ADMIN', 'COMMERCIAL']} />}>
                <Route element={<ClientsPage />} path="clients" />
              </Route>
              <Route element={<AuthorizedRoute roles={['ADMIN', 'COMMERCIAL']} />}>
                <Route element={<OrdersPage />} path="orders" />
              </Route>
              <Route
                element={
                  <AuthorizedRoute roles={['ADMIN', 'TECHNICAL_MANAGER', 'ANALYST', 'VALIDATOR']} />
                }
              >
                <Route element={<WorkOrdersPage />} path="work-orders" />
              </Route>
              <Route element={<AuthorizedRoute roles={['ADMIN', 'ANALYST']} />}>
                <Route element={<ResultEntryPage />} path="result-entry" />
                <Route element={<BudResultsPage />} path="bud-results" />
              </Route>
              <Route element={<AuthorizedRoute roles={['ADMIN', 'VALIDATOR']} />}>
                <Route element={<ResultValidationPage />} path="result-validation" />
              </Route>
              <Route
                element={
                  <AuthorizedRoute
                    roles={['ADMIN', 'COMMERCIAL', 'TECHNICAL_MANAGER', 'VALIDATOR']}
                  />
                }
              >
                <Route element={<ReportsPage />} path="reports" />
              </Route>
              <Route element={<AuthorizedRoute roles={['ADMIN']} />}>
                <Route element={<UsersPage />} path="users" />
                <Route element={<AreasPage />} path="areas" />
                <Route element={<CatalogsPage />} path="catalogs" />
                <Route element={<AuditLogPage />} path="audit-log" />
                <Route element={<OrganizationPage />} path="organization" />
              </Route>
            </Route>
            <Route element={<Navigate replace to="/" />} path="*" />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </OrganizationProvider>
  );
}

export default App;
