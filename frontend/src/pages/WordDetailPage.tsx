import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteWord, getWord } from '../api/slangWords'
import { problemMessage } from '../api/client'
import { useAuth } from '../auth/useAuth'

export function WordDetailPage() {
  const { word = '' } = useParams()
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const query = useQuery({ queryKey: ['slang-word', word], queryFn: () => getWord(word) })

  const remove = useMutation({
    mutationFn: () => deleteWord(word),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['slang-words'] })
      navigate('/')
    },
  })

  if (query.isPending) {
    return <p className="text-slate-500">Loading…</p>
  }

  if (query.isError) {
    return (
      <div className="space-y-3">
        <p className="text-red-600">{problemMessage(query.error, 'Word not found')}</p>
        <Link to="/" className="text-sm text-sky-600 underline">
          Back to search
        </Link>
      </div>
    )
  }

  return (
    <article className="space-y-6">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">{query.data.word}</h1>
        <p className="mt-1 text-sm text-slate-400">
          Added {new Date(query.data.createdAt).toLocaleDateString()}
        </p>
      </header>

      <ul className="space-y-2">
        {query.data.definitions.map((definition, index) => (
          <li key={index} className="rounded-md border border-slate-200 bg-white px-4 py-3 text-slate-700">
            {definition}
          </li>
        ))}
      </ul>

      {isAuthenticated && (
        <div className="flex items-center gap-3">
          <Link
            to={`/words/${encodeURIComponent(query.data.word)}/edit`}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm hover:bg-slate-100"
          >
            Edit
          </Link>
          <button
            type="button"
            onClick={() => remove.mutate()}
            disabled={remove.isPending}
            className="rounded-md border border-red-300 px-4 py-2 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50"
          >
            {remove.isPending ? 'Deleting…' : 'Delete'}
          </button>
          {remove.isError && <span className="text-sm text-red-600">{problemMessage(remove.error)}</span>}
        </div>
      )}
    </article>
  )
}
