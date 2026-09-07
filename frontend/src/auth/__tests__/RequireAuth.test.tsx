import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider } from '../AuthProvider'
import { RequireAuth } from '../RequireAuth'

function renderGuarded() {
  return render(
    <MemoryRouter initialEntries={['/secret']}>
      <AuthProvider>
        <Routes>
          <Route
            path="/secret"
            element={
              <RequireAuth>
                <p>classified</p>
              </RequireAuth>
            }
          />
          <Route path="/login" element={<p>please log in</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('RequireAuth', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('redirects an anonymous visitor to the login page', () => {
    renderGuarded()

    expect(screen.queryByText('classified')).not.toBeInTheDocument()
    expect(screen.getByText('please log in')).toBeInTheDocument()
  })

  it('renders the protected content for a signed-in visitor', () => {
    localStorage.setItem('slangword.token', 'a-token')
    localStorage.setItem('slangword.user', JSON.stringify({ username: 'hieu', role: 'USER' }))

    renderGuarded()

    expect(screen.getByText('classified')).toBeInTheDocument()
  })
})
