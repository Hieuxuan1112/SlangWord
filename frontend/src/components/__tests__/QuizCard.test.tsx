import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { QuizCard } from '../QuizCard'
import type { QuizQuestion } from '../../types'

const question: QuizQuestion = {
  mode: 'WORD_FROM_DEFINITION',
  prompt: 'British Broadcasting Corporation',
  correctAnswer: 'BBC',
  options: ['AAA', 'BBC', 'CCC', 'DDD'],
}

describe('QuizCard', () => {
  it('renders the prompt and every option', () => {
    render(<QuizCard question={question} chosen={null} onChoose={vi.fn()} />)

    expect(screen.getByText('British Broadcasting Corporation')).toBeInTheDocument()
    expect(screen.getAllByRole('button')).toHaveLength(4)
  })

  it('reports the choice back to the parent', async () => {
    const onChoose = vi.fn()
    render(<QuizCard question={question} chosen={null} onChoose={onChoose} />)

    await userEvent.click(screen.getByRole('button', { name: 'BBC' }))

    expect(onChoose).toHaveBeenCalledWith('BBC')
  })

  it('shows the verdict and locks the options once answered', () => {
    render(<QuizCard question={question} chosen="AAA" onChoose={vi.fn()} />)

    expect(screen.getByRole('status')).toHaveTextContent('Wrong — the answer is BBC')
    expect(screen.getByRole('button', { name: 'BBC' })).toBeDisabled()
  })

  it('reports a correct answer', () => {
    render(<QuizCard question={question} chosen="BBC" onChoose={vi.fn()} />)

    expect(screen.getByRole('status')).toHaveTextContent('Correct')
  })
})
