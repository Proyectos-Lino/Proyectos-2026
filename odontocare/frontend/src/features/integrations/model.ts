export interface IntegrationSettings {
  version: number
  whatsappEnabled: boolean
  aiEnabled: boolean
  hasKapsoKey: boolean
  phoneNumberId: string
  sender: string
  hasWebhookSecret: boolean
  publicBaseUrl: string
  webhookUrl: string
  fixedWebhookUrl: boolean
  restrictParticipants: boolean
  participants: string[]
  hasGroqKey: boolean
  model: string
  encryptionReady: boolean
  credentialsLocked: boolean
}
export interface IntegrationForm {
  whatsappEnabled: boolean
  aiEnabled: boolean
  phoneNumberId: string
  sender: string
  publicBaseUrl: string
  restrictParticipants: boolean
  participants: string
  model: string
  kapsoKey: string
  webhookSecret: string
  groqKey: string
  clearKapsoKey: boolean
  clearWebhookSecret: boolean
  clearGroqKey: boolean
}
export function formFrom(settings: IntegrationSettings): IntegrationForm {
  return {
    whatsappEnabled: settings.whatsappEnabled,
    aiEnabled: settings.aiEnabled,
    phoneNumberId: settings.phoneNumberId,
    sender: settings.sender,
    publicBaseUrl: settings.publicBaseUrl,
    restrictParticipants: settings.restrictParticipants,
    participants: settings.participants.join('\n'),
    model: settings.model,
    kapsoKey: '',
    webhookSecret: '',
    groqKey: '',
    clearKapsoKey: false,
    clearWebhookSecret: false,
    clearGroqKey: false,
  }
}
