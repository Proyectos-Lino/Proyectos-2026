import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../shared/ui/Button'
import { FieldLabel } from '../../shared/ui/FieldLabel'
import { errorMessage } from '../../shared/api/http'
import { notify } from '../../shared/notifications/notifications'
import { integrations } from './service'
import { formFrom, type IntegrationForm, type IntegrationSettings } from './model'
import { IntegrationGuide } from './IntegrationGuide'

export function IntegrationsPage() {
  const [saved, setSaved] = useState<IntegrationSettings>()
  const [form, setForm] = useState<IntegrationForm>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [checking, setChecking] = useState('')
  const [result, setResult] = useState<{ success: boolean; message: string }>()
  async function load(signal?: AbortSignal) {
    try {
      const data = await integrations.get(signal)
      setSaved(data)
      setForm(formFrom(data))
      setError('')
    } catch (e) {
      if (!signal?.aborted) setError(errorMessage(e))
    }
  }
  useEffect(() => {
    const controller = new AbortController()
    integrations
      .get(controller.signal)
      .then((data) => {
        setSaved(data)
        setForm(formFrom(data))
        setError('')
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setError(errorMessage(e))
      })
    return () => controller.abort()
  }, [])
  if (!form || !saved)
    return (
      <div className="space-y-4">
        {error ? (
          <p role="alert" className="error-box">
            {error}
          </p>
        ) : (
          <p>Cargando integraciones…</p>
        )}
        <Button variant="secondary" onClick={() => void load()}>
          Volver a cargar
        </Button>
      </div>
    )
  const dirty = JSON.stringify(form) !== JSON.stringify(formFrom(saved))
  const locked = !saved.encryptionReady || saved.credentialsLocked
  function update<K extends keyof IntegrationForm>(name: K, value: IntegrationForm[K]) {
    setForm((old) => (old ? { ...old, [name]: value } : old))
    setResult(undefined)
  }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!form || !saved) return
    setBusy(true)
    setError('')
    setResult(undefined)
    try {
      const data = await integrations.save(saved.version, form)
      setSaved(data)
      setForm(formFrom(data))
      notify('Integraciones actualizadas. Los cambios ya están aplicados.', 'success')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }
  async function check(provider: 'kapso' | 'groq') {
    if (!saved) return
    setChecking(provider)
    setResult(undefined)
    try {
      setResult(await integrations.check(provider, saved.version))
    } catch (e) {
      setResult({ success: false, message: errorMessage(e) })
    } finally {
      setChecking('')
    }
  }
  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value)
      notify('Copiado al portapapeles.', 'success')
    } catch {
      setError('No se pudo copiar. Selecciona el texto y cópialo manualmente.')
    }
  }
  const secretField = (
    name: 'kapsoKey' | 'webhookSecret' | 'groqKey',
    label: string,
    has: boolean,
    clear: 'clearKapsoKey' | 'clearWebhookSecret' | 'clearGroqKey',
  ) => (
    <div className="space-y-2">
      <label className="block text-sm">
        <FieldLabel
          label={label}
          required={!has && (name === 'groqKey' ? form.aiEnabled : form.whatsappEnabled)}
        />
        <input
          type="password"
          autoComplete="new-password"
          maxLength={512}
          required={!has && (name === 'groqKey' ? form.aiEnabled : form.whatsappEnabled)}
          value={form[name]}
          onChange={(e) => update(name, e.target.value)}
          disabled={form[clear]}
          placeholder={has ? 'Guardada. Deja vacío para conservarla.' : 'Introduce la credencial'}
          className="field mt-2 w-full"
        />
      </label>
      {has && (
        <label className="flex min-h-11 items-center gap-2 text-sm text-muted">
          <input
            type="checkbox"
            checked={form[clear]}
            onChange={(e) => {
              update(clear, e.target.checked)
              if (e.target.checked) update(name, '')
            }}
          />
          Eliminar esta credencial guardada
        </label>
      )}
    </div>
  )
  return (
    <div className="space-y-6">
      <header>
        <h2 className="text-xl font-semibold">WhatsApp y asistente</h2>
        <p className="mt-2 text-sm text-muted">
          Conecta tus propias cuentas. La agenda y los demás módulos funcionan aunque estas
          integraciones estén desactivadas.
        </p>
      </header>
      <IntegrationGuide />
      {locked && (
        <p role="alert" className="error-box">
          {saved.credentialsLocked
            ? 'No se pueden recuperar las credenciales guardadas. Restaura la clave de cifrado original de esta instalación.'
            : 'La configuración de credenciales no está disponible. Contacta al administrador de la instalación.'}
        </p>
      )}
      <form onSubmit={submit} className="space-y-6">
        <fieldset disabled={busy || locked || !!checking} className="space-y-6">
          <section className="rounded-2xl border border-line bg-white p-5 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h3 className="text-lg font-semibold">WhatsApp · Kapso</h3>
              <span className="rounded-full bg-canvas px-3 py-1 text-sm">
                {saved.whatsappEnabled ? 'Activado' : 'Desactivado'}
              </span>
            </div>
            <label className="my-4 flex min-h-11 items-center gap-3 font-medium">
              <input
                type="checkbox"
                checked={form.whatsappEnabled}
                onChange={(e) => update('whatsappEnabled', e.target.checked)}
              />
              Activar WhatsApp
            </label>
            <div className="grid gap-5 lg:grid-cols-2">
              {secretField('kapsoKey', 'API key de Kapso', saved.hasKapsoKey, 'clearKapsoKey')}
              <label className="block text-sm">
                <FieldLabel
                  label="Identificador del número (Phone number ID)"
                  required={form.whatsappEnabled}
                />
                <input
                  className="field mt-2 w-full"
                  value={form.phoneNumberId}
                  onChange={(e) => update('phoneNumberId', e.target.value)}
                  maxLength={30}
                  inputMode="numeric"
                  required={form.whatsappEnabled}
                />
              </label>
              <label className="block text-sm">
                <FieldLabel
                  label="Número de WhatsApp del consultorio o Sandbox"
                  required={form.whatsappEnabled}
                />
                <input
                  className="field mt-2 w-full"
                  type="tel"
                  placeholder="+51999999999"
                  value={form.sender}
                  onChange={(e) => update('sender', e.target.value)}
                  maxLength={20}
                  required={form.whatsappEnabled}
                />
              </label>
              {secretField(
                'webhookSecret',
                'Secreto de firma del webhook',
                saved.hasWebhookSecret,
                'clearWebhookSecret',
              )}
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  const bytes = crypto.getRandomValues(new Uint8Array(32))
                  update('clearWebhookSecret', false)
                  update(
                    'webhookSecret',
                    Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join(''),
                  )
                }}
              >
                Generar secreto nuevo
              </Button>
              <Button
                type="button"
                variant="quiet"
                disabled={!form.webhookSecret}
                onClick={() => void copy(form.webhookSecret)}
              >
                Copiar secreto nuevo
              </Button>
            </div>
            {saved.hasKapsoKey && form.kapsoKey && (
              <p className="mt-3 text-sm text-muted">
                Si reemplazas la clave de Kapso, genera también un secreto de webhook nuevo y
                actualízalo en Kapso. Esto evita recibir eventos de la cuenta anterior.
              </p>
            )}
            <label className="mt-5 block text-sm">
              <FieldLabel
                label="Dirección HTTPS pública del receptor"
                required={form.whatsappEnabled}
              />
              <input
                type="url"
                className="field mt-2 w-full"
                placeholder="https://api-odontocare.camiluxlym.com"
                readOnly={saved.fixedWebhookUrl}
                value={form.publicBaseUrl}
                onChange={(e) => update('publicBaseUrl', e.target.value)}
                maxLength={250}
                required={form.whatsappEnabled}
              />
            </label>
            {form.publicBaseUrl && (
              <div className="mt-3 rounded-xl bg-canvas p-4">
                <p className="text-sm font-medium">Endpoint URL para copiar en Kapso</p>
                <code className="mt-2 block break-all text-xs">
                  {form.publicBaseUrl.replace(/\/+$/, '')}/api/v1/integrations/kapso/events
                </code>
                <Button
                  type="button"
                  variant="quiet"
                  onClick={() =>
                    void copy(
                      form.publicBaseUrl.replace(/\/+$/, '') + '/api/v1/integrations/kapso/events',
                    )
                  }
                >
                  Copiar dirección del webhook
                </Button>
              </div>
            )}
            <label className="mt-5 flex min-h-11 items-center gap-3 text-sm">
              <input
                type="checkbox"
                checked={form.restrictParticipants}
                onChange={(e) => update('restrictParticipants', e.target.checked)}
              />
              Recibir mensajes solo de números autorizados
            </label>
            {form.restrictParticipants && (
              <label className="mt-3 block text-sm">
                <FieldLabel label="Teléfonos autorizados" required={form.whatsappEnabled} />
                <textarea
                  aria-label="Teléfonos autorizados"
                  aria-describedby="integration-participant-help"
                  className="field mt-2 min-h-24 w-full"
                  placeholder="+51999999999"
                  value={form.participants}
                  onChange={(e) => update('participants', e.target.value)}
                  required={form.whatsappEnabled}
                />
                <span id="integration-participant-help" className="mt-1 block text-xs text-muted">
                  Incluye + y código de país. Escribe un teléfono por línea; máximo 100.
                </span>
              </label>
            )}
            <Button
              className="mt-5"
              type="button"
              variant="secondary"
              disabled={dirty || !!checking || !saved.hasKapsoKey || !saved.phoneNumberId}
              onClick={() => void check('kapso')}
            >
              {checking === 'kapso' ? 'Comprobando…' : 'Comprobar Kapso'}
            </Button>
          </section>
          <section className="rounded-2xl border border-line bg-white p-5 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h3 className="text-lg font-semibold">Asistente · GroqCloud</h3>
              <span className="rounded-full bg-canvas px-3 py-1 text-sm">
                {saved.aiEnabled ? 'Activado' : 'Desactivado'}
              </span>
            </div>
            <label className="my-4 flex min-h-11 items-center gap-3 font-medium">
              <input
                type="checkbox"
                checked={form.aiEnabled}
                onChange={(e) => update('aiEnabled', e.target.checked)}
              />
              Activar agente IA
            </label>
            <div className="grid gap-5 lg:grid-cols-2">
              {secretField('groqKey', 'API key de GroqCloud', saved.hasGroqKey, 'clearGroqKey')}
              <label className="block text-sm">
                <FieldLabel label="Modelo" required />
                <select
                  aria-label="Modelo"
                  required
                  className="field mt-2 w-full"
                  value={form.model}
                  onChange={(e) => update('model', e.target.value)}
                >
                  <option value="openai/gpt-oss-20b">GPT OSS 20B · recomendado</option>
                  <option value="openai/gpt-oss-120b">GPT OSS 120B</option>
                </select>
              </label>
            </div>
            <p className="mt-4 text-sm text-muted">
              Para responder automáticamente por WhatsApp deben estar activados ambos servicios. Las
              reglas de atención, horarios y control humano se mantienen.
            </p>
            <Button
              className="mt-5"
              type="button"
              variant="secondary"
              disabled={dirty || !!checking || !saved.hasGroqKey}
              onClick={() => void check('groq')}
            >
              {checking === 'groq' ? 'Comprobando…' : 'Comprobar GroqCloud'}
            </Button>
          </section>
        </fieldset>
        {result && (
          <p
            role="status"
            className={
              result.success ? 'rounded-xl bg-brand-50 p-4 text-sm text-brand-800' : 'error-box'
            }
          >
            {result.message}
          </p>
        )}
        {error && (
          <p role="alert" className="error-box">
            {error}
          </p>
        )}
        <p className="text-sm text-muted">
          Al cambiar o desactivar una integración se retiran las solicitudes y envíos pendientes de
          la configuración anterior. Las citas ya registradas y su historial se conservan. Los
          cambios se aplican al guardar.
        </p>
        <div className="flex flex-wrap gap-3">
          <Button type="submit" disabled={busy || locked || !!checking || !dirty}>
            {busy ? 'Guardando…' : 'Guardar integraciones'}
          </Button>
          <Button type="button" variant="secondary" disabled={busy} onClick={() => void load()}>
            Recargar configuración
          </Button>
          <Link className="self-center text-sm text-link" to="/conversaciones">
            Ir a conversaciones
          </Link>
        </div>
      </form>
    </div>
  )
}
