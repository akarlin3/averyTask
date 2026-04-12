import { useNavigate, useLocation } from 'react-router-dom';
import { CalendarDays, CalendarRange, Clock } from 'lucide-react';

const CALENDAR_TABS = [
  { key: 'week', path: '/calendar/week', label: 'Week', icon: CalendarRange },
  { key: 'month', path: '/calendar/month', label: 'Month', icon: CalendarDays },
  { key: 'timeline', path: '/calendar/timeline', label: 'Timeline', icon: Clock },
] as const;

export function CalendarNav() {
  const navigate = useNavigate();
  const location = useLocation();

  const handleTabChange = (path: string) => {
    navigate(path);
    localStorage.setItem('prismtask_calendar_view', path);
  };

  return (
    <div className="flex border-b border-[var(--color-border)] mb-4">
      {CALENDAR_TABS.map(({ key, path, label, icon: Icon }) => {
        const isActive = location.pathname === path;
        return (
          <button
            key={key}
            onClick={() => handleTabChange(path)}
            className={`relative flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium transition-colors duration-150 ${
              isActive
                ? 'text-[var(--color-accent)]'
                : 'text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
            }`}
          >
            <Icon className="h-4 w-4" />
            {label}
            {isActive && (
              <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-[var(--color-accent)]" />
            )}
          </button>
        );
      })}
    </div>
  );
}
