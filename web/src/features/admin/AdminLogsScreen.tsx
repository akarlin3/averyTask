import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bug, Activity, ShieldCheck } from 'lucide-react';
import { toast } from 'sonner';
import { useAuthStore } from '@/stores/authStore';
import { Tabs, type Tab } from '@/components/ui/Tabs';
import { DebugLogsPanel } from './DebugLogsPanel';
import { ActivityLogsPanel } from './ActivityLogsPanel';

const ADMIN_TABS: Tab[] = [
  { key: 'debug', label: 'Debug Logs', icon: <Bug className="h-4 w-4" /> },
  { key: 'activity', label: 'Activity Logs', icon: <Activity className="h-4 w-4" /> },
];

export function AdminLogsScreen() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const [activeTab, setActiveTab] = useState('debug');

  // Redirect non-admins
  useEffect(() => {
    if (user && !user.is_admin) {
      toast.error("You don't have admin access.");
      navigate('/', { replace: true });
    }
  }, [user, navigate]);

  return (
    <div className="mx-auto max-w-6xl space-y-4">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[var(--color-accent)]/10">
          <ShieldCheck className="h-5 w-5 text-[var(--color-accent)]" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-[var(--color-text-primary)]">
            Admin Logs
          </h1>
          <p className="text-sm text-[var(--color-text-secondary)]">
            View and manage application logs
          </p>
        </div>
      </div>

      {/* Tabs */}
      <Tabs
        tabs={ADMIN_TABS}
        activeTab={activeTab}
        onChange={setActiveTab}
      />

      {/* Tab content */}
      {activeTab === 'debug' && <DebugLogsPanel />}
      {activeTab === 'activity' && <ActivityLogsPanel />}
    </div>
  );
}
