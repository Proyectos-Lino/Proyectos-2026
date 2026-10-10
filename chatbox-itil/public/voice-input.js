(function () {
  'use strict';
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  let active = null;
  const icon = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8"/></svg>';

  function attach(input) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'voice-button';
    button.innerHTML = icon;
    button.setAttribute('aria-label', 'Dictar mensaje');
    button.setAttribute('aria-pressed', 'false');
    button.title = 'Dictar mensaje';
    input.after(button);
    const status = document.createElement('p');
    status.className = 'voice-status';
    status.id = input.id + '-voice-status';
    status.setAttribute('role', 'status');
    input.parentElement.after(status);
    button.setAttribute('aria-describedby', status.id);
    if (!Recognition || !window.isSecureContext) {
      button.disabled = true;
      status.textContent = !window.isSecureContext
        ? 'Para usar voz, abre el sistema mediante HTTPS o localhost.'
        : 'El dictado no está disponible en este navegador. Puedes escribir tu mensaje.';
      return;
    }

    let session = null;
    function finish(message) {
      if (!session) return;
      const old = session;
      session = null;
      if (active === cancel) active = null;
      old.abort();
      button.setAttribute('aria-pressed', 'false');
      button.setAttribute('aria-label', 'Dictar mensaje');
      button.title = 'Dictar mensaje';
      status.textContent = message;
    }
    function cancel() { finish('Dictado detenido. Revisa el texto antes de enviarlo.'); }
    button.addEventListener('click', () => {
      if (session) { cancel(); return; }
      if (input.disabled || input.readOnly) return;
      active?.();
      const recognition = new Recognition();
      session = recognition;
      active = cancel;
      recognition.lang = 'es-PE';
      recognition.continuous = true;
      recognition.interimResults = true;
      const initial = input.value.trimEnd();
      recognition.onresult = event => {
        if (session !== recognition) return;
        const parts = Array.from(event.results, result => result[0].transcript.trim());
        input.value = [initial, ...parts].filter(Boolean).join(' ');
        input.dispatchEvent(new Event('input', { bubbles: true }));
      };
      recognition.onerror = event => {
        if (session !== recognition) return;
        const messages = {
          'not-allowed': 'Permite el acceso al micrófono en tu navegador y vuelve a intentar.',
          'service-not-allowed': 'El navegador no permite el servicio de dictado.',
          'audio-capture': 'No se encontró un micrófono disponible.',
          'no-speech': 'No se detectó voz. Pulsa el micrófono para intentar otra vez.',
          network: 'No se pudo conectar al servicio de dictado. Revisa tu conexión.'
        };
        finish(messages[event.error] || 'No se pudo completar el dictado. Puedes seguir escribiendo.');
      };
      recognition.onend = () => {
        if (session === recognition) cancel();
      };
      button.setAttribute('aria-pressed', 'true');
      button.setAttribute('aria-label', 'Detener dictado');
      button.title = 'Detener dictado';
      status.textContent = 'Escuchando… Habla y pulsa de nuevo el micrófono para terminar. Luego pulsa Enviar.';
      try { recognition.start(); }
      catch (_) { finish('No se pudo iniciar el micrófono. Revisa los permisos y vuelve a intentar.'); }
    });
    // Detener antes de enviar o editar evita que un resultado tardío cambie el mensaje.
    input.addEventListener('beforeinput', cancel);
    input.addEventListener('keydown', event => { if (event.key === 'Enter' && !event.shiftKey) cancel(); });
    input.closest('form')?.addEventListener('submit', cancel, true);
    new MutationObserver(() => {
      const disabled = input.disabled || input.readOnly;
      if (button.disabled !== disabled) button.disabled = disabled;
      if (session && (button.disabled || !input.getClientRects().length)) cancel();
    }).observe(document.body, { attributes: true, subtree: true, attributeFilter: ['style', 'class', 'disabled', 'readonly', 'hidden'] });
  }
  function init() {
    ['asistente-input', 'chat-input'].forEach(id => {
      const input = document.getElementById(id);
      if (input) attach(input);
    });
  }
  document.addEventListener('visibilitychange', () => { if (document.hidden) active?.(); });
  window.addEventListener('pagehide', () => active?.());
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
