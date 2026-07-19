import { Link } from 'react-router-dom'
import { BRAND } from '../../config/brand'

export function LandingScreen() {
  return (
    <main className="app-shell landing-page">
      <nav className="landing-navigation" aria-label="Welcome navigation">
        <Link className="landing-brand" to="/welcome">{BRAND.productName}</Link>
        <div>
          <Link to="/login">Sign in</Link>
          <Link className="button button--primary" to="/register">Create account</Link>
        </div>
      </nav>

      <section className="landing-hero">
        <div>
          <p className="eyebrow">{BRAND.tagline}</p>
          <h1>Outthink the army you cannot see.</h1>
          <p>
            Play the classic Filipino hidden-information strategy game in private rooms or
            climb the ranked ladder. Every move, clock and battle is resolved by the server.
          </p>
          <div className="landing-actions">
            <Link className="button button--primary" to="/register">Create your command</Link>
            <Link className="button button--secondary" to="/login">Return to battle</Link>
          </div>
        </div>
        <aside className="landing-briefing" aria-label="Platform features">
          <p className="eyebrow">Command briefing</p>
          <ul>
            <li><strong>Private matches</strong><span>Invite an opponent with a room code.</span></li>
            <li><strong>Ranked command</strong><span>Meet similarly rated, verified players.</span></li>
            <li><strong>Live and persistent</strong><span>Reconnect safely without losing the match.</span></li>
            <li><strong>Information stays hidden</strong><span>Opponent ranks appear only when the rules reveal them.</span></li>
          </ul>
        </aside>
      </section>
    </main>
  )
}
