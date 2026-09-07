import { useEffect, useState } from 'react'
import type { SearchField } from '../api/slangWords'

interface Props {
  field: SearchField
  onQueryChange: (value: string) => void
  onFieldChange: (value: SearchField) => void
}

const DEBOUNCE_MS = 300

export function SearchBar({ field, onQueryChange, onFieldChange }: Props) {
  const [draft, setDraft] = useState('')

  // Debounced so typing does not fire one request per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => onQueryChange(draft), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [draft, onQueryChange])

  return (
    <div className="space-y-3">
      <input
        type="search"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        placeholder="Search slang words…"
        aria-label="Search slang words"
        className="w-full rounded-lg border border-slate-300 px-4 py-3 text-base shadow-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
      />

      <fieldset className="flex items-center gap-4">
        <legend className="sr-only">Search field</legend>
        {(['word', 'definition'] as const).map((option) => (
          <label key={option} className="flex items-center gap-2 text-sm text-slate-600">
            <input
              type="radio"
              name="search-field"
              value={option}
              checked={field === option}
              onChange={() => onFieldChange(option)}
              className="accent-sky-600"
            />
            by {option}
          </label>
        ))}
      </fieldset>
    </div>
  )
}
