import { AuthForm } from './AuthForm'
import { useAuth } from '../auth/useAuth'

export function RegisterPage() {
  const { register } = useAuth()

  return (
    <AuthForm
      title="Create an account"
      submitLabel="Sign up"
      onSubmit={register}
      footer={{ text: 'Already registered?', linkLabel: 'Log in', to: '/login' }}
    />
  )
}
