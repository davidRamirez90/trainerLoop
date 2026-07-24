interface SettingsProps {
  onBack: () => void;
}

export function Settings({ onBack }: SettingsProps) {
  return (
    <div className="page settings-page">
      <header className="page-header">
        <button className="back-button" onClick={onBack} type="button">
          ← Back
        </button>
        <h1>Settings</h1>
      </header>

      <div className="settings-grid">
        <section className="settings-section">
          <h2>Profile</h2>
          <p>Manage your athlete profile, FTP, weight, and training zones.</p>
          <button className="btn" type="button">
            Edit Profile
          </button>
        </section>

        <section className="settings-section">
          <h2>Devices</h2>
          <p>Configure Bluetooth device connections and preferences.</p>
          <button className="btn" type="button">
            Device Settings
          </button>
        </section>

        <section className="settings-section">
          <h2>Integrations</h2>
          <p>Connect with Strava, Intervals.icu, and other services.</p>
          <button className="btn" type="button">
            Manage Integrations
          </button>
        </section>

        <section className="settings-section">
          <h2>Preferences</h2>
          <p>App theme, units, and workout display preferences.</p>
          <button className="btn" type="button">
            Edit Preferences
          </button>
        </section>
      </div>
    </div>
  );
}
