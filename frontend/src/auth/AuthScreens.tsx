import { type FormEvent, type ReactNode, useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import {
  AuthApiError,
  forgotPassword,
  getCurrentAccount,
  login,
  logout,
  registerAccount,
  resendVerification,
  resetPassword,
  verifyEmail,
  type CurrentAccount,
} from './client'
import { BRAND } from '../config/brand'

function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <main className="app-shell auth-shell">
      <Link to="/welcome" className="auth-brand">{BRAND.productName}</Link>
      <section className="auth-card">
        <p className="eyebrow">{BRAND.tagline}</p>
        <h1>{title}</h1>
        <p className="auth-card__subtitle">{subtitle}</p>
        {children}
      </section>
    </main>
  )
}

function ErrorMessage({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof AuthApiError ? error.message : 'The account request could not be completed.'
  return <p className="error-notice" role="alert">{message}</p>
}

export function LoginScreen() {
  const navigate = useNavigate()
  const [loginValue, setLoginValue] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>()
  const [pending, setPending] = useState(false)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setPending(true)
    setError(undefined)
    try {
      const account = await login(loginValue, password)
      navigate(account.emailVerified ? '/' : '/verification-status', { replace: true })
    } catch (requestError) {
      setError(requestError)
    } finally {
      setPending(false)
    }
  }
  return (
    <AuthLayout title="Sign in" subtitle="Return to your command table.">
      <form className="auth-form" onSubmit={submit}>
        <label>Username or email<input value={loginValue} autoComplete="username" required onChange={(event) => setLoginValue(event.target.value)} /></label>
        <label>Password<input type="password" value={password} autoComplete="current-password" required onChange={(event) => setPassword(event.target.value)} /></label>
        <button className="button button--primary button--wide" disabled={pending}>{pending ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <ErrorMessage error={error} />
      <nav className="auth-links"><Link to="/forgot-password">Forgot password?</Link><Link to="/register">Create account</Link></nav>
    </AuthLayout>
  )
}

export function RegistrationScreen() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', displayName: '', password: '' })
  const [error, setError] = useState<unknown>()
  const [pending, setPending] = useState(false)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setPending(true)
    setError(undefined)
    try {
      await registerAccount(form)
      navigate('/login?registered=1', { replace: true })
    } catch (requestError) {
      setError(requestError)
    } finally {
      setPending(false)
    }
  }
  const update = (field: keyof typeof form, value: string) => setForm((current) => ({ ...current, [field]: value }))
  return (
    <AuthLayout title="Create account" subtitle="Build a persistent record for casual and ranked play.">
      <form className="auth-form" onSubmit={submit}>
        <label>Username<input value={form.username} autoComplete="username" minLength={3} maxLength={32} required onChange={(event) => update('username', event.target.value)} /></label>
        <label>Display name<input value={form.displayName} autoComplete="name" minLength={2} maxLength={50} required onChange={(event) => update('displayName', event.target.value)} /></label>
        <label>Email<input type="email" value={form.email} autoComplete="email" required onChange={(event) => update('email', event.target.value)} /></label>
        <label>Password<input type="password" value={form.password} autoComplete="new-password" minLength={12} maxLength={72} required onChange={(event) => update('password', event.target.value)} /></label>
        <small>Use 12–72 characters with upper and lowercase letters, a number, and a symbol.</small>
        <button className="button button--primary button--wide" disabled={pending}>{pending ? 'Creating…' : 'Create account'}</button>
      </form>
      <ErrorMessage error={error} />
      <nav className="auth-links"><Link to="/login">Already have an account?</Link></nav>
    </AuthLayout>
  )
}

export function ForgotPasswordScreen() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState<unknown>()
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(undefined)
    try {
      setMessage((await forgotPassword(email)).message)
    } catch (requestError) {
      setError(requestError)
    }
  }
  return (
    <AuthLayout title="Reset access" subtitle="We will send reset instructions if the account exists.">
      <form className="auth-form" onSubmit={submit}>
        <label>Email<input type="email" value={email} autoComplete="email" required onChange={(event) => setEmail(event.target.value)} /></label>
        <button className="button button--primary button--wide">Send reset link</button>
      </form>
      {message && <p className="success-notice" role="status">{message}</p>}
      <ErrorMessage error={error} />
      <nav className="auth-links"><Link to="/login">Back to sign in</Link></nav>
    </AuthLayout>
  )
}

export function ResetPasswordScreen() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>()
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(undefined)
    try {
      await resetPassword(token, password)
      navigate('/login?reset=1', { replace: true })
    } catch (requestError) {
      setError(requestError)
    }
  }
  return (
    <AuthLayout title="Choose a new password" subtitle="Completing this reset signs out every existing session.">
      <form className="auth-form" onSubmit={submit}>
        <label>New password<input type="password" value={password} autoComplete="new-password" minLength={12} maxLength={72} required onChange={(event) => setPassword(event.target.value)} /></label>
        <button className="button button--primary button--wide" disabled={!token}>Reset password</button>
      </form>
      {!token && <p className="error-notice" role="alert">This reset link is incomplete.</p>}
      <ErrorMessage error={error} />
    </AuthLayout>
  )
}

export function VerifyEmailScreen() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [account, setAccount] = useState<CurrentAccount>()
  const [error, setError] = useState<unknown>()
  useEffect(() => {
    if (token) void verifyEmail(token).then(setAccount).catch(setError)
  }, [token])
  return (
    <AuthLayout title="Verify email" subtitle="Verification unlocks ranked matchmaking.">
      {account?.emailVerified && <p className="success-notice" role="status">Email verified. Your account is active.</p>}
      {!account && !error && token && <p role="status">Verifying secure link…</p>}
      {!token && <p className="error-notice" role="alert">This verification link is incomplete.</p>}
      <ErrorMessage error={error} />
      <nav className="auth-links"><Link to="/login">Continue to sign in</Link></nav>
    </AuthLayout>
  )
}

export function VerificationStatusScreen() {
  const navigate = useNavigate()
  const [account, setAccount] = useState<CurrentAccount>()
  const [message, setMessage] = useState('')
  const [error, setError] = useState<unknown>()
  useEffect(() => {
    void getCurrentAccount().then(setAccount).catch(setError)
  }, [])
  const signOut = async () => {
    await logout()
    navigate('/login', { replace: true })
  }
  return (
    <AuthLayout title="Verify your email" subtitle="Check Mailpit during local development, then follow the secure link.">
      {account && <p>Signed in as <strong>{account.displayName}</strong> ({account.username}).</p>}
      <button className="button button--secondary button--wide" onClick={() => void resendVerification().then(() => setMessage('A new verification message was sent.')).catch(setError)}>Resend verification</button>
      <button className="button button--ghost button--wide" onClick={() => void signOut()}>Sign out</button>
      {message && <p className="success-notice" role="status">{message}</p>}
      <ErrorMessage error={error} />
    </AuthLayout>
  )
}
