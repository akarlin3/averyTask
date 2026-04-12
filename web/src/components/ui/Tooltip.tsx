import { useState, useRef, type ReactNode } from 'react';

type TooltipPosition = 'top' | 'bottom' | 'left' | 'right';

interface TooltipProps {
  content: string;
  position?: TooltipPosition;
  delay?: number;
  children: ReactNode;
  className?: string;
}

const positionStyles: Record<TooltipPosition, string> = {
  top: 'bottom-full left-1/2 -translate-x-1/2 mb-2',
  bottom: 'top-full left-1/2 -translate-x-1/2 mt-2',
  left: 'right-full top-1/2 -translate-y-1/2 mr-2',
  right: 'left-full top-1/2 -translate-y-1/2 ml-2',
};

export function Tooltip({
  content,
  position = 'top',
  delay = 300,
  children,
  className = '',
}: TooltipProps) {
  const [visible, setVisible] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const show = () => {
    timeoutRef.current = setTimeout(() => setVisible(true), delay);
  };

  const hide = () => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    setVisible(false);
  };

  return (
    <div
      className={`relative inline-flex ${className}`}
      onMouseEnter={show}
      onMouseLeave={hide}
      onFocus={show}
      onBlur={hide}
    >
      {children}
      {visible && (
        <div
          role="tooltip"
          className={`pointer-events-none absolute z-50 whitespace-nowrap rounded-md bg-[var(--color-text-primary)] px-2.5 py-1.5 text-xs text-[var(--color-bg-primary)] shadow-lg ${positionStyles[position]}`}
        >
          {content}
        </div>
      )}
    </div>
  );
}
