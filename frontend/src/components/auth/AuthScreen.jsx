import React, { useState } from 'react';
import { ArrowRight, Database, LogIn, ShieldCheck, UserPlus } from 'lucide-react';
import { api, setAuthToken } from '../../lib/api';
import { Button } from '../ui/Button';
import { Field, Input } from '../ui/Field';
import { Segmented } from '../ui/Segmented';
import { Alert } from '../ui/Feedback';

const MODES = [
  { value: 'login', label: 'Sign in', icon: LogIn },
  { value: 'register', label: 'Register', icon: UserPlus },
];

export function AuthScreen({ onAuthenticated }) {
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const isLogin = mode === 'login';

  const switchMode = (next) => {
    setMode(next);
    setError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const res = isLogin
        ? await api.login(email, password)
        : await api.register(email, password, displayName);
      setAuthToken(res.accessToken);
      onAuthenticated(res.user);
    } catch (err) {
      setError(err.message || 'Authentication failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="auth-mark">
            <Database size={22} />
          </span>
          <div>
            <div className="auth-title">Schema VC</div>
            <div className="auth-tagline">Branch, diff, review and merge database schemas</div>
          </div>
        </div>

        <Segmented items={MODES} value={mode} onChange={switchMode} />

        {error && <Alert tone="error" className="auth-error">{error}</Alert>}

        <form className="auth-form" onSubmit={handleSubmit}>
          {!isLogin && (
            <Field label="Display name">
              <Input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="Jay Raj Singh"
                autoComplete="name"
                required
              />
            </Field>
          )}

          <Field label="Email">
            <Input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              autoComplete="email"
              required
            />
          </Field>

          <Field label="Password" hint={isLogin ? undefined : 'At least 6 characters'}>
            <Input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete={isLogin ? 'current-password' : 'new-password'}
              minLength={isLogin ? undefined : 6}
              required
            />
          </Field>

          <Button type="submit" variant="primary" size="lg" block loading={busy}>
            {isLogin ? 'Sign in' : 'Create account'}
            {!busy && <ArrowRight size={15} />}
          </Button>
        </form>

        <div className="auth-foot">
          <ShieldCheck size={13} />
          Stateless JWT · BCrypt password hashing
        </div>
      </div>
    </div>
  );
}
