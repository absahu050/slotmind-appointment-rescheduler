import { Component, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';

type Provider = { id: string; name: string; specialty: string; status: string };
type Slot = { id: string; providerId: string; providerName: string; specialty: string; startTime: string; durationMinutes: number; status: string };
type Suggestion = { id: string; appointmentId: string; slotId: string; providerId: string; providerName: string; startTime: string; confidence: number; reasoning: string; status: string; triggerReason: string; createdAt: string };
type Appointment = { id: string; patientName: string; type: string; status: string; provider: Provider; slot: Slot; suggestion: Suggestion | null };

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly api = 'http://localhost:8080/api';

  providers: Provider[] = [];
  slots: Slot[] = [];
  appointments: Appointment[] = [];
  filter: 'all' | 'review' | 'confirmed' = 'all';
  loading = true;
  busy = false;
  error = '';
  notice = '';
  modalOpen = false;
  patientName = '';
  appointmentType = 'ROUTINE';
  selectedProvider = '';
  selectedSlot = '';

  constructor() {
    timer(0, 5000).pipe(
      switchMap(() => forkJoin({
        providers: this.http.get<Provider[]>(`${this.api}/providers`),
        slots: this.http.get<Slot[]>(`${this.api}/slots`),
        appointments: this.http.get<Appointment[]>(`${this.api}/appointments`)
      })),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: data => {
        this.providers = data.providers;
        this.slots = data.slots;
        this.appointments = data.appointments;
        this.loading = false;
        this.error = '';
      },
      error: () => {
        this.loading = false;
        this.error = 'Could not reach SlotMind. Make sure the backend is running on port 8080.';
      }
    });
  }

  get visibleAppointments(): Appointment[] {
    if (this.filter === 'review') return this.appointments.filter(a => a.status === 'RESCHEDULE_PENDING');
    if (this.filter === 'confirmed') return this.appointments.filter(a => a.status === 'CONFIRMED' || a.status === 'RESCHEDULED');
    return this.appointments;
  }

  get reviewCount(): number { return this.appointments.filter(a => a.status === 'RESCHEDULE_PENDING').length; }
  get confirmedCount(): number { return this.appointments.filter(a => a.status === 'CONFIRMED' || a.status === 'RESCHEDULED').length; }
  get openSlotCount(): number { return this.slots.filter(s => s.status === 'OPEN').length; }
  get selectedProviderSlots(): Slot[] { return this.slots.filter(s => s.providerId === this.selectedProvider && s.status === 'OPEN'); }
  get todayLabel(): string { return new Date().toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' }); }
  get greeting(): string {
    const hour = new Date().getHours();
    return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  }

  initials(name: string): string { return name.replace('Dr. ', '').split(' ').map(part => part[0]).slice(0, 2).join('').toUpperCase(); }
  time(value: string): string { return new Date(value).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }); }
  date(value: string): string { return new Date(value).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' }); }
  triggerLabel(reason: string): string {
    if (reason === 'PROVIDER_OFFLINE') return 'Provider offline';
    if (reason === 'SLOT_CANCELLED') return 'Slot cancelled';
    return 'Manual request';
  }

  scrollToTeam(): void { document.getElementById('team')?.scrollIntoView({ behavior: 'smooth' }); }

  refreshMessage(message: string): void {
    this.notice = message;
    window.setTimeout(() => this.notice = '', 3600);
  }

  resolve(suggestion: Suggestion, status: 'ACCEPTED' | 'REJECTED'): void {
    this.busy = true;
    this.http.patch<Suggestion>(`${this.api}/suggestions/${suggestion.id}`, { status }).subscribe({
      next: () => {
        this.busy = false;
        this.refreshMessage(status === 'ACCEPTED' ? 'New appointment time confirmed.' : 'Suggestion declined.');
        this.reload();
      },
      error: err => { this.busy = false; this.error = err.error?.message ?? 'Could not update suggestion.'; }
    });
  }

  setProviderStatus(provider: Provider): void {
    const status = provider.status === 'OFFLINE' ? 'AVAILABLE' : 'OFFLINE';
    this.http.patch(`${this.api}/providers/${provider.id}/status`, { status }).subscribe({
      next: () => {
        this.refreshMessage(status === 'OFFLINE' ? `${provider.name} marked offline. Finding replacement times…` : `${provider.name} is back online.`);
        this.reload();
        window.setTimeout(() => this.reload(), 700);
      },
      error: err => this.error = err.error?.message ?? 'Could not update provider.'
    });
  }

  cancelSlot(appointment: Appointment): void {
    this.http.patch(`${this.api}/slots/${appointment.slot.id}/status`, { status: 'CANCELLED' }).subscribe({
      next: () => {
        this.refreshMessage('Cancelled slot. Replacement suggestions are on the way.');
        this.reload();
        window.setTimeout(() => this.reload(), 700);
      },
      error: err => this.error = err.error?.message ?? 'Could not cancel slot.'
    });
  }

  suggest(appointment: Appointment): void {
    this.http.post<Suggestion>(`${this.api}/appointments/${appointment.id}/suggest`, {}).subscribe({
      next: () => { this.refreshMessage('A replacement suggestion is ready for review.'); this.reload(); },
      error: err => this.error = err.error?.message ?? 'No suitable replacement time found.'
    });
  }

  openBooking(): void {
    this.patientName = '';
    this.selectedProvider = this.providers.find(p => p.status !== 'OFFLINE')?.id ?? '';
    this.selectedSlot = '';
    this.modalOpen = true;
  }

  book(): void {
    if (!this.patientName.trim() || !this.selectedProvider || !this.selectedSlot) return;
    this.busy = true;
    this.http.post<Appointment>(`${this.api}/appointments`, {
      patientName: this.patientName,
      type: this.appointmentType,
      providerId: this.selectedProvider,
      slotId: this.selectedSlot
    }).subscribe({
      next: () => {
        this.busy = false;
        this.modalOpen = false;
        this.refreshMessage('Appointment booked.');
        this.reload();
      },
      error: err => { this.busy = false; this.error = err.error?.message ?? 'Could not book appointment.'; }
    });
  }

  private reload(): void {
    forkJoin({
      providers: this.http.get<Provider[]>(`${this.api}/providers`),
      slots: this.http.get<Slot[]>(`${this.api}/slots`),
      appointments: this.http.get<Appointment[]>(`${this.api}/appointments`)
    }).subscribe({
      next: data => { this.providers = data.providers; this.slots = data.slots; this.appointments = data.appointments; this.error = ''; },
      error: () => this.error = 'Could not refresh the schedule.'
    });
  }
}
