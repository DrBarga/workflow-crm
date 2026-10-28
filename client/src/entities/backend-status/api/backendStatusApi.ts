import { apiFetch } from "@shared/api";

interface ActuatorHealthResponse {
  status: string;
}

export async function fetchBackendHealth(): Promise<ActuatorHealthResponse> {
  return apiFetch<ActuatorHealthResponse>("/actuator/health");
}
