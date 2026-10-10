/* Presentation helpers. No business rules or API calls. */
(() => {
  'use strict';
  let region;
  function notify(message) {
    if (!region) {
      region = document.createElement('div');
      region.className = 'toast-region';
      region.setAttribute('aria-label', 'Notificaciones');
      document.body.append(region);
    }
    const error = /error|fallo|no se pudo|obligatorio|por favor/i.test(String(message));
    const toast = document.createElement('div');
    toast.className = 'toast' + (error ? ' toast-error' : '');
    toast.setAttribute('role', error ? 'alert' : 'status');
    const text = document.createElement('p');
    text.textContent = String(message);
    const close = document.createElement('button');
    close.type = 'button';
    close.textContent = '\u00d7';
    close.setAttribute('aria-label', 'Cerrar notificación');
    close.addEventListener('click', () => toast.remove());
    toast.append(text, close);
    region.append(toast);
    // Notifications disappear after 3 seconds
    let timer;
    const pause = () => clearTimeout(timer);
    const resume = () => { timer = setTimeout(() => toast.remove(), 3000); };
    toast.addEventListener('mouseenter', pause);
    toast.addEventListener('mouseleave', resume);
    toast.addEventListener('focusin', pause);
    toast.addEventListener('focusout', resume);
    resume();
  }
  function confirmAction(message) {
    return new Promise(resolve => {
      const dialog = document.createElement('dialog');
      dialog.className = 'confirm-dialog';
      dialog.setAttribute('aria-labelledby', 'confirmation-title');
      const title = document.createElement('h3');
      title.id = 'confirmation-title'; title.textContent = 'Confirmar eliminaci\u00f3n';
      const text = document.createElement('p'); text.textContent = String(message);
      const actions = document.createElement('div'); actions.className = 'modal-actions';
      const cancel = document.createElement('button');
      cancel.type = 'button'; cancel.className = 'btn-cancel'; cancel.textContent = 'Cancelar';
      const confirm = document.createElement('button');
      confirm.type = 'button'; confirm.className = 'btn-danger'; confirm.textContent = 'Eliminar';
      cancel.addEventListener('click', () => dialog.close('cancel'));
      confirm.addEventListener('click', () => dialog.close('confirm'));
      actions.append(cancel, confirm); dialog.append(title, text, actions); document.body.append(dialog);
      dialog.addEventListener('close', () => { const accepted = dialog.returnValue === 'confirm'; dialog.remove(); resolve(accepted); }, {once:true});
      dialog.showModal(); cancel.focus();
    });
  }
  window.DeskUI = { notify, confirm: confirmAction };
  const visible = el => !!el && !el.closest('[inert]') && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
  const focusable = el => [...el.querySelectorAll('button:not(:disabled),a[href],input:not(:disabled):not([type=hidden]),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]')].filter(visible);
  function trap(event, scope) {
    if (event.key !== 'Tab') return;
    const items = focusable(scope);
    if (!items.length) { event.preventDefault(); scope.focus(); return; }
    const first = items[0], last = items[items.length - 1];
    if (event.shiftKey && (document.activeElement === first || !scope.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && (document.activeElement === last || !scope.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
  }
  document.addEventListener('DOMContentLoaded', () => {
    const sidebar = document.querySelector('.sidebar');
    const main = document.querySelector('main');
    const menu = document.querySelector('.mobile-menu');
    const app = document.querySelector('.app-container');
    const narrow = matchMedia('(max-width:1023px)');
    let mobileOpen = false;
    function setMobile(open, restore = true) {
      mobileOpen = open && narrow.matches;
      document.body.classList.toggle('sidebar-open', mobileOpen);
      menu?.setAttribute('aria-expanded', String(mobileOpen));
      if (sidebar) sidebar.inert = narrow.matches && !mobileOpen;
      if (main) main.inert = mobileOpen;
      if (mobileOpen) focusable(sidebar)[0]?.focus();
      else if (restore && narrow.matches) menu?.focus();
    }
    menu?.addEventListener('click', () => setMobile(!mobileOpen));
    document.querySelector('.sidebar-backdrop')?.addEventListener('click', () => setMobile(false));
    narrow.addEventListener('change', () => setMobile(false, false));
    setMobile(false, false);
    const modalStates = new Map();
    let activeModal = null;
    let returnFocus = null;
    function syncDialogs() {
      const open = [...modalStates.keys()].filter(el => el.style.display !== 'none');
      const next = open[open.length - 1] || null;
      if (next === activeModal) return;
      if (next) {
        if (!activeModal) returnFocus = document.activeElement;
        activeModal = next;
        if (app) app.inert = true;
        open.forEach(el => { el.inert = el !== next; });
        const input = focusable(next).find(el => el.matches('input,select,textarea'));
        (input || focusable(next)[0] || next.querySelector('.modal'))?.focus();
      } else {
        activeModal = null;
        if (app) app.inert = false;
        modalStates.forEach((_, el) => { el.inert = false; });
        if (visible(returnFocus)) returnFocus.focus();
        else document.querySelector('.view.active-view h2')?.focus();
      }
    }
    document.querySelectorAll('.modal-overlay').forEach((overlay, i) => {
      const dialog = overlay.querySelector('.modal');
      dialog.setAttribute('role', 'dialog');
      dialog.setAttribute('aria-modal', 'true');
      dialog.tabIndex = -1;
      const title = dialog.querySelector('h3');
      if (title) { if (!title.id) title.id = 'dialog-title-' + i; dialog.setAttribute('aria-labelledby', title.id); }
      const close = document.createElement('button');
      close.type = 'button'; close.className = 'btn-icon modal-close'; close.textContent = '\u00d7';
      close.setAttribute('aria-label', 'Cerrar diálogo');
      close.addEventListener('click', () => { overlay.style.display = 'none'; });
      dialog.prepend(close);
      modalStates.set(overlay, true);
      new MutationObserver(syncDialogs).observe(overlay, {attributes:true,attributeFilter:['style']});
    });
    function updateNav(focus = false) {
      const nav = document.querySelector('.nav-links a.active');
      document.querySelectorAll('.nav-links a').forEach(a => {
        if (a === nav) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
      });
      const label = document.getElementById('current-view-label');
      const view = [...document.querySelectorAll('.view')].find(el => el.style.display !== 'none' && el.classList.contains('active-view'));
      if (label) label.textContent = view?.id === 'view-ticket-detail' ? 'Detalle del ticket' : nav?.textContent.trim() || 'Tickets';
      if (focus && view) { main.scrollTop = 0; view.querySelector('h2')?.focus({preventScroll:true}); }
    }
    document.querySelectorAll('.view h2').forEach(el => { el.tabIndex = -1; });
    const navObserver = new MutationObserver(() => updateNav());
    document.querySelectorAll('.nav-links a').forEach(a => {
      navObserver.observe(a, {attributes:true,attributeFilter:['class']});
      a.addEventListener('click', () => { if (mobileOpen) setMobile(false, false); requestAnimationFrame(() => updateNav(true)); });
    });
    document.querySelectorAll('.view').forEach(el => navObserver.observe(el, {attributes:true,attributeFilter:['class']}));
    updateNav();
    document.querySelector('.context-toggle')?.addEventListener('click', e => {
      const expanded = document.querySelector('.asistente-context-panel').classList.toggle('context-expanded');
      e.currentTarget.setAttribute('aria-expanded', String(expanded));
    });
    document.querySelector('.attach-button')?.addEventListener('keydown', e => {
      if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); document.getElementById('asistente-image-input').click(); }
    });
    // Associate existing labels without changing field IDs or form submission.
    document.querySelectorAll('.form-group label:not([for])').forEach(label => {
      const field = label.parentElement.querySelector('input:not([type=hidden]),select,textarea');
      if (field?.id) label.htmlFor = field.id;
    });
    const names = {'asistente-input':'Consulta al asistente','chat-input':'Consulta de soporte','ki-search-input':'Buscar en el conocimiento','ki-filter-tipo':'Tipo de conocimiento','ki-filter-categoria':'Categoría ITIL','log-text':'Acción realizada','log-mins':'Minutos invertidos'};
    Object.entries(names).forEach(([id,name]) => document.getElementById(id)?.setAttribute('aria-label',name));
    ['asistente-history','chat-history'].forEach(id => {
      const log = document.getElementById(id);
      if (log) { log.setAttribute('role','log'); log.setAttribute('aria-live','polite'); log.setAttribute('aria-relevant','additions'); }
    });
    document.querySelectorAll('.ki-processing-bar,#autofill-loading').forEach(el => el.setAttribute('role','status'));
    document.addEventListener('keydown', e => {
      if (activeModal) {
        if (e.key === 'Escape') { e.preventDefault(); activeModal.style.display = 'none'; }
        else trap(e, activeModal);
        return;
      }
      if (mobileOpen) {
        if (e.key === 'Escape') { e.preventDefault(); setMobile(false); } else trap(e, sidebar);
        return;
      }
      const dropdown = document.querySelector('.ticket-dropdown.show');
      if (dropdown && e.key === 'Escape') { dropdown.classList.remove('show'); dropdown.previousElementSibling.setAttribute('aria-expanded','false'); dropdown.previousElementSibling.focus(); }
      if (dropdown && ['ArrowDown','ArrowUp'].includes(e.key)) {
        const items = focusable(dropdown), index = items.indexOf(document.activeElement);
        e.preventDefault(); items[(index + (e.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length]?.focus();
      }
    });
    const dropdownObserver = new MutationObserver(records => records.forEach(({target}) => {
      if (target.classList.contains('ticket-dropdown')) target.previousElementSibling?.setAttribute('aria-expanded', String(target.classList.contains('show')));
    }));
    dropdownObserver.observe(document.body,{subtree:true,attributes:true,attributeFilter:['class']});
  });
})();
