import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin } from 'rxjs';
import { environment } from '../../environments/environment';
import { Appointment, CreateAppointmentRequest, DashboardSnapshot, Provider, Slot, Suggestion } from '../models/scheduling.models';

@Injectable({ providedIn: 'root' })
export class SchedulingApiService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  loadDashboard(): Observable<DashboardSnapshot> {
    return forkJoin({
      providers: this.http.get<Provider[]>(`${this.api}/providers`),
      slots: this.http.get<Slot[]>(`${this.api}/slots`),
      appointments: this.http.get<Appointment[]>(`${this.api}/appointments`)
    });
  }

  updateProviderStatus(providerId: string, status: string): Observable<void> {
    return this.http.patch<void>(`${this.api}/providers/${providerId}/status`, { status });
  }

  cancelSlot(slotId: string): Observable<void> {
    return this.http.patch<void>(`${this.api}/slots/${slotId}/status`, { status: 'CANCELLED' });
  }

  suggestAppointment(appointmentId: string): Observable<Suggestion> {
    return this.http.post<Suggestion>(`${this.api}/appointments/${appointmentId}/suggest`, {});
  }

  resolveSuggestion(suggestionId: string, status: 'ACCEPTED' | 'REJECTED'): Observable<Suggestion> {
    return this.http.patch<Suggestion>(`${this.api}/suggestions/${suggestionId}`, { status });
  }

  createAppointment(request: CreateAppointmentRequest): Observable<Appointment> {
    return this.http.post<Appointment>(`${this.api}/appointments`, request);
  }
}
