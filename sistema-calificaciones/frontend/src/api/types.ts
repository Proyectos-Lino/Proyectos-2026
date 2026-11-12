export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface Organization {
  id: number
  code: string
  name: string
  role: 'ADMINISTRADOR' | 'OPERADOR' | 'REVISOR' | 'LECTOR'
}

export interface Session {
  id: number
  email: string
  name: string
  organizations: Organization[]
}

export interface Evaluation {
  id: number
  organizationId: number
  code: string
  name: string
  description: string | null
  type: string
  status: 'BORRADOR' | 'CONFIGURADA' | 'EN_PROCESO' | 'CERRADA' | 'PUBLICADA' | 'ARCHIVADA'
  date: string | null
  groupType: 'GRUPO' | 'CARRERA' | 'SEDE' | 'CATEGORIA' | 'PROGRAMA'
}

export interface EvaluationOverview extends Evaluation {
  participants: number
  exams: number
  observed: number
  admitted: number
  averageScore: number | null
}

export interface Statistics {
  evaluations: number
  exams: number
  observed: number
  published: number
}

export interface Group { id: number; evaluationId: number; code: string; name: string; capacity: number | null }
export interface Form { id: number; evaluationId: number; code: string; questions: number; options: number }
export interface Rule {
  id: number; evaluationId: number; version: number; correct: number; incorrect: number; blank: number
  passingScore: number | null; officialFormula: string | null
}
export interface RankingPolicy {
  id: number; evaluationId: number; version: number
  scope: 'GLOBAL' | 'GRUPO'
  approvalMode: 'NINGUNA' | 'MINIMA' | 'VACANTES' | 'AMBAS' | 'VACANTES_O_MINIMA'
  tieBreak: 'COMPARTIR_PUESTO' | 'CORRECTAS' | 'MENOS_INCORRECTAS' | 'CODIGO'
  globalCapacity: number | null
}
export interface SectionInput {
  code: string; name: string; fromQuestion: number; toQuestion: number
  correct: number; incorrect: number; blank: number
}
export interface Section extends SectionInput { id: number; order: number }
export interface SectionConfiguration { id: number; formId: number; version: number; sections: Section[] }
export interface AnswerKey { id: number; formId: number; version: number; answers: string }
export interface Exam {
  id: number
  evaluationId: number
  sheetCode: string | null
  participantCode: string
  participantName: string
  groupName: string | null
  formCode: string
  status: 'RECIBIDA' | 'OBSERVADA' | 'VALIDADA' | 'ANULADA'
  observation: string | null
  correct: number | null
  incorrect: number | null
  blank: number | null
  score: number | null
  officialScore: number | null
  officialFormula: string | null
  position: number | null
  passed: boolean | null
  createdAt: string
}
export interface SectionScore {
  code: string; name: string; fromQuestion: number; toQuestion: number
  correct: number; incorrect: number; blank: number
  correctPoints: number; incorrectPoints: number; blankPoints: number; points: number
}
export interface ExamExplanation {
  sheetId: number; formCode: string; questions: number; keyVersion: number; ruleVersion: number
  correctPoints: number; incorrectPoints: number; blankPoints: number; minimumScore: number | null
  sectionVersion: number | null; score: number; sections: SectionScore[]
  officialScore: number | null; officialFormula: string | null
}
export interface RankingDecision {
  id: number; policyVersion: number; scope: RankingPolicy['scope']
  approvalMode: RankingPolicy['approvalMode']; tieBreak: RankingPolicy['tieBreak']
  capacity: number | null; minimumScore: number | null
  position: number | null; passed: boolean | null; recordedAt: string
}

export interface Batch { id: number; filename: string; status: string; total: number; valid: number; errors: number }
export interface ImportSummary { batchId: number; total: number; valid: number; errors: number }
export interface DbfColumnMapping {
  identificationSheet: string; identificationForm: string; identificationParticipant: string
  responseSheet: string; responseForm: string; responseAnswersFrom: string
  keyForm: string; keyAnswersFrom: string
}
export interface DbfFileColumns { columns: string[]; rows: number; sample: string[][] }
export interface DbfInspection {
  identification: DbfFileColumns; responses: DbfFileColumns; keys: DbfFileColumns
  suggestedMapping: DbfColumnMapping
}
export interface DbfPreviewRow {
  row: number; sheetCode: string; participantCode: string; formCode: string
  decision: 'ACEPTAR' | 'OBSERVADA' | 'CORREGIR'; issues: string[]
}
export interface DbfPreview {
  fingerprint: string; total: number; accepted: number; observed: number
  correctionNeeded: number; generalIssues: string[]; rows: Page<DbfPreviewRow>
}
export interface BulkImportSummary { created: number; updated: number; skipped: number; reportId: number | null }
export interface PreparationCheck { code: string; label: string; completed: boolean; pending: number }
export interface Preparation { status: Evaluation['status']; approved: boolean; editable: boolean; setup: PreparationCheck[]; publication: PreparationCheck[]; readyToApprove: boolean; readyToPublish: boolean }
export interface Review { id: number; sheetId: number; status: string; reason: string; resolution: string | null; openedAt: string; assigneeId: number | null; assigneeName: string | null; openedByName: string | null; resolvedByName: string | null; resolvedAt: string | null }
export interface ReviewCase { id: number; sheetId: number; participantCode: string; participantName: string; sheetCode: string | null; status: string; reason: string; assigneeId: number | null; assigneeName: string | null; openedAt: string }
export interface Assignee { id: number; name: string; role: string }
export interface ResultVersion {
  id: number; version: number; keyVersion: number; ruleVersion: number; sectionVersion: number | null
  correct: number; incorrect: number; blank: number; score: number; reason: string; calculatedAt: string
  officialScore: number | null; officialFormula: string | null
}
export interface ResultVersionDetail { version: ResultVersion; responses: string[]; key: string[]; correctPoints: number; incorrectPoints: number; blankPoints: number; sections: SectionScore[] }
export interface AuditEvent { id: number; userId: number | null; userName: string | null; action: string; entity: string; entityId: number | null; details: string; occurredAt: string; sheetId: number | null }
export interface PublicationPolicy { showScore: boolean; showPosition: boolean; showPassed: boolean; showCounts: boolean; visibleFrom: string | null; visibleUntil: string | null; approvedBy: number | null; approvedAt: string | null; publishedBy: number | null; publishedAt: string | null; configured: boolean }
export interface PublicResult { evaluation: string; participantCode: string; participantName: string; formCode: string; score: number | null; position: number | null; passed: boolean | null; correct: number | null; incorrect: number | null; blank: number | null; evaluationType: string }
export interface Evidence { id: number; reviewId: number; filename: string; contentType: string; size: number; uploadedByName: string | null; uploadedAt: string }
export interface User { id: number; email: string; name: string; active: boolean; role: Organization['role']; membershipActive: boolean; sharedAccount: boolean }
