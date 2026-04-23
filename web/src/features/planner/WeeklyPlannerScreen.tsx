import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  CalendarDays,
  Clock,
  Lightbulb,
  ListX,
  Loader2,
  Settings2,
} from 'lucide-react';
import { format, parseISO } from 'date-fns';
import { toast } from 'sonner';
import { aiApi } from '@/api/ai';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { ProUpgradeModal } from '@/components/shared/ProUpgradeModal';
import { useProFeature } from '@/hooks/useProFeature';
import type {
  WeekdayCode,
  WeeklyPlanResponse,
  WeeklyPlanPreferences,
} from '@/types/briefingPlanner';

const DEFAULT_PREFS: WeeklyPlanPreferences = {
  work_days: ['MO', 'TU', 'WE', 'TH', 'FR'],
  focus_hours_per_day: 6,
  prefer_front_loading: true,
};

const WEEKDAY_OPTIONS: { code: WeekdayCode; label: string }[] = [
  { code: 'MO', label: 'Mon' },
  { code: 'TU', label: 'Tue' },
  { code: 'WE', label: 'Wed' },
  { code: 'TH', label: 'Thu' },
  { code: 'FR', label: 'Fri' },
  { code: 'SA', label: 'Sat' },
  { code: 'SU', label: 'Sun' },
];

export function WeeklyPlannerScreen() {
  const { isPro, showUpgrade, setShowUpgrade } = useProFeature();
  const [plan, setPlan] = useState<WeeklyPlanResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [prefs, setPrefs] = useState<WeeklyPlanPreferences>(DEFAULT_PREFS);
  const [settingsOpen, setSettingsOpen] = useState(false);

  const generate = async () => {
    if (!isPro) {
      setShowUpgrade(true);
      return;
    }
    setLoading(true);
    try {
      const response = await aiApi.weeklyPlan({ preferences: prefs });
      setPlan(response);
    } catch (e) {
      const msg = (e as { response?: { status?: number }; message?: string });
      if (msg?.response?.status === 429) {
        toast.error(
          'Weekly plan is rate-limited to once per 30 minutes. Try again shortly.',
        );
      } else {
        toast.error(msg?.message || 'Failed to generate plan');
      }
    } finally {
      setLoading(false);
    }
  };

  const toggleWorkDay = (code: WeekdayCode) => {
    setPrefs((p) => ({
      ...p,
      work_days: p.work_days.includes(code)
        ? p.work_days.filter((d) => d !== code)
        : [...p.work_days, code],
    }));
  };

  const dayEntries = plan ? Object.entries(plan.plan) : [];

  return (
    <div className="mx-auto max-w-5xl pb-16">
      <header className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-semibold text-[var(--color-text-primary)]">
            <CalendarDays
              className="h-5 w-5 text-[var(--color-accent)]"
              aria-hidden="true"
            />
            Weekly Planner
          </h1>
          <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
            AI-generated plan for the upcoming week based on your open tasks.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            onClick={() => setSettingsOpen((v) => !v)}
            aria-expanded={settingsOpen}
          >
            <Settings2 className="h-4 w-4" />
            Preferences
          </Button>
          <Button onClick={generate} disabled={loading}>
            {loading ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : null}
            {plan ? 'Regenerate plan' : 'Generate plan'}
          </Button>
        </div>
      </header>

      {settingsOpen && (
        <section className="mb-6 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
          <div className="mb-4">
            <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Work Days
            </label>
            <div className="flex flex-wrap gap-1.5">
              {WEEKDAY_OPTIONS.map(({ code, label }) => {
                const selected = prefs.work_days.includes(code);
                return (
                  <button
                    key={code}
                    onClick={() => toggleWorkDay(code)}
                    className={`rounded-md border px-2.5 py-1 text-xs font-medium transition-colors ${
                      selected
                        ? 'border-[var(--color-accent)] bg-[var(--color-accent)]/10 text-[var(--color-accent)]'
                        : 'border-[var(--color-border)] text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-secondary)]'
                    }`}
                    aria-pressed={selected}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
            <label className="text-sm">
              <span className="mb-1 block text-xs font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
                Focus hours per day
              </span>
              <input
                type="number"
                min={1}
                max={12}
                value={prefs.focus_hours_per_day}
                onChange={(e) =>
                  setPrefs((p) => ({
                    ...p,
                    focus_hours_per_day: Math.min(
                      12,
                      Math.max(1, Number(e.target.value) || 6),
                    ),
                  }))
                }
                className="w-24 rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-2 py-1 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
              />
            </label>

            <label className="flex items-center gap-2 text-sm text-[var(--color-text-primary)]">
              <input
                type="checkbox"
                checked={prefs.prefer_front_loading}
                onChange={(e) =>
                  setPrefs((p) => ({
                    ...p,
                    prefer_front_loading: e.target.checked,
                  }))
                }
                className="h-4 w-4 rounded border-[var(--color-border)] text-[var(--color-accent)]"
              />
              Front-load heavy work early in the week
            </label>
          </div>
        </section>
      )}

      {loading && !plan && (
        <div className="flex items-center gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-6">
          <Loader2 className="h-5 w-5 animate-spin text-[var(--color-accent)]" />
          <span className="text-sm text-[var(--color-text-primary)]">
            Laying out the week…
          </span>
        </div>
      )}

      {!loading && !plan && (
        <EmptyState
          title={isPro ? 'No plan yet' : 'Pro feature'}
          description={
            isPro
              ? 'Tap "Generate plan" to let the AI draft a week from your open tasks.'
              : 'Weekly AI planning is part of Pro. Upgrade to unlock it.'
          }
        />
      )}

      {plan && (
        <div className="flex flex-col gap-4">
          <section className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
            <h2 className="mb-1 text-sm font-semibold uppercase tracking-wide text-[var(--color-text-secondary)]">
              Week Summary
            </h2>
            <p className="text-sm text-[var(--color-text-primary)]">
              {plan.week_summary}
            </p>
          </section>

          <section className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
            {dayEntries.map(([dayName, day]) => (
              <article
                key={dayName}
                className="flex flex-col gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4"
              >
                <header className="flex items-baseline justify-between">
                  <h3 className="text-sm font-semibold text-[var(--color-text-primary)]">
                    {dayName}
                  </h3>
                  <span className="text-xs text-[var(--color-text-secondary)]">
                    {format(parseISO(day.date), 'MMM d')} ·{' '}
                    {day.total_hours.toFixed(1)}h
                  </span>
                </header>
                {day.tasks.length === 0 ? (
                  <p className="text-xs italic text-[var(--color-text-secondary)]">
                    No scheduled tasks.
                  </p>
                ) : (
                  <ul className="flex flex-col gap-1.5">
                    {day.tasks.map((t) => (
                      <li
                        key={t.task_id}
                        className="flex items-start gap-2 rounded-md bg-[var(--color-bg-secondary)] p-2"
                      >
                        <Clock
                          className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[var(--color-accent)]"
                          aria-hidden="true"
                        />
                        <div className="min-w-0">
                          <Link
                            to={`/tasks/${t.task_id}`}
                            className="text-xs font-medium text-[var(--color-text-primary)] hover:underline"
                          >
                            {t.title}
                          </Link>
                          <p className="text-[11px] text-[var(--color-text-secondary)]">
                            {t.suggested_time} · {t.duration_minutes}m · {t.reason}
                          </p>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
                {day.habits.length > 0 && (
                  <div className="flex flex-wrap gap-1 border-t border-[var(--color-border)] pt-2">
                    {day.habits.map((h, i) => (
                      <span
                        key={i}
                        className="rounded-full border border-[var(--color-border)] px-2 py-0.5 text-[11px] text-[var(--color-text-secondary)]"
                      >
                        {h}
                      </span>
                    ))}
                  </div>
                )}
              </article>
            ))}
          </section>

          {plan.unscheduled.length > 0 && (
            <section className="rounded-xl border border-amber-500/40 bg-amber-500/5 p-4">
              <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-amber-600 dark:text-amber-400">
                <ListX className="h-4 w-4" aria-hidden="true" />
                Unscheduled
              </h2>
              <ul className="flex flex-col gap-1.5">
                {plan.unscheduled.map((t) => (
                  <li
                    key={t.task_id}
                    className="rounded-md bg-[var(--color-bg-card)] p-2 text-sm"
                  >
                    <Link
                      to={`/tasks/${t.task_id}`}
                      className="font-medium text-[var(--color-text-primary)] hover:underline"
                    >
                      {t.title}
                    </Link>
                    <p className="text-xs text-[var(--color-text-secondary)]">
                      {t.reason}
                    </p>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {plan.tips.length > 0 && (
            <section className="rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-card)] p-4">
              <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-[var(--color-text-primary)]">
                <Lightbulb
                  className="h-4 w-4 text-[var(--color-accent)]"
                  aria-hidden="true"
                />
                Tips
              </h2>
              <ul className="flex flex-col gap-1.5 text-sm text-[var(--color-text-primary)]">
                {plan.tips.map((tip, i) => (
                  <li key={i}>{tip}</li>
                ))}
              </ul>
            </section>
          )}
        </div>
      )}

      <ProUpgradeModal
        isOpen={showUpgrade}
        onClose={() => setShowUpgrade(false)}
        featureName="Weekly Planner"
        featureDescription="AI-laid-out week plans that respect your work days, focus hours, and existing commitments."
      />
    </div>
  );
}
