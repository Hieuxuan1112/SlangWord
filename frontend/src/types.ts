export interface SlangWord {
  id: number
  word: string
  definitions: string[]
  createdAt: string
  updatedAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export type QuizMode = 'WORD_FROM_DEFINITION' | 'DEFINITION_FROM_WORD'

export interface QuizQuestion {
  mode: QuizMode
  prompt: string
  correctAnswer: string
  options: string[]
}

export interface QuizAnswerResult {
  correct: boolean
  correctAnswer: string
  totalAnswered: number
  totalCorrect: number
}

export interface SearchHistoryEntry {
  id: number
  keyword: string
  resultCount: number
  searchedAt: string
}

export interface QuizStats {
  totalAnswered: number
  totalCorrect: number
  accuracy: number
}

export interface AuthSession {
  /** Short-lived JWT sent with every request. */
  accessToken: string
  /** Long-lived, revocable; exchanged at /auth/refresh when the access token expires. */
  refreshToken: string
  username: string
  role: string
  expiresInSeconds: number
}
