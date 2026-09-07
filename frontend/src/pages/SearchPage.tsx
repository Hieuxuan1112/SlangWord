import { useCallback, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getRandomWord, searchWords } from '../api/slangWords'
import type { SearchField } from '../api/slangWords'
import { SearchBar } from '../components/SearchBar'
import { WordCard } from '../components/WordCard'
import { Pagination } from '../components/Pagination'

export function SearchPage() {
  const [query, setQuery] = useState('')
  const [field, setField] = useState<SearchField>('word')
  const [page, setPage] = useState(0)

  const handleQueryChange = useCallback((value: string) => {
    setQuery(value)
    setPage(0)
  }, [])

  const handleFieldChange = useCallback((value: SearchField) => {
    setField(value)
    setPage(0)
  }, [])

  const results = useQuery({
    queryKey: ['slang-words', query, field, page],
    queryFn: () => searchWords(query, field, page),
  })

  const random = useQuery({ queryKey: ['random-word'], queryFn: getRandomWord, enabled: false })

  return (
    <div className="space-y-6">
      <SearchBar field={field} onQueryChange={handleQueryChange} onFieldChange={handleFieldChange} />

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={() => random.refetch()}
          className="rounded-md bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          Random word
        </button>
        {random.data && (
          <Link
            to={`/words/${encodeURIComponent(random.data.word)}`}
            className="text-sm text-sky-600 underline"
          >
            {random.data.word} — {random.data.definitions[0]}
          </Link>
        )}
      </div>

      {results.isPending && <p className="text-slate-500">Loading…</p>}
      {results.isError && <p className="text-red-600">Could not load results.</p>}

      {results.data && (
        <>
          <p className="text-sm text-slate-500">{results.data.totalElements} result(s)</p>
          <ul className="space-y-3">
            {results.data.content.map((word) => (
              <WordCard key={word.id} word={word} />
            ))}
          </ul>
          <Pagination page={results.data.page} totalPages={results.data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  )
}
