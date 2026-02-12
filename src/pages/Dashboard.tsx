import { useTheme } from '../hooks/useTheme';

interface UserProfile {
  nickname: string;
  weightKg: string;
  ftpWatts: string;
  ergBiasWatts: string;
  thresholdHr: string;
  maxHr: string;
}

interface DashboardProps {
  profile: UserProfile;
  onNavigate: (view: 'workout' | 'builder' | 'library' | 'settings' | 'history') => void;
  onOpenProfile: () => void;
}

interface DeviceStatusProps {
  label: string;
  connected: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
}

function DeviceStatusRow({ label, connected, onConnect, onDisconnect }: DeviceStatusProps) {
  return (
    <div className="device-status-row">
      <div className={`device-status-indicator ${connected ? 'connected' : ''}`} />
      <span className="device-status-label">{label}</span>
      {connected ? (
        <button className="device-action-btn disconnect" onClick={onDisconnect}>
          Disconnect
        </button>
      ) : (
        <button className="device-action-btn" onClick={onConnect}>
          Connect
        </button>
      )}
    </div>
  );
}

interface NavigationCardProps {
  icon: string;
  title: string;
  description: string;
  onClick: () => void;
  primary?: boolean;
}

function NavigationCard({ icon, title, description, onClick, primary }: NavigationCardProps) {
  return (
    <button
      className={`nav-card ${primary ? 'primary' : ''}`}
      onClick={onClick}
      type="button"
    >
      <span className="nav-card-icon">{icon}</span>
      <div className="nav-card-content">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </button>
  );
}

export function Dashboard({ profile, onNavigate, onOpenProfile }: DashboardProps) {
  const { theme, toggleTheme } = useTheme();
  const ftp = profile.ftpWatts ? `${profile.ftpWatts}W` : 'Not set';
  const weight = profile.weightKg ? `${profile.weightKg}kg` : '--';
  const nickname = profile.nickname || 'Athlete';

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <div className="user-info">
          <div className="user-avatar">
            <span>{nickname[0]?.toUpperCase() || 'A'}</span>
          </div>
          <div className="user-details">
            <h1>{nickname}</h1>
            <div className="user-stats">
              <span>FTP: {ftp}</span>
              <span>Weight: {weight}</span>
            </div>
          </div>
        </div>
        <div className="header-actions">
          <button
            className="theme-toggle"
            type="button"
            onClick={toggleTheme}
            title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            <span aria-hidden="true">{theme === 'dark' ? '☀️' : '🌙'}</span>
          </button>
          <button
            className="settings-button"
            type="button"
            onClick={onOpenProfile}
            aria-label="Open settings"
          >
            <span className="settings-icon" aria-hidden="true" />
          </button>
        </div>
      </header>

      <section className="dashboard-section devices-section">
        <h2>Devices</h2>
        <div className="devices-panel">
          <DeviceStatusRow
            label="Smart Trainer"
            connected={false}
            onConnect={() => {}}
            onDisconnect={() => {}}
          />
          <DeviceStatusRow
            label="Heart Rate Monitor"
            connected={false}
            onConnect={() => {}}
            onDisconnect={() => {}}
          />
        </div>
      </section>

      <section className="dashboard-section">
        <h2>Quick Access</h2>
        <div className="nav-grid">
          <NavigationCard
            icon="🚴"
            title="Workout Mode"
            description="Start training with your connected devices"
            onClick={() => onNavigate('workout')}
            primary
          />
          <NavigationCard
            icon="📝"
            title="Workout Builder"
            description="Create custom workouts with text commands"
            onClick={() => onNavigate('builder')}
          />
          <NavigationCard
            icon="📚"
            title="Workout Library"
            description="Browse and manage your saved workouts"
            onClick={() => onNavigate('library')}
          />
          <NavigationCard
            icon="📊"
            title="History"
            description="View past sessions and analytics"
            onClick={() => onNavigate('history')}
          />
          <NavigationCard
            icon="⚙️"
            title="Settings"
            description="Configure profile, zones, and integrations"
            onClick={() => onNavigate('settings')}
          />
        </div>
      </section>
    </div>
  );
}
