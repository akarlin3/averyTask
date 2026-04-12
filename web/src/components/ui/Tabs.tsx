import { useState, useRef, useEffect, useCallback } from 'react';

export interface Tab {
  key: string;
  label: string;
  icon?: React.ReactNode;
}

interface TabsProps {
  tabs: Tab[];
  activeTab?: string;
  defaultTab?: string;
  onChange?: (key: string) => void;
  className?: string;
}

export function Tabs({ tabs, activeTab, defaultTab, onChange, className = '' }: TabsProps) {
  const [internalActive, setInternalActive] = useState(defaultTab || tabs[0]?.key || '');
  const [indicatorStyle, setIndicatorStyle] = useState<{ left: number; width: number }>({ left: 0, width: 0 });
  const tabRefs = useRef<Map<string, HTMLButtonElement>>(new Map());
  const containerRef = useRef<HTMLDivElement>(null);

  const current = activeTab ?? internalActive;

  const updateIndicator = useCallback(() => {
    const el = tabRefs.current.get(current);
    if (el && containerRef.current) {
      const containerRect = containerRef.current.getBoundingClientRect();
      const tabRect = el.getBoundingClientRect();
      setIndicatorStyle({
        left: tabRect.left - containerRect.left,
        width: tabRect.width,
      });
    }
  }, [current]);

  useEffect(() => {
    updateIndicator();
  }, [updateIndicator]);

  useEffect(() => {
    window.addEventListener('resize', updateIndicator);
    return () => window.removeEventListener('resize', updateIndicator);
  }, [updateIndicator]);

  const handleSelect = (key: string) => {
    if (activeTab === undefined) {
      setInternalActive(key);
    }
    onChange?.(key);
  };

  return (
    <div ref={containerRef} className={`relative flex border-b border-[var(--color-border)] ${className}`} role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.key}
          ref={(el) => {
            if (el) tabRefs.current.set(tab.key, el);
          }}
          role="tab"
          aria-selected={current === tab.key}
          onClick={() => handleSelect(tab.key)}
          className={`relative flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium transition-colors duration-150 ${
            current === tab.key
              ? 'text-[var(--color-accent)]'
              : 'text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
          }`}
        >
          {tab.icon}
          {tab.label}
        </button>
      ))}

      {/* Sliding underline indicator */}
      <div
        className="absolute bottom-0 h-0.5 bg-[var(--color-accent)] transition-all duration-200 ease-out"
        style={{ left: indicatorStyle.left, width: indicatorStyle.width }}
      />
    </div>
  );
}
