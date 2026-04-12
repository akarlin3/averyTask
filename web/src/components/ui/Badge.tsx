import type { ReactNode } from 'react';

type BadgeVariant = 'solid' | 'outline' | 'dot';

interface BadgeProps {
  children?: ReactNode;
  variant?: BadgeVariant;
  color?: string;
  className?: string;
}

export function Badge({
  children,
  variant = 'solid',
  color = 'var(--color-accent)',
  className = '',
}: BadgeProps) {
  if (variant === 'dot') {
    return (
      <span
        className={`inline-block h-2.5 w-2.5 rounded-full ${className}`}
        style={{ backgroundColor: color }}
      />
    );
  }

  if (variant === 'outline') {
    return (
      <span
        className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${className}`}
        style={{ borderColor: color, color }}
      >
        {children}
      </span>
    );
  }

  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium text-white ${className}`}
      style={{ backgroundColor: color }}
    >
      {children}
    </span>
  );
}
