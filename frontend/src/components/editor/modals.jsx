import React, { useState } from 'react';
import { GitBranch, GitCommit, GitPullRequest, Table2 } from 'lucide-react';
import { Modal, ModalForm } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Field, Input, Select, Textarea } from '../ui/Field';
import { Alert } from '../ui/Feedback';

/** Wraps a submit handler with busy + error state so each modal doesn't repeat it. */
function useSubmit(handler) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const onSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await handler();
    } catch (err) {
      setError(err.message || 'Something went wrong');
    } finally {
      setBusy(false);
    }
  };

  return { busy, error, onSubmit };
}

export function AddTableModal({ onClose, onCreate }) {
  const [name, setName] = useState('');
  const { busy, error, onSubmit } = useSubmit(async () => {
    const result = onCreate(name);
    if (!result?.ok) throw new Error(result?.error || 'Could not create table');
    onClose();
  });

  return (
    <Modal title="New table" icon={Table2} onClose={onClose}>
      <ModalForm
        onSubmit={onSubmit}
        footer={
          <>
            <Button type="button" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={busy}>
              Create table
            </Button>
          </>
        }
      >
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Table name" hint="Seeded with an id primary key and a created_at timestamp.">
          <Input
            mono
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="orders"
            autoFocus
            required
          />
        </Field>
      </ModalForm>
    </Modal>
  );
}

export function CommitModal({ branchName, changeSummary, onClose, onCommit }) {
  const [message, setMessage] = useState('');
  const { busy, error, onSubmit } = useSubmit(async () => {
    await onCommit(message.trim());
    onClose();
  });

  return (
    <Modal title="Commit schema version" icon={GitCommit} onClose={onClose}>
      <ModalForm
        onSubmit={onSubmit}
        footer={
          <>
            <Button type="button" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" icon={GitCommit} loading={busy}>
              Commit
            </Button>
          </>
        }
      >
        {error && <Alert tone="error">{error}</Alert>}
        <Alert tone="info">
          Committing {changeSummary} to <strong>{branchName}</strong>.
        </Alert>
        <Field label="Commit message">
          <Textarea
            rows={3}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="Widen users.email to VARCHAR(500)"
            autoFocus
            required
          />
        </Field>
      </ModalForm>
    </Modal>
  );
}

export function CreateBranchModal({ baseBranchName, onClose, onCreate }) {
  const [name, setName] = useState('');
  const { busy, error, onSubmit } = useSubmit(async () => {
    await onCreate(name.trim());
    onClose();
  });

  return (
    <Modal title="New branch" icon={GitBranch} onClose={onClose}>
      <ModalForm
        onSubmit={onSubmit}
        footer={
          <>
            <Button type="button" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={busy}>
              Create &amp; check out
            </Button>
          </>
        }
      >
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Base branch">
          <Input value={baseBranchName} mono disabled />
        </Field>
        <Field label="New branch name" hint="Letters, numbers and / _ . - only.">
          <Input
            mono
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="feature/payments"
            pattern="[a-zA-Z0-9/_.\-]+"
            autoFocus
            required
          />
        </Field>
      </ModalForm>
    </Modal>
  );
}

export function SubmitMergeRequestModal({
  sourceBranch,
  branches,
  users,
  currentUser,
  defaultTarget,
  onClose,
  onSubmit: submitMr,
}) {
  const candidates = branches.filter((b) => b.name !== sourceBranch.name);
  const [targetName, setTargetName] = useState(
    candidates.some((b) => b.name === defaultTarget) ? defaultTarget : candidates[0]?.name || ''
  );
  const [approverId, setApproverId] = useState('');

  const { busy, error, onSubmit } = useSubmit(async () => {
    const target = branches.find((b) => b.name === targetName);
    if (!target) throw new Error('Pick a target branch');
    await submitMr(sourceBranch.id, target.id, approverId);
    onClose();
  });

  const peers = users.filter((u) => u.id !== currentUser.id);

  return (
    <Modal title="Open merge request" icon={GitPullRequest} onClose={onClose}>
      <ModalForm
        onSubmit={onSubmit}
        footer={
          <>
            <Button type="button" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={busy} disabled={!targetName}>
              Open merge request
            </Button>
          </>
        }
      >
        {error && <Alert tone="error">{error}</Alert>}

        <Field label="Source branch">
          <Input value={sourceBranch.name} mono disabled />
        </Field>

        <Field label="Target branch">
          <Select value={targetName} onChange={(e) => setTargetName(e.target.value)} required>
            {candidates.length === 0 && <option value="">No other branch available</option>}
            {candidates.map((b) => (
              <option key={b.id} value={b.name}>
                {b.name}
              </option>
            ))}
          </Select>
        </Field>

        <Field
          label="Requested reviewer"
          hint="Optional. Any peer other than you can approve, whoever is assigned."
        >
          <Select value={approverId} onChange={(e) => setApproverId(e.target.value)}>
            <option value="">Any peer reviewer</option>
            {peers.map((u) => (
              <option key={u.id} value={u.id}>
                {u.displayName} · {u.email}
              </option>
            ))}
          </Select>
        </Field>

        <Alert tone="info">You cannot approve your own merge request.</Alert>
      </ModalForm>
    </Modal>
  );
}
