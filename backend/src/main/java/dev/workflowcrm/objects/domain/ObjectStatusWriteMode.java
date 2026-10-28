package dev.workflowcrm.objects.domain;

import java.util.UUID;

record ObjectStatusWriteMode(boolean statusChanged, boolean refusalEntry, UUID requestId) {}
