import { createContext, type ReactNode } from 'react'

export interface NotificationItem { id: string; content: ReactNode; tone: 'success' | 'error'; target: HTMLElement }
export const NotificationContext = createContext<{
  show: (item: NotificationItem) => void; remove: (id: string) => void
} | null>(null)
