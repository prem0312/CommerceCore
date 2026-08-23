import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { Layers, ShieldCheck, Mail, Lock, ArrowRight, UserCheck } from 'lucide-react';
import { Button } from '../components/common/Button';
import { Input } from '../components/common/Input';
import { Alert } from '../components/common/Alert';
import { useAuth } from '../hooks/useAuth';
import { extractApiErrorMessage } from '../api/client';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, isAuthenticated } = useAuth();

  const [email, setEmail] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  // Destination after login (e.g. from /checkout)
  const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/';

  // If already authenticated, redirect
  React.useEffect(() => {
    if (isAuthenticated) {
      navigate(from, { replace: true });
    }
  }, [isAuthenticated, navigate, from]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !password.trim()) {
      setError('Please provide both email address and password.');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      await login({
        email: email.trim(),
        password,
      });
      navigate(from, { replace: true });
    } catch (err) {
      setError(extractApiErrorMessage(err, 'Authentication failed. Please check your credentials.'));
    } finally {
      setIsLoading(false);
    }
  };

  const setDemoCredentials = (demoEmail: string, demoPass: string) => {
    setEmail(demoEmail);
    setPassword(demoPass);
    setError(null);
  };

  return (
    <div className="min-h-[calc(100vh-16rem)] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        <div className="text-center space-y-2">
          <div className="inline-flex w-12 h-12 rounded-2xl bg-slate-900 items-center justify-center text-white shadow-sm mb-2">
            <Layers className="w-6 h-6" />
          </div>
          <h2 className="text-3xl font-extrabold text-slate-900 tracking-tight">
            Sign In to CommerceCore
          </h2>
          <p className="text-xs text-slate-500">
            HMAC-signed JWT authentication through API Gateway (:8080)
          </p>
        </div>

        <div className="bg-white p-8 rounded-3xl border border-slate-200/80 shadow-xs space-y-6">
          {error && (
            <Alert variant="error" title="Sign In Error" onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          <form className="space-y-4" onSubmit={handleSubmit}>
            <Input
              label="Email Address"
              type="email"
              placeholder="name@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              leftIcon={<Mail className="w-4 h-4" />}
              autoComplete="email"
              required
            />
            <Input
              label="Password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              leftIcon={<Lock className="w-4 h-4" />}
              autoComplete="current-password"
              required
            />

            <Button
              type="submit"
              variant="primary"
              className="w-full"
              isLoading={isLoading}
              rightIcon={<ArrowRight className="w-4 h-4" />}
            >
              Sign In
            </Button>
          </form>

          <div className="pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-600">
            <span>Don't have an account?</span>
            <Link to="/register" className="font-semibold text-blue-600 hover:text-blue-500">
              Register here
            </Link>
          </div>
        </div>

        {/* Development Quick-Fill Profiles */}
        <div className="p-5 rounded-2xl bg-slate-50 border border-slate-200 text-xs text-slate-600 space-y-3">
          <div className="font-bold text-slate-900 flex items-center gap-1.5">
            <ShieldCheck className="w-4 h-4 text-blue-600" />
            Quick Demo Login Profiles
          </div>

          <div className="grid grid-cols-2 gap-2 pt-1">
            <button
              type="button"
              onClick={() => setDemoCredentials('admin@commercecore.io', 'AdminPass123!')}
              className="p-2.5 bg-white rounded-xl border border-slate-200 text-left hover:border-slate-300 hover:bg-slate-50 transition-all text-xs flex flex-col gap-0.5"
            >
              <div className="flex items-center gap-1 font-bold text-slate-900">
                <ShieldCheck className="w-3.5 h-3.5 text-blue-600" /> Admin
              </div>
              <span className="text-[10px] text-slate-400 font-mono">admin@commercecore.io</span>
            </button>

            <button
              type="button"
              onClick={() => setDemoCredentials('shopper@commercecore.io', 'Password123!')}
              className="p-2.5 bg-white rounded-xl border border-slate-200 text-left hover:border-slate-300 hover:bg-slate-50 transition-all text-xs flex flex-col gap-0.5"
            >
              <div className="flex items-center gap-1 font-bold text-slate-900">
                <UserCheck className="w-3.5 h-3.5 text-emerald-600" /> Customer
              </div>
              <span className="text-[10px] text-slate-400 font-mono">shopper@commercecore.io</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
