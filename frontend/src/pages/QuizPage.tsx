import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { nextQuestion, submitAnswer } from '../api/quiz'
import { QuizCard } from '../components/QuizCard'
import { useAuth } from '../auth/useAuth'
import type { QuizMode } from '../types'

const MODES: { value: QuizMode; label: string }[] = [
  { value: 'WORD_FROM_DEFINITION', label: 'Guess the word' },
  { value: 'DEFINITION_FROM_WORD', label: 'Guess the meaning' },
]

export function QuizPage() {
  const { isAuthenticated } = useAuth()
  const [mode, setMode] = useState<QuizMode>('WORD_FROM_DEFINITION')
  const [chosen, setChosen] = useState<string | null>(null)
  const [streak, setStreak] = useState(0)

  const question = useQuery({ queryKey: ['quiz', mode], queryFn: () => nextQuestion(mode) })

  const answer = useMutation({
    mutationFn: (option: string) => submitAnswer(question.data!, option),
  })

  const choose = (option: string) => {
    setChosen(option)
    const correct = option === question.data?.correctAnswer
    setStreak((current) => (correct ? current + 1 : 0))
    // Anonymous players still see the verdict; only signed-in attempts are recorded.
    if (isAuthenticated) {
      answer.mutate(option)
    }
  }

  const skipToNext = () => {
    setChosen(null)
    answer.reset()
    question.refetch()
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-2">
          {MODES.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => {
                setMode(option.value)
                setChosen(null)
                answer.reset()
              }}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${
                mode === option.value ? 'bg-slate-900 text-white' : 'border border-slate-300 hover:bg-slate-100'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
        <span className="text-sm text-slate-500">Streak: {streak}</span>
      </div>

      {question.isPending && <p className="text-slate-500">Loading question…</p>}
      {question.isError && <p className="text-red-600">Could not load a question.</p>}

      {question.data && (
        <>
          <QuizCard question={question.data} chosen={chosen} onChoose={choose} />

          {answer.data && (
            <p className="text-sm text-slate-500">
              {answer.data.totalCorrect} correct out of {answer.data.totalAnswered} answered
            </p>
          )}

          {!isAuthenticated && chosen !== null && (
            <p className="text-sm text-slate-500">
              <Link to="/login" className="text-sky-600 underline">
                Log in
              </Link>{' '}
              to save your score.
            </p>
          )}

          <button
            type="button"
            onClick={skipToNext}
            className="rounded-md bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-500"
          >
            {chosen === null ? 'Skip question' : 'Next question'}
          </button>
        </>
      )}
    </div>
  )
}
