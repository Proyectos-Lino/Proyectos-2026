import { Navigate, Route, Routes } from 'react-router'
import { AppShell } from './components/layout/AppShell'
import { RequireAuth } from './auth/RequireAuth'
import { AuthProvider } from './auth/AuthContext'
import { DashboardPage } from './pages/DashboardPage'
import { EvaluationDetailPage } from './pages/EvaluationDetailPage'
import { EvaluationsPage } from './pages/EvaluationsPage'
import { LoginPage } from './pages/LoginPage'
import { NewEvaluationPage } from './pages/NewEvaluationPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { SetupPage } from './pages/SetupPage'
import { ParticipantsPage } from './features/participants/ParticipantsPage'
import { UsersPage } from './features/users/UsersPage'
import { OrganizationsPage } from './features/organizations/OrganizationsPage'
import { EvaluationSetupPage } from './features/evaluations/EvaluationSetupPage'
import { ImportsPage } from './features/imports/ImportsPage'
import { ExamsPage } from './features/exams/ExamsPage'
import { ExamDetailPage } from './features/reviews/ExamDetailPage'
import { ReviewCasesPage } from './features/reviews/ReviewCasesPage'
import { PublicationPage } from './pages/PublicationPage'
import { PublicResultPage } from './pages/PublicResultPage'
import { AnalyticsPage } from './features/analytics/AnalyticsPage'
import { Notifications } from './components/Notifications'

function App() {
  return (
    <Notifications><AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/consulta" element={<PublicResultPage />} />
        <Route path="/instalacion" element={<SetupPage />} />
        <Route element={<RequireAuth />}>
          <Route element={<AppShell />}>
            <Route index element={<DashboardPage />} />
            <Route path="/evaluaciones" element={<EvaluationsPage />} />
            <Route path="/evaluaciones/nueva" element={<NewEvaluationPage />} />
            <Route path="/evaluaciones/:evaluationId" element={<EvaluationDetailPage />} />
            <Route path="/evaluaciones/:evaluationId/configuracion" element={<EvaluationSetupPage />} />
            <Route path="/evaluaciones/:evaluationId/participantes" element={<ParticipantsPage />} />
            <Route path="/evaluaciones/:evaluationId/importaciones" element={<ImportsPage />} />
            <Route path="/evaluaciones/:evaluationId/examenes" element={<ExamsPage />} />
            <Route path="/evaluaciones/:evaluationId/revisiones" element={<ReviewCasesPage />} />
            <Route path="/evaluaciones/:evaluationId/publicacion" element={<PublicationPage />} />
            <Route path="/evaluaciones/:evaluationId/informes" element={<AnalyticsPage />} />
            <Route path="/evaluaciones/:evaluationId/examenes/:sheetId" element={<ExamDetailPage />} />
            <Route path="/participantes" element={<ParticipantsPage />} />
            <Route path="/usuarios" element={<UsersPage />} />
            <Route path="/organizaciones" element={<OrganizationsPage />} />
            <Route path="/inicio" element={<Navigate to="/" replace />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </AuthProvider></Notifications>
  )
}

export default App
