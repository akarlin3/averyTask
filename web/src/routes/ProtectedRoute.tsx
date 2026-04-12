import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { SplashScreen } from '@/components/shared/SplashScreen';
import type { ReactNode } from 'react';

interface ProtectedRouteProps {
  children: ReactNode;
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isLoading = useAuthStore((s) => s.isLoading);
  const [timedOut, setTimedOut] = useState(false);

  // If hydration takes > 3 seconds, redirect to login
  useEffect(() => {
    if (!isLoading) return;
    const timer = setTimeout(() => setTimedOut(true), 3000);
    return () => clearTimeout(timer);
  }, [isLoading]);

  if (isLoading && !timedOut) {
    return <SplashScreen />;
  }

  if (!isAuthenticated || timedOut) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
