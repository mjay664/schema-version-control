import React from 'react';
import { useWorkspace } from '../../state/WorkspaceProvider';
import { MergeRequestList } from './MergeRequestList';
import { MergeRequestReview } from './MergeRequestReview';

export function MergeRequestsView() {
  const { selectedMrId } = useWorkspace();
  return selectedMrId ? <MergeRequestReview mergeRequestId={selectedMrId} /> : <MergeRequestList />;
}
