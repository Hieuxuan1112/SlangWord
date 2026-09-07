import { api } from './client'
import type { Page, SlangWord } from '../types'

export type SearchField = 'word' | 'definition'

export async function searchWords(q: string, field: SearchField, page: number, size = 20) {
  const { data } = await api.get<Page<SlangWord>>('/slang-words', { params: { q, field, page, size } })
  return data
}

export async function getWord(word: string) {
  const { data } = await api.get<SlangWord>(`/slang-words/${encodeURIComponent(word)}`)
  return data
}

export async function getRandomWord() {
  const { data } = await api.get<SlangWord>('/slang-words/random')
  return data
}

export async function createWord(word: string, definitions: string[], overwrite = false) {
  const { data } = await api.post<SlangWord>('/slang-words', { word, definitions }, { params: { overwrite } })
  return data
}

export async function updateWord(word: string, definitions: string[]) {
  const { data } = await api.put<SlangWord>(`/slang-words/${encodeURIComponent(word)}`, { definitions })
  return data
}

export async function deleteWord(word: string) {
  await api.delete(`/slang-words/${encodeURIComponent(word)}`)
}
