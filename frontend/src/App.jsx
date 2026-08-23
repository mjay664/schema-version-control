import React, { useEffect, useState } from 'react';
import { Database } from 'lucide-react';
import { api, getAuthToken, onUnauthorized, removeAuthToken } from './lib/api';
import { ToastProvider } from './state/ToastProvider';
import { WorkspaceProvider, useWorkspace } from './state/WorkspaceProvider';
import { AuthScreen } from './components/auth/AuthScreen';
import { TopBar } from './components/layout/TopBar';
import { Sidebar } from './components/layout/Sidebar';
import { ActivityDrawer } from './components/layout/ActivityDrawer';
import { SchemaWorkspace } from './components/editor/SchemaWorkspace';
import { MergeRequestsView } from './components/merge/MergeRequestsView';
import { EmptyState } from './components/ui/Feedback';

/** Chooses the active workspace view. Kept separate so it can read the context. */
function WorkspaceRoutes() {
  const { view, currentRepo, bootstrapping } = useWorkspace();

  if (!currentRepo) {
    return (
      <EmptyState icon={Database} title={bootstrapping ? 'Loading workspace…' : 'No repository selected'}>
        {bootstrapping
          ? 'Fetching your repositories.'
          : 'Create a repository in the sidebar to start versioning a schema.'}
      </EmptyState>
    );
  }

  return view === 'merge_requests' ? <MergeRequestsView /> : <SchemaWorkspace />;
}

function Shell({ currentUser, onLogout }) {
  return (
    <WorkspaceProvider currentUser={currentUser}>
      <div className="app">
        <TopBar onLogout={onLogout} />
        <div className="app-body">
          <Sidebar />
          <main className="workspace">
            <WorkspaceRoutes />
          </main>
        </div>
        <ActivityDrawer />
      </div>
    </WorkspaceProvider>
  );
}

export default function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const [checkingSession, setCheckingSession] = useState(true);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      if (!getAuthToken()) {
        if (!cancelled) setCheckingSession(false);
        return;
      }
      try {
        const me = await api.getMe();
        if (!cancelled) setCurrentUser(me);
      } catch (_) {
        removeAuthToken();
      } finally {
        if (!cancelled) setCheckingSession(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  // A rejected token anywhere in the app drops us back to the sign-in screen.
  useEffect(() => onUnauthorized(() => setCurrentUser(null)), []);

  const handleLogout = () => {
    removeAuthToken();
    setCurrentUser(null);
  };

  if (checkingSession) {
    return (
      <div className="boot">
        <span className="spinner" />
        Restoring session…
      </div>
    );
  }

  return (
    <ToastProvider>
      {currentUser ? (
        <Shell currentUser={currentUser} onLogout={handleLogout} />
      ) : (
        <AuthScreen onAuthenticated={setCurrentUser} />
      )}
    </ToastProvider>
  );
}
