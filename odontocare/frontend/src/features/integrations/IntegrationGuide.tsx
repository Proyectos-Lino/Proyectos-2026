export function IntegrationGuide() {
  return (
    <details className="rounded-2xl border border-line bg-white p-5">
      <summary className="cursor-pointer font-semibold">Guía para conectar tus cuentas</summary>
      <div className="mt-5 space-y-6 text-sm leading-7 text-muted">
        <section>
          <h3 className="font-semibold text-ink">1. Crea tu cuenta de Kapso y conecta WhatsApp</h3>
          <p>
            Entra a{' '}
            <a className="text-link" href="https://app.kapso.ai" target="_blank" rel="noreferrer">
              Kapso
            </a>
            , crea tu cuenta y un proyecto. Para probar, abre Quick setup o Phone numbers, activa el
            Sandbox y únete desde tu celular siguiendo el código que te muestre Kapso. Para un
            consultorio, conecta su número mediante el proceso de WhatsApp Business que ofrece
            Kapso.
          </p>
          <p>
            En Integrations → API keys genera una clave del proyecto. En el número, copia el Phone
            number ID (identificador interno, no es el teléfono) y el número visible con su código
            de país. Guarda esos valores en la sección de WhatsApp.
          </p>
          <p>
            <a
              className="text-link"
              href="https://docs.kapso.ai/docs/how-to/whatsapp/use-sandbox-for-testing"
              target="_blank"
              rel="noreferrer"
            >
              Guía oficial del Sandbox
            </a>
          </p>
        </section>
        <section>
          <h3 className="font-semibold text-ink">2. Configura la recepción de mensajes</h3>
          <p>
            Copia la dirección completa del webhook que aparece en esta pantalla. Es la dirección de
            recepción de mensajes de tu instalación y está lista para registrarla en Kapso.
          </p>
          <p>
            Crea en Kapso un webhook vinculado al mismo número: Endpoint URL = dirección copiada,
            formato v2, firma HMAC SHA-256 y el mismo secreto que guardes aquí. Puedes generar un
            secreto y copiarlo antes de guardar; después quedará oculto.
          </p>
          <p>
            Selecciona estos cinco eventos: whatsapp.message.received, whatsapp.message.sent,
            whatsapp.message.delivered, whatsapp.message.read y whatsapp.message.failed. Guarda y
            activa el webhook. Si estás en Sandbox, autoriza aquí los teléfonos participantes con +
            y código de país.
          </p>
          <p>
            <a
              className="text-link"
              href="https://docs.kapso.ai/docs/platform/webhooks/overview"
              target="_blank"
              rel="noreferrer"
            >
              Webhooks de Kapso
            </a>
          </p>
        </section>
        <section>
          <h3 className="font-semibold text-ink">3. Configura GroqCloud para el asistente</h3>
          <p>
            Abre{' '}
            <a
              className="text-link"
              href="https://console.groq.com"
              target="_blank"
              rel="noreferrer"
            >
              GroqCloud
            </a>
            , crea tu cuenta, entra a API Keys y genera una clave. Pégala en la sección del agente y
            conserva el modelo recomendado openai/gpt-oss-20b. No necesitas instalar un modelo en tu
            computadora.
          </p>
          <p>
            <a
              className="text-link"
              href="https://console.groq.com/docs/quickstart"
              target="_blank"
              rel="noreferrer"
            >
              Inicio oficial de GroqCloud
            </a>
          </p>
        </section>
        <section>
          <h3 className="font-semibold text-ink">4. Guarda, comprueba y activa</h3>
          <p>
            Primero guarda los datos con las integraciones desactivadas. Pulsa Comprobar Kapso y
            Comprobar GroqCloud; estas consultas no envían mensajes ni generan inferencias. Después
            activa WhatsApp para responder manualmente o activa también el agente para atención
            automática, y vuelve a guardar.
          </p>
          <p>
            Para una reserva, configura antes servicios habilitados, profesionales y horarios en el
            consultorio. Envía un mensaje desde un teléfono autorizado y revisa la conversación. Una
            conexión comprobada no confirma por sí sola que el webhook o la entrega real estén
            funcionando.
          </p>
        </section>
        <p>
          Los planes, saldos y límites dependen de cada proveedor. El texto libre de WhatsApp
          requiere la ventana de atención de 24 horas desde el último mensaje del contacto. Puedes
          desactivar estas funciones sin afectar agenda, pacientes ni finanzas.
        </p>
      </div>
    </details>
  )
}
