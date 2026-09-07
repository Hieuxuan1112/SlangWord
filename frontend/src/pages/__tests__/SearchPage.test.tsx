import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SearchPage } from '../SearchPage'
import type { Page, SlangWord } from '../../types'

const searchWords = vi.fn()
const getRandomWord = vi.fn()

vi.mock('../../api/slangWords', () => ({
  searchWords: (...args: unknown[]) => searchWords(...args),
  getRandomWord: () => getRandomWord(),
}))

const page: Page<SlangWord> = {
  content: [
    {
      id: 1,
      word: 'BBC',
      definitions: ['British Broadcasting Corporation'],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  last: true,
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('SearchPage', () => {
  beforeEach(() => {
    searchWords.mockReset()
    getRandomWord.mockReset()
  })

  it('renders the results returned by the API', async () => {
    searchWords.mockResolvedValue(page)

    renderPage()

    expect(await screen.findByRole('link', { name: 'BBC' })).toBeInTheDocument()
    expect(screen.getByText('British Broadcasting Corporation')).toBeInTheDocument()
    expect(screen.getByText('1 result(s)')).toBeInTheDocument()
  })

  it('shows an error message when the request fails', async () => {
    searchWords.mockRejectedValue(new Error('boom'))

    renderPage()

    expect(await screen.findByText('Could not load results.')).toBeInTheDocument()
  })
})
