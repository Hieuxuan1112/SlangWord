import { createContext } from 'react'

export interface AuthState {
  username: string | null
  role: string | null
  isAuthenticated: boolean
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string) => Promise<void>
  logout: () => void
}

// Kept apart from AuthProvider so the provider module only exports a component,
// which is what React Fast Refresh needs.
export const AuthContext = createContext<AuthState | undefined>(undefined)
