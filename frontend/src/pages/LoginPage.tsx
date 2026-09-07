import { AuthForm } from './AuthForm'
import { useAuth } from '../auth/useAuth'

export function LoginPage() {
  const { login } = useAuth()

  return (
    <AuthForm
      title="Log in"
      submitLabel="Log in"
      onSubmit={login}
      footer={{ text: 'No account yet?', linkLabel: 'Sign up', to: '/register' }}
    />
  )
}
