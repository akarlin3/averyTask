import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useAuthStore } from '@/stores/authStore';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { PrismLogo } from '@/components/shared/PrismLogo';

const registerSchema = z
  .object({
    name: z.string().min(2, 'Name must be at least 2 characters'),
    email: z.string().min(1, 'Email is required').email('Please enter a valid email'),
    password: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string().min(1, 'Please confirm your password'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type RegisterForm = z.infer<typeof registerSchema>;

export function RegisterScreen() {
  const [serverError, setServerError] = useState('');
  const [loading, setLoading] = useState(false);
  const registerUser = useAuthStore((s) => s.register);
  const navigate = useNavigate();
  const nameRef = useRef<HTMLInputElement | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterForm>({
    defaultValues: { name: '', email: '', password: '', confirmPassword: '' },
  });

  // Auto-focus name on mount
  useEffect(() => {
    nameRef.current?.focus();
  }, []);

  const { ref: nameRegRef, ...nameRegProps } = register('name');

  const onSubmit = async (data: RegisterForm) => {
    const result = registerSchema.safeParse(data);
    if (!result.success) return;

    setServerError('');
    setLoading(true);
    try {
      await registerUser(data.email, data.password, data.name);
      navigate('/');
    } catch (err) {
      setServerError(
        (err as { response?: { data?: { detail?: string } } })?.response?.data
          ?.detail || 'Registration failed. Please try again.',
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
            Create Account
          </h1>
          <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
            Get started with PrismTask
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
          <Input
            label="Name"
            type="text"
            placeholder="Your name"
            error={errors.name?.message}
            ref={(el) => {
              nameRegRef(el);
              nameRef.current = el;
            }}
            {...nameRegProps}
          />
          <Input
            label="Email"
            type="email"
            placeholder="you@example.com"
            error={errors.email?.message}
            {...register('email')}
          />
          <Input
            label="Password"
            type="password"
            placeholder="Create a password (min. 8 characters)"
            error={errors.password?.message}
            {...register('password')}
          />
          <Input
            label="Confirm Password"
            type="password"
            placeholder="Confirm your password"
            error={errors.confirmPassword?.message}
            {...register('confirmPassword')}
          />

          {serverError && (
            <div className="rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-500">
              {serverError}
            </div>
          )}

          <Button type="submit" loading={loading} className="mt-2 w-full">
            Create Account
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-[var(--color-text-secondary)]">
          Already have an account?{' '}
          <Link
            to="/login"
            className="font-medium text-[var(--color-accent)] hover:underline"
          >
            Log In
          </Link>
        </p>
      </div>
    </div>
  );
}
