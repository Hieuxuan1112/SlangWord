import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { REFRESH_KEY, clearSession, setUnauthorizedHandler, storeSession } from '../api/client'
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
    // Tell the server first so the refresh token is revoked rather than left
    // valid for a week, but never block sign-out on the network.
    const refreshToken = localStorage.getItem(REFRESH_KEY)
    if (refreshToken) {
      authApi.logout(refreshToken).catch(() => undefined)
    }
    clearSession()
    localStorage.removeItem(USER_KEY)
    setSession(null)
  }, [])

  // Any 401 from the API clears the session exactly once.
  useEffect(() => {
    setUnauthorizedHandler(logout)
  }, [logout])

  const persist = useCallback((result: AuthSession) => {
    storeSession(result)
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
