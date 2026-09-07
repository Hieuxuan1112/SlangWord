import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { createWord, getWord, updateWord } from '../api/slangWords'
import { isConflict, problemMessage } from '../api/client'

const inputClass =
  'w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500'

interface FormProps {
  mode: 'create' | 'edit'
  initialWord: string
  initialDefinitions: string[]
}

/**
 * Pure form: its initial state comes from props and it is remounted via `key`
 * when the loaded word changes, so no effect has to copy fetched data into state.
 */
function WordForm({ mode, initialWord, initialDefinitions }: FormProps) {
  const isEdit = mode === 'edit'
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [word, setWord] = useState(initialWord)
  const [definitions, setDefinitions] = useState<string[]>(
    initialDefinitions.length > 0 ? initialDefinitions : [''],
  )
  const [duplicate, setDuplicate] = useState(false)

  const save = useMutation({
    mutationFn: (overwrite: boolean) => {
      const cleaned = definitions.map((d) => d.trim()).filter((d) => d.length > 0)
      return isEdit ? updateWord(initialWord, cleaned) : createWord(word.trim(), cleaned, overwrite)
    },
    onSuccess: async (saved) => {
      setDuplicate(false)
      await queryClient.invalidateQueries({ queryKey: ['slang-words'] })
      await queryClient.invalidateQueries({ queryKey: ['slang-word', saved.word] })
      navigate(`/words/${encodeURIComponent(saved.word)}`)
    },
    onError: (error) => setDuplicate(isConflict(error)),
  })

  const updateDefinition = (index: number, value: string) =>
    setDefinitions((current) => current.map((item, i) => (i === index ? value : item)))

  const hasContent = word.trim().length > 0 && definitions.some((d) => d.trim().length > 0)

  return (
    <form
      className="max-w-xl space-y-6"
      onSubmit={(event) => {
        event.preventDefault()
        save.mutate(false)
      }}
    >
      <h1 className="text-2xl font-semibold tracking-tight">
        {isEdit ? `Edit ${initialWord}` : 'Add a slang word'}
      </h1>

      <div className="space-y-1">
        <label htmlFor="word" className="text-sm font-medium text-slate-700">
          Slang word
        </label>
        <input
          id="word"
          value={word}
          onChange={(event) => setWord(event.target.value)}
          disabled={isEdit}
          className={`${inputClass} disabled:bg-slate-100`}
        />
      </div>

      <div className="space-y-2">
        <span className="text-sm font-medium text-slate-700">Definitions</span>
        {definitions.map((definition, index) => (
          <div key={index} className="flex items-center gap-2">
            <input
              value={definition}
              onChange={(event) => updateDefinition(index, event.target.value)}
              aria-label={`Definition ${index + 1}`}
              className={inputClass}
            />
            {definitions.length > 1 && (
              <button
                type="button"
                onClick={() => setDefinitions((current) => current.filter((_, i) => i !== index))}
                className="rounded-md border border-slate-300 px-2 py-1 text-sm hover:bg-slate-100"
                aria-label={`Remove definition ${index + 1}`}
              >
                &minus;
              </button>
            )}
          </div>
        ))}
        <button
          type="button"
          onClick={() => setDefinitions((current) => [...current, ''])}
          className="text-sm text-sky-600 underline"
        >
          Add another definition
        </button>
      </div>

      {duplicate ? (
        // The REST form of the original app's "already exists, overwrite?" dialog.
        <div className="rounded-md border border-amber-300 bg-amber-50 p-4">
          <p className="text-sm text-amber-900">
            <strong>{word}</strong> already exists. Overwrite its definitions?
          </p>
          <div className="mt-3 flex gap-2">
            <button
              type="button"
              onClick={() => save.mutate(true)}
              className="rounded-md bg-amber-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-amber-500"
            >
              Overwrite
            </button>
            <button
              type="button"
              onClick={() => setDuplicate(false)}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100"
            >
              Cancel
            </button>
          </div>
        </div>
      ) : (
        save.isError && <p className="text-sm text-red-600">{problemMessage(save.error)}</p>
      )}

      <button
        type="submit"
        disabled={!hasContent || save.isPending}
        className="rounded-md bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-500 disabled:opacity-50"
      >
        {save.isPending ? 'Saving…' : 'Save'}
      </button>
    </form>
  )
}

/** Loads the existing word when editing, then hands the form its initial values. */
export function WordFormPage() {
  const { word: routeWord } = useParams()
  const isEdit = Boolean(routeWord)

  const existing = useQuery({
    queryKey: ['slang-word', routeWord],
    queryFn: () => getWord(routeWord as string),
    enabled: isEdit,
  })

  if (!isEdit) {
    return <WordForm mode="create" initialWord="" initialDefinitions={[]} />
  }

  if (existing.isPending) {
    return <p className="text-slate-500">Loading…</p>
  }

  if (existing.isError) {
    return <p className="text-red-600">{problemMessage(existing.error, 'Word not found')}</p>
  }

  return (
    <WordForm
      key={existing.data.word}
      mode="edit"
      initialWord={existing.data.word}
      initialDefinitions={existing.data.definitions}
    />
  )
}
