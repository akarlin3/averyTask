import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useAuthStore } from '@/stores/authStore';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { PrismLogo } from '@/components/shared/PrismLogo';

const loginSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Please enter a valid email'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

type LoginForm = z.infer<typeof loginSchema>;

export function LoginScreen() {
  const [serverError, setServerError] = useState('');
  const [loading, setLoading] = useState(false);
  const login = useAuthStore((s) => s.login);
  const navigate = useNavigate();
  const emailRef = useRef<HTMLInputElement | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({
    defaultValues: { email: '', password: '' },
  });

  // Auto-focus email on mount
  useEffect(() => {
    emailRef.current?.focus();
  }, []);

  const { ref: emailRegRef, ...emailRegProps } = register('email');

  const onSubmit = async (data: LoginForm) => {
    // Client-side validation via Zod
    const result = loginSchema.safeParse(data);
    if (!result.success) return;

    setServerError('');
    setLoading(true);
    try {
      await login(data.email, data.password);
      navigate('/');
    } catch (err) {
      setServerError(
        (err as { response?: { data?: { detail?: string } } })?.response?.data
          ?.detail || 'Invalid credentials. Please try again.',
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--color-bg-primary)] px-4">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="mb-8 flex flex-col items-center">
          <PrismLogo variant="full" size={48} className="mb-4" />
          <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
            Welcome Back
          </h1>
          <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
            Sign in to your account
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
          <Input
            label="Email"
            type="email"
            placeholder="you@example.com"
            error={errors.email?.message}
            ref={(el) => {
              emailRegRef(el);
              emailRef.current = el;
            }}
            {...emailRegProps}
          />
          <Input
            label="Password"
            type="password"
            placeholder="Enter your password"
            error={errors.password?.message}
            {...register('password')}
          />

          {serverError && (
            <div className="rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-500">
              {serverError}
            </div>
          )}

          <Button type="submit" loading={loading} className="mt-2 w-full">
            Log In
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-[var(--color-text-secondary)]">
          Don&apos;t have an account?{' '}
          <Link
            to="/register"
            className="font-medium text-[var(--color-accent)] hover:underline"
          >
            Register
          </Link>
        </p>
      </div>
    </div>
  );
}
