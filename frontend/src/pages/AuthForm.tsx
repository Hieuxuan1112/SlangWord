import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { problemMessage } from '../api/client'

interface Props {
  title: string
  submitLabel: string
  onSubmit: (username: string, password: string) => Promise<void>
  footer: { text: string; linkLabel: string; to: string }
}

const inputClass =
  'w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500'

/** Login and register differ only in copy and which auth call they make. */
export function AuthForm({ title, submitLabel, onSubmit, footer }: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  return (
    <form
      className="mx-auto max-w-sm space-y-5"
      onSubmit={async (event) => {
        event.preventDefault()
        setPending(true)
        setError(null)
        try {
          await onSubmit(username, password)
          navigate(from, { replace: true })
        } catch (caught) {
          setError(problemMessage(caught))
        } finally {
          setPending(false)
        }
      }}
    >
      <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>

      <div className="space-y-1">
        <label htmlFor="username" className="text-sm font-medium text-slate-700">
          Username
        </label>
        <input
          id="username"
          value={username}
          autoComplete="username"
          onChange={(event) => setUsername(event.target.value)}
          className={inputClass}
        />
      </div>

      <div className="space-y-1">
        <label htmlFor="password" className="text-sm font-medium text-slate-700">
          Password
        </label>
        <input
          id="password"
          type="password"
          value={password}
          autoComplete="current-password"
          onChange={(event) => setPassword(event.target.value)}
          className={inputClass}
        />
      </div>

      {error && (
        <p className="text-sm text-red-600" role="alert">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={pending || username.length === 0 || password.length === 0}
        className="w-full rounded-md bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-500 disabled:opacity-50"
      >
        {pending ? 'Please wait…' : submitLabel}
      </button>

      <p className="text-center text-sm text-slate-500">
        {footer.text}{' '}
        <Link to={footer.to} className="text-sky-600 underline">
          {footer.linkLabel}
        </Link>
      </p>
    </form>
  )
}
