import { useState } from 'react';
import { Moon, Sun, Monitor, LogOut } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';
import { useThemeStore } from '@/stores/themeStore';
import { NLPInput } from '@/components/shared/NLPInput';
import { Avatar } from '@/components/ui/Avatar';
import type { ThemeMode } from '@/stores/themeStore';

export function Header() {
  const [showUserMenu, setShowUserMenu] = useState(false);
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const themeMode = useThemeStore((s) => s.mode);
  const setMode = useThemeStore((s) => s.setMode);

  const themeOptions: { mode: ThemeMode; icon: typeof Sun; label: string }[] = [
    { mode: 'light', icon: Sun, label: 'Light' },
    { mode: 'dark', icon: Moon, label: 'Dark' },
    { mode: 'system', icon: Monitor, label: 'System' },
  ];

  return (
    <header className="flex h-14 items-center gap-4 border-b border-[var(--color-border)] bg-[var(--color-bg-primary)] px-4">
      {/* NLP Quick Add Bar */}
      <NLPInput
        onTaskCreate={(data) => {
          // Task creation will be wired in a future phase
          console.log('Create task:', data);
        }}
      />

      {/* Theme Toggle */}
      <div className="flex items-center gap-1 rounded-lg border border-[var(--color-border)] p-1">
        {themeOptions.map(({ mode, icon: Icon, label }) => (
          <button
            key={mode}
            onClick={() => setMode(mode)}
            className={`rounded-md p-1.5 transition-colors ${
              themeMode === mode
                ? 'bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
                : 'text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
            }`}
            aria-label={label}
            title={label}
          >
            <Icon className="h-4 w-4" />
          </button>
        ))}
      </div>

      {/* User Menu */}
      <div className="relative">
        <button
          onClick={() => setShowUserMenu(!showUserMenu)}
          aria-label="User menu"
        >
          <Avatar name={user?.name} src={user?.avatar_url} size="md" />
        </button>

        {showUserMenu && (
          <>
            <div
              className="fixed inset-0 z-40"
              onClick={() => setShowUserMenu(false)}
            />
            <div className="absolute right-0 top-full z-50 mt-2 w-48 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] p-1 shadow-lg">
              <div className="border-b border-[var(--color-border)] px-3 py-2">
                <p className="text-sm font-medium text-[var(--color-text-primary)]">
                  {user?.name || 'User'}
                </p>
                <p className="text-xs text-[var(--color-text-secondary)]">
                  {user?.email || ''}
                </p>
              </div>
              <button
                onClick={() => {
                  setShowUserMenu(false);
                  logout();
                }}
                className="mt-1 flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10"
              >
                <LogOut className="h-4 w-4" />
                Sign Out
              </button>
            </div>
          </>
        )}
      </div>
    </header>
  );
}
