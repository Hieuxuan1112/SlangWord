import { api } from './client'
import type { AuthSession } from '../types'

export async function register(username: string, password: string) {
  const { data } = await api.post<AuthSession>('/auth/register', { username, password })
  return data
}

export async function login(username: string, password: string) {
  const { data } = await api.post<AuthSession>('/auth/login', { username, password })
  return data
}

/** Best effort: the local session is cleared whether or not the server is reachable. */
export async function logout(refreshToken: string) {
  await api.post('/auth/logout', { refreshToken })
}
