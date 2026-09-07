import axios from 'axios'
import type { AuthSession } from '../types'

export const TOKEN_KEY = 'slangword.token'
export const REFRESH_KEY = 'slangword.refresh'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let onSessionEnded: (() => void) | null = null

/** AuthProvider registers its logout here so an unrecoverable 401 clears the session once. */
export function setUnauthorizedHandler(handler: () => void) {
  onSessionEnded = handler
}

export function storeSession(session: AuthSession) {
  localStorage.setItem(TOKEN_KEY, session.accessToken)
  localStorage.setItem(REFRESH_KEY, session.refreshToken)
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

/*
 * The access token is deliberately short-lived, so a 401 is expected during
 * normal use rather than exceptional. On the first 401 the client exchanges its
 * refresh token for a new pair and replays the original request, which keeps the
 * expiry invisible to the user.
 *
 * The in-flight promise is shared: a page that fires several requests at once
 * would otherwise send several refreshes, and since the server rotates tokens,
 * all but one would present an already-used token and trip reuse detection —
 * logging the user out precisely because they loaded a busy page.
 */
let refreshInFlight: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem(REFRESH_KEY)
  if (!refreshToken) {
    throw new Error('No refresh token')
  }
  // A bare axios call, not `api`: the interceptors below must not see this request.
  const { data } = await axios.post<AuthSession>(
    `${api.defaults.baseURL}/auth/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' } },
  )
  storeSession(data)
  return data.accessToken
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401 || !error.config) {
      return Promise.reject(error)
    }

    const request = error.config as typeof error.config & { _retried?: boolean }
    // Only ever retry once: if the replay also 401s, the session is genuinely over.
    if (request._retried || request.url?.includes('/auth/')) {
      clearSession()
      onSessionEnded?.()
      return Promise.reject(error)
    }
    request._retried = true

    try {
      refreshInFlight = refreshInFlight ?? refreshAccessToken().finally(() => {
        refreshInFlight = null
      })
      const token = await refreshInFlight
      request.headers.Authorization = `Bearer ${token}`
      return api.request(request)
    } catch {
      clearSession()
      onSessionEnded?.()
      return Promise.reject(error)
    }
  },
)

/** Pull the human-readable message out of an RFC 7807 ProblemDetail body. */
export function problemMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(error)) {
    const detail = (error.response?.data as { detail?: string } | undefined)?.detail
    return detail ?? error.message ?? fallback
  }
  return fallback
}

export function isConflict(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 409
}
