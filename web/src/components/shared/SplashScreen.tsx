import { PrismLogo } from './PrismLogo';

export function SplashScreen() {
  return (
    <div className="flex h-screen items-center justify-center bg-[var(--color-bg-primary)]">
      <div className="flex flex-col items-center gap-4 animate-pulse-slow">
        <PrismLogo variant="full" size={56} />
        <div className="h-1 w-16 overflow-hidden rounded-full bg-[var(--color-bg-secondary)]">
          <div className="h-full w-full animate-loading-bar rounded-full bg-[var(--color-accent)]" />
        </div>
      </div>
    </div>
  );
}
