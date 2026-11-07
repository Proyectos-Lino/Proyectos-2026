export interface Person {
  id: number; names: string; surnames: string | null; documentType: string
  document: string | null; email: string | null; automatic: boolean; registrations: number
}
export interface Enrollment {
  id: number; personId: number; code: string; names: string; surnames: string | null
  documentType: string; document: string | null; email: string | null; automatic: boolean
  groupId: number | null; groupName: string | null; hasExam: boolean
}
export interface EnrollmentInput {
  code: string; personId: number | null; names: string; surnames: string; documentType: string
  document: string; email: string; groupId: number | null
}
export interface ParticipantSummary { total: number; incomplete: number; withoutGroup: number; withoutEmail: number }
export type ImportMode = 'REGISTROS' | 'GRUPOS'
export interface EnrollmentImportRow {
  row: number; code: string; action: string; detail: string; previousGroup: string | null; group: string | null
}
export interface EnrollmentImportCounts {
  total: number; newEnrollments: number; newPeople: number; completed: number
  reused: number; preserved: number; groupChanges: number; errors: number
}
export interface EnrollmentImportResult { reportId: number; counts: EnrollmentImportCounts }
