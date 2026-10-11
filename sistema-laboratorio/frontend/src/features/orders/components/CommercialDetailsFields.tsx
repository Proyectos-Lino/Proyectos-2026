import type { CommercialDetailsFormValues } from '../model/order-commercial-details';
import { custodyLabels, postAnalysisCustodyOptions } from '../model/order-commercial-details';

interface CommercialDetailsFieldsProps {
  values: CommercialDetailsFormValues;
  onChange: (field: keyof CommercialDetailsFormValues, value: string | boolean) => void;
}

export function CommercialDetailsFields({ values, onChange }: CommercialDetailsFieldsProps) {
  return (
    <>
      <div className="space-y-2">
        <label htmlFor="order-report-name">Nombre para informe</label>
        <input
          id="order-report-name"
          onChange={(event) => onChange('reportName', event.target.value)}
          value={values.reportName}
        />
      </div>

      <div className="space-y-2">
        <label htmlFor="order-report-email">Correo de informe</label>
        <input
          id="order-report-email"
          onChange={(event) => onChange('reportEmail', event.target.value)}
          type="email"
          value={values.reportEmail}
        />
      </div>

      <div className="space-y-2 md:col-span-2">
        <label htmlFor="order-report-address">Dirección para informe</label>
        <input
          id="order-report-address"
          onChange={(event) => onChange('reportAddress', event.target.value)}
          value={values.reportAddress}
        />
      </div>

      <div className="space-y-2">
        <label htmlFor="order-delivery">Entrega de informe</label>
        <select
          id="order-delivery"
          onChange={(event) => onChange('deliveryMethod', event.target.value)}
          value={values.deliveryMethod}
        >
          <option value="EMAIL">Correo electrónico</option>
          <option value="PICKUP">Recojo en laboratorio</option>
        </select>
      </div>

      {values.deliveryMethod === 'PICKUP' ? (
        <div className="space-y-2">
          <label htmlFor="order-pickup-person">Persona que recoge</label>
          <input
            id="order-pickup-person"
            onChange={(event) => onChange('pickupPerson', event.target.value)}
            required
            value={values.pickupPerson}
          />
        </div>
      ) : (
        <div className="rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-600">
          El informe se entregará al correo registrado.
        </div>
      )}

      <div className="space-y-2">
        <label htmlFor="order-due-date">Entrega programada</label>
        <input
          id="order-due-date"
          onChange={(event) => onChange('plannedDeliveryAt', event.target.value)}
          required
          type="datetime-local"
          value={values.plannedDeliveryAt}
        />
      </div>

      <div className="space-y-2">
        <label htmlFor="order-development-days">Días de desarrollo</label>
        <input
          id="order-development-days"
          min="0"
          onChange={(event) => onChange('developmentDays', event.target.value)}
          type="number"
          value={values.developmentDays}
        />
      </div>

      <div className="space-y-2">
        <label htmlFor="order-proforma">Número de proforma</label>
        <input
          id="order-proforma"
          onChange={(event) => onChange('proformaNumber', event.target.value)}
          value={values.proformaNumber}
        />
      </div>

      <div className="space-y-2">
        <label htmlFor="order-technical-proposal">Propuesta técnica</label>
        <input
          id="order-technical-proposal"
          onChange={(event) => onChange('technicalProposalNumber', event.target.value)}
          value={values.technicalProposalNumber}
        />
      </div>

      <div className="space-y-2">
        <label htmlFor="order-post-analysis-custody">Custodia posensayo</label>
        <select
          id="order-post-analysis-custody"
          onChange={(event) => onChange('postAnalysisCustody', event.target.value)}
          value={values.postAnalysisCustody}
        >
          {postAnalysisCustodyOptions.map((option) => (
            <option key={option} value={option}>
              {custodyLabels[option]}
            </option>
          ))}
        </select>
      </div>

      <label className="flex cursor-pointer items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 text-sm font-semibold text-slate-700">
        <input
          checked={values.outsourced}
          onChange={(event) => onChange('outsourced', event.target.checked)}
          type="checkbox"
        />
        Servicio subcontratado
      </label>

      <div className="space-y-2 md:col-span-2">
        <label htmlFor="order-observations">Observaciones generales</label>
        <textarea
          id="order-observations"
          onChange={(event) => onChange('generalObservations', event.target.value)}
          rows={3}
          value={values.generalObservations}
        />
      </div>
    </>
  );
}
