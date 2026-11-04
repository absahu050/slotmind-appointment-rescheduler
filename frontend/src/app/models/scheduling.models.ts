export interface Provider {
  id: string;
  name: string;
  specialty: string;
  status: string;
}

export interface Slot {
  id: string;
  providerId: string;
  providerName: string;
  specialty: string;
  startTime: string;
  durationMinutes: number;
  status: string;
}

export interface Suggestion {
  id: string;
  appointmentId: string;
  slotId: string;
  providerId: string;
  providerName: string;
  startTime: string;
  confidence: number;
  reasoning: string;
  status: string;
  triggerReason: string;
  createdAt: string;
}

export interface Appointment {
  id: string;
  patientName: string;
  type: string;
  status: string;
  provider: Provider;
  slot: Slot;
  suggestion: Suggestion | null;
}

export interface DashboardSnapshot {
  providers: Provider[];
  slots: Slot[];
  appointments: Appointment[];
}

export interface CreateAppointmentRequest {
  patientName: string;
  type: string;
  providerId: string;
  slotId: string;
}
