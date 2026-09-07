import { Link } from 'react-router-dom'
import type { SlangWord } from '../types'

export function WordCard({ word }: { word: SlangWord }) {
  return (
    <li className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition hover:border-sky-300">
      <Link to={`/words/${encodeURIComponent(word.word)}`} className="font-semibold text-sky-700 hover:underline">
        {word.word}
      </Link>
      <ul className="mt-1 space-y-0.5">
        {word.definitions.map((definition, index) => (
          <li key={index} className="text-sm text-slate-600">
            {definition}
          </li>
        ))}
      </ul>
    </li>
  )
}
