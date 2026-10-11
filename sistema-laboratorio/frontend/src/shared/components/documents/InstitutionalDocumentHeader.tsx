import type { OrganizationIdentity } from '../../types/domain';
import { formatDate } from '../../lib/format';

interface InstitutionalDocumentHeaderProps {
  code: string;
  generatedAt: string;
  organization?: OrganizationIdentity;
  section: string;
  title: string;
  version: number;
}

export function InstitutionalDocumentHeader({
  code,
  generatedAt,
  organization,
  section,
  title,
  version,
}: InstitutionalDocumentHeaderProps) {
  const contacts = organization?.contacts.map((contact) => contact.value).join(' · ');

  return (
    <header>
      <div className="grid overflow-hidden border border-slate-700 sm:grid-cols-[12rem_1fr]">
        <div className="flex min-h-24 items-center justify-center gap-3 border-b border-slate-700 p-4 sm:border-r sm:border-b-0">
          {organization?.logoDataUrl ? (
            <img
              alt={`Logo de ${organization.commercialName}`}
              className="max-h-14 max-w-28 object-contain"
              src={organization.logoDataUrl}
            />
          ) : null}
          <span className="text-center text-sm font-extrabold uppercase tracking-wide text-slate-900">
            {organization?.commercialName ?? 'Laboratorio'}
          </span>
        </div>
        <div className="flex flex-col justify-center p-4 text-center">
          <p className="text-base font-extrabold uppercase text-slate-950">
            {organization?.legalName ?? organization?.commercialName ?? 'Gestión de laboratorio'}
          </p>
          <p className="mt-1 text-xs leading-5 text-slate-600">
            {[organization?.documentLabel, organization?.address, contacts]
              .filter(Boolean)
              .join(' · ') || 'Documento institucional de laboratorio'}
          </p>
        </div>
      </div>
      <div className="border-x border-b border-slate-700 bg-slate-200 px-4 py-2 text-center text-sm font-extrabold uppercase text-slate-950">
        {section}
      </div>
      <div className="border-x border-b border-slate-700 px-4 py-3 text-center">
        <h1 className="text-xl font-extrabold uppercase tracking-tight text-slate-950">
          {title} · {code}
        </h1>
        <p className="mt-1 text-xs text-slate-500">
          Versión {version} · Emitido el {formatDate(generatedAt)}
        </p>
      </div>
    </header>
  );
}

