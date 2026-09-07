import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-1.5 text-sm font-medium ${
    isActive ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-200'
  }`

export function Layout() {
  const { isAuthenticated, username, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-4xl items-center gap-2 px-4 py-3">
          <Link to="/" className="mr-4 text-lg font-semibold tracking-tight">
            Slang<span className="text-sky-600">Word</span>
          </Link>
          <nav className="flex flex-1 items-center gap-1">
            <NavLink to="/" end className={linkClass}>
              Search
            </NavLink>
            <NavLink to="/quiz" className={linkClass}>
              Quiz
            </NavLink>
            {isAuthenticated && (
              <>
                <NavLink to="/history" className={linkClass}>
                  History
                </NavLink>
                <NavLink to="/words/new" className={linkClass}>
                  Add word
                </NavLink>
              </>
            )}
          </nav>

          {isAuthenticated ? (
            <div className="flex items-center gap-3">
              <span className="text-sm text-slate-500">{username}</span>
              <button
                type="button"
                onClick={() => {
                  logout()
                  navigate('/')
                }}
                className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100"
              >
                Log out
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link to="/login" className="rounded-md px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-200">
                Log in
              </Link>
              <Link
                to="/register"
                className="rounded-md bg-sky-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-sky-500"
              >
                Sign up
              </Link>
            </div>
          )}
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
