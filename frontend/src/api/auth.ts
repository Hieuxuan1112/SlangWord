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
