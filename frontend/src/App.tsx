import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { HistoryPage } from './pages/HistoryPage'
import { LoginPage } from './pages/LoginPage'
import { QuizPage } from './pages/QuizPage'
import { RegisterPage } from './pages/RegisterPage'
import { SearchPage } from './pages/SearchPage'
import { WordDetailPage } from './pages/WordDetailPage'
import { WordFormPage } from './pages/WordFormPage'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<SearchPage />} />
              {/* Literal segment before the parameter, or /words/new is swallowed by :word. */}
              <Route
                path="/words/new"
                element={
                  <RequireAuth>
                    <WordFormPage />
                  </RequireAuth>
                }
              />
              <Route path="/words/:word" element={<WordDetailPage />} />
              <Route
                path="/words/:word/edit"
                element={
                  <RequireAuth>
                    <WordFormPage />
                  </RequireAuth>
                }
              />
              <Route path="/quiz" element={<QuizPage />} />
              <Route
                path="/history"
                element={
                  <RequireAuth>
                    <HistoryPage />
                  </RequireAuth>
                }
              />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
            </Route>
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
