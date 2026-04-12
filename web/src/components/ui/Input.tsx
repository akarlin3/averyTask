import { forwardRef, useState, type InputHTMLAttributes } from 'react';
import { Eye, EyeOff } from 'lucide-react';

type InputVariant = 'default' | 'filled';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  variant?: InputVariant;
}

const variantStyles: Record<InputVariant, string> = {
  default: 'bg-[var(--color-bg-card)]',
  filled: 'bg-[var(--color-bg-secondary)]',
};

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, helperText, variant = 'default', className = '', id, type, ...props }, ref) => {
    const [showPassword, setShowPassword] = useState(false);
    const inputId = id || label?.toLowerCase().replace(/\s+/g, '-');
    const isPassword = type === 'password';
    const inputType = isPassword && showPassword ? 'text' : type;

    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label
            htmlFor={inputId}
            className="text-sm font-medium text-[var(--color-text-primary)]"
          >
            {label}
          </label>
        )}
        <div className="relative">
          <input
            ref={ref}
            id={inputId}
            type={inputType}
            className={`w-full rounded-lg border border-[var(--color-border)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder-[var(--color-text-secondary)] outline-none transition-colors duration-150 focus:border-[var(--color-accent)] focus:ring-1 focus:ring-[var(--color-accent)] ${
              error ? 'border-red-500 focus:border-red-500 focus:ring-red-500' : ''
            } ${variantStyles[variant]} ${isPassword ? 'pr-10' : ''} ${className}`}
            {...props}
          />
          {isPassword && (
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] transition-colors"
              tabIndex={-1}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? (
                <EyeOff className="h-4 w-4" />
              ) : (
                <Eye className="h-4 w-4" />
              )}
            </button>
          )}
        </div>
        {error && <p className="text-xs text-red-500">{error}</p>}
        {helperText && !error && (
          <p className="text-xs text-[var(--color-text-secondary)]">{helperText}</p>
        )}
      </div>
    );
  },
);

Input.displayName = 'Input';
