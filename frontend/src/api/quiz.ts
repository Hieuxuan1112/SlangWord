import { api } from './client'
import type { QuizAnswerResult, QuizMode, QuizQuestion } from '../types'

const MODE_PARAM: Record<QuizMode, string> = {
  WORD_FROM_DEFINITION: 'word-from-definition',
  DEFINITION_FROM_WORD: 'definition-from-word',
}

export async function nextQuestion(mode: QuizMode) {
  const { data } = await api.get<QuizQuestion>('/quiz', { params: { mode: MODE_PARAM[mode] } })
  return data
}

export async function submitAnswer(question: QuizQuestion, chosenAnswer: string) {
  const { data } = await api.post<QuizAnswerResult>('/quiz/answer', {
    mode: question.mode,
    prompt: question.prompt,
    correctAnswer: question.correctAnswer,
    chosenAnswer,
  })
  return data
}
