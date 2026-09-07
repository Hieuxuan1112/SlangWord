import { api } from './client'
import type { Page, QuizStats, SearchHistoryEntry } from '../types'

export async function getHistory(page: number, size = 20) {
  const { data } = await api.get<Page<SearchHistoryEntry>>('/history', { params: { page, size } })
  return data
}

export async function getQuizStats() {
  const { data } = await api.get<QuizStats>('/history/quiz-stats')
  return data
}

export async function clearHistory() {
  await api.delete('/history')
}
