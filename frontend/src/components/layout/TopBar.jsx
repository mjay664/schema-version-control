import React from 'react';
import { Database, FileCode2, GitBranch, GitPullRequest, LogOut } from 'lucide-react';
import { useWorkspace } from '../../state/WorkspaceProvider';
import { Button } from '../ui/Button';
import { Segmented } from '../ui/Segmented';
import { Avatar } from '../ui/Feedback';

export function TopBar({ onLogout }) {
  const { currentUser, currentRepo, currentBranch, view, navigateTo, openMrCount } = useWorkspace();

  return (
    <header className="topbar">
      <div className="brand">
        <span className="brand-mark">
          <Database size={15} />
        </span>
        <span className="brand-name">Schema VC</span>
      </div>

      {currentRepo && (
        <>
          <span className="divider-v" />
          <nav className="breadcrumb" aria-label="Location">
            <span className="breadcrumb-item">
              <Database size={12} />
              <strong>{currentRepo.name}</strong>
            </span>
            {currentBranch && (
              <>
                <span className="breadcrumb-sep">/</span>
                <span className="breadcrumb-item">
                  <GitBranch size={12} />
                  <strong>{currentBranch.name}</strong>
                </span>
              </>
            )}
          </nav>
        </>
      )}

      <span className="spacer" />

      {currentRepo && (
        <Segmented
          value={view}
          onChange={navigateTo}
          items={[
            { value: 'editor', label: 'Schema', icon: FileCode2 },
            { value: 'merge_requests', label: 'Merge requests', icon: GitPullRequest, count: openMrCount || undefined },
          ]}
        />
      )}

      <span className="spacer" />

      <div className="topbar-user">
        <Avatar name={currentUser?.displayName} />
        <span className="topbar-user-meta">
          <span className="topbar-user-name">{currentUser?.displayName}</span>
          <span className="topbar-user-mail">{currentUser?.email}</span>
        </span>
      </div>
      <Button variant="ghost" icon={LogOut} onClick={onLogout}>
        Sign out
      </Button>
    </header>
  );
}
