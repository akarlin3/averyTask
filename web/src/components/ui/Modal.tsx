import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { useIsMobile } from '@/hooks/useMediaQuery';

type ModalSize = 'sm' | 'md' | 'lg' | 'full';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
  footer?: ReactNode;
  size?: ModalSize;
  persistent?: boolean;
  className?: string;
}

const sizeStyles: Record<ModalSize, string> = {
  sm: 'max-w-[400px]',
  md: 'max-w-[560px]',
  lg: 'max-w-[720px]',
  full: 'max-w-full h-full',
};

export function Modal({
  isOpen,
  onClose,
  title,
  children,
  footer,
  size = 'md',
  persistent = false,
  className = '',
}: ModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const isMobile = useIsMobile();

  // Lock body scroll
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
      previousFocusRef.current = document.activeElement as HTMLElement;
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  // Escape key
  useEffect(() => {
    if (!isOpen || persistent) return;
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [isOpen, onClose, persistent]);

  // Focus trap
  useEffect(() => {
    if (!isOpen || !modalRef.current) return;

    const modal = modalRef.current;
    const focusableElements = modal.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    );
    const firstFocusable = focusableElements[0];
    const lastFocusable = focusableElements[focusableElements.length - 1];

    firstFocusable?.focus();

    const handleTab = (e: KeyboardEvent) => {
      if (e.key !== 'Tab') return;
      if (e.shiftKey) {
        if (document.activeElement === firstFocusable) {
          e.preventDefault();
          lastFocusable?.focus();
        }
      } else {
        if (document.activeElement === lastFocusable) {
          e.preventDefault();
          firstFocusable?.focus();
        }
      }
    };

    modal.addEventListener('keydown', handleTab);
    return () => {
      modal.removeEventListener('keydown', handleTab);
      previousFocusRef.current?.focus();
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const effectiveSize = isMobile ? 'full' : size;

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/50 transition-opacity duration-150"
        onClick={persistent ? undefined : onClose}
      />

      {/* Modal content */}
      <div
        ref={modalRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`relative z-10 mx-0 sm:mx-4 w-full flex flex-col rounded-t-xl sm:rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] shadow-2xl transition-all duration-150 ${
          isMobile ? 'max-h-[90vh] animate-slide-up' : 'max-h-[85vh]'
        } ${sizeStyles[effectiveSize]} ${className}`}
      >
        {/* Header */}
        {title && (
          <div className="flex shrink-0 items-center justify-between border-b border-[var(--color-border)] px-6 py-4">
            <h2 className="text-lg font-semibold text-[var(--color-text-primary)]">
              {title}
            </h2>
            {!persistent && (
              <button
                onClick={onClose}
                className="rounded-md p-1 text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
                aria-label="Close"
              >
                <X className="h-5 w-5" />
              </button>
            )}
          </div>
        )}

        {/* Scrollable body */}
        <div className="flex-1 overflow-y-auto px-6 py-4">{children}</div>

        {/* Sticky footer */}
        {footer && (
          <div className="shrink-0 border-t border-[var(--color-border)] px-6 py-4">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
