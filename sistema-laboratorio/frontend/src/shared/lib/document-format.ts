import type { DocumentFormat } from '../types/domain';

export function describeDocumentFormat(format?: DocumentFormat): string {
  return [
    format?.code ? `Formato ${format.code}` : '',
    format?.version ? `Versión del formato ${format.version}` : '',
    format?.approvedOn ? `Aprobado el ${format.approvedOn.split('-').reverse().join('/')}` : '',
  ]
    .filter(Boolean)
    .join(' · ');
}
