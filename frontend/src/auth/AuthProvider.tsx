import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { TOKEN_KEY, setUnauthorizedHandler } from '../api/client'
import * as authApi from '../api/auth'
import { AuthContext } from './AuthContext'
import type { AuthState } from './AuthContext'
import type { AuthSession } from '../types'

const USER_KEY = 'slangword.user'

interface StoredUser {
  username: string
  role: string
}

function readStoredUser(): StoredUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as StoredUser) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<StoredUser | null>(readStoredUser)

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    setSession(null)
  }, [])

  // Any 401 from the API clears the session exactly once.
  useEffect(() => {
    setUnauthorizedHandler(logout)
  }, [logout])

  const persist = useCallback((result: AuthSession) => {
    localStorage.setItem(TOKEN_KEY, result.token)
    const user: StoredUser = { username: result.username, role: result.role }
    localStorage.setItem(USER_KEY, JSON.stringify(user))
    setSession(user)
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      username: session?.username ?? null,
      role: session?.role ?? null,
      isAuthenticated: session !== null,
      login: async (username, password) => persist(await authApi.login(username, password)),
      register: async (username, password) => persist(await authApi.register(username, password)),
      logout,
    }),
    [session, logout, persist],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
