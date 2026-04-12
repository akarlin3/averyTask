import { useState, useRef, useEffect, type ReactNode } from 'react';

export interface DropdownItem {
  key: string;
  label: string;
  icon?: ReactNode;
  onClick: () => void;
  danger?: boolean;
  disabled?: boolean;
}

export interface DropdownSection {
  header?: string;
  items: DropdownItem[];
}

interface DropdownProps {
  trigger: ReactNode;
  sections: DropdownSection[];
  align?: 'left' | 'right';
  className?: string;
}

export function Dropdown({ trigger, sections, align = 'left', className = '' }: DropdownProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const allItems = sections.flatMap((s) => s.items);

  // Close on outside click
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [isOpen]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!isOpen) {
      if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
        e.preventDefault();
        setIsOpen(true);
        setHighlightIndex(0);
      }
      return;
    }

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setHighlightIndex((i) => {
          let next = i + 1;
          while (next < allItems.length && allItems[next].disabled) next++;
          return next < allItems.length ? next : i;
        });
        break;
      case 'ArrowUp':
        e.preventDefault();
        setHighlightIndex((i) => {
          let next = i - 1;
          while (next >= 0 && allItems[next].disabled) next--;
          return next >= 0 ? next : i;
        });
        break;
      case 'Enter':
        e.preventDefault();
        if (highlightIndex >= 0 && allItems[highlightIndex] && !allItems[highlightIndex].disabled) {
          allItems[highlightIndex].onClick();
          setIsOpen(false);
        }
        break;
      case 'Escape':
        e.preventDefault();
        setIsOpen(false);
        break;
    }
  };

  let flatIndex = -1;

  return (
    <div ref={containerRef} className={`relative inline-block ${className}`}>
      {/* Trigger */}
      <div
        onClick={() => setIsOpen(!isOpen)}
        onKeyDown={handleKeyDown}
        tabIndex={0}
        role="button"
        aria-expanded={isOpen}
        aria-haspopup="menu"
      >
        {trigger}
      </div>

      {/* Menu */}
      {isOpen && (
        <div
          ref={menuRef}
          role="menu"
          className={`absolute top-full z-50 mt-1 min-w-[180px] rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] p-1 shadow-lg ${
            align === 'right' ? 'right-0' : 'left-0'
          }`}
        >
          {sections.map((section, sIdx) => (
            <div key={sIdx}>
              {sIdx > 0 && (
                <div className="my-1 border-t border-[var(--color-border)]" />
              )}
              {section.header && (
                <div className="px-3 py-1.5 text-xs font-medium uppercase tracking-wider text-[var(--color-text-secondary)]">
                  {section.header}
                </div>
              )}
              {section.items.map((item) => {
                flatIndex++;
                const idx = flatIndex;
                return (
                  <button
                    key={item.key}
                    role="menuitem"
                    disabled={item.disabled}
                    onClick={() => {
                      if (!item.disabled) {
                        item.onClick();
                        setIsOpen(false);
                      }
                    }}
                    onMouseEnter={() => setHighlightIndex(idx)}
                    className={`flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors ${
                      item.disabled
                        ? 'cursor-not-allowed opacity-50'
                        : highlightIndex === idx
                          ? 'bg-[var(--color-bg-secondary)]'
                          : ''
                    } ${
                      item.danger
                        ? 'text-red-500'
                        : 'text-[var(--color-text-primary)]'
                    }`}
                  >
                    {item.icon}
                    <span>{item.label}</span>
                  </button>
                );
              })}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
