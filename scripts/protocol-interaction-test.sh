#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mvn -B -f "$repo_root/backend/pom.xml" clean \
  '-Dtest=ProtocolVerticalIntegrationTest#protocolInteractionsAreAppendOnlyPrivateAndReopenRequiresResponsibleSector+applicantInteractionSerializesWithProtocolCompletion+interactionHistoryUsesWriteOrderWhenAnOlderTransactionWaitsForProtocolLock+attachmentPolicyChangesWaitForProtocolSubjectReadLock' \
  test

"$repo_root/dev" e2e --grep 'conversa de protocolo'
