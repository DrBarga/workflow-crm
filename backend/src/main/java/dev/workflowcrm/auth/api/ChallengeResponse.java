package dev.workflowcrm.auth.api;

record ChallengeResponse(String challengeId, String status, int expiresInSeconds) {}
