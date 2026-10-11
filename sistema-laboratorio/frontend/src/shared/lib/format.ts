let configuredTimeZone = 'America/Lima';

export function setApplicationTimeZone(timeZone: string) {
  configuredTimeZone = timeZone;
}

export function formatDate(value: string): string {
  return new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: configuredTimeZone,
  }).format(new Date(value));
}
