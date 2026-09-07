import type { QuizQuestion } from '../types'

interface Props {
  question: QuizQuestion
  chosen: string | null
  onChoose: (option: string) => void
}

export function QuizCard({ question, chosen, onChoose }: Props) {
  const answered = chosen !== null

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <p className="mb-1 text-xs uppercase tracking-wide text-slate-400">
        {question.mode === 'WORD_FROM_DEFINITION' ? 'Which slang word means…' : 'What does this slang word mean?'}
      </p>
      <p className="mb-5 text-lg font-medium text-slate-900">{question.prompt}</p>

      <ul className="space-y-2">
        {question.options.map((option) => {
          const isCorrect = option === question.correctAnswer
          const isChosen = option === chosen
          let style = 'border-slate-200 hover:border-sky-400'
          if (answered && isCorrect) {
            style = 'border-emerald-500 bg-emerald-50'
          } else if (answered && isChosen) {
            style = 'border-red-500 bg-red-50'
          }

          return (
            <li key={option}>
              <button
                type="button"
                disabled={answered}
                onClick={() => onChoose(option)}
                className={`w-full rounded-md border px-4 py-2 text-left text-sm ${style}`}
              >
                {option}
              </button>
            </li>
          )
        })}
      </ul>

      {answered && (
        <p className="mt-4 text-sm font-medium" role="status">
          {chosen === question.correctAnswer
            ? 'Correct'
            : `Wrong — the answer is ${question.correctAnswer}`}
        </p>
      )}
    </div>
  )
}
