import { Component, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { Appointment, Provider, Slot, Suggestion } from './models/scheduling.models';
import { SchedulingApiService } from './services/scheduling-api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  private readonly api = inject(SchedulingApiService);
  private readonly destroyRef = inject(DestroyRef);

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
      switchMap(() => this.api.loadDashboard().pipe(catchError(() => {
        this.loading = false;
        this.error = 'Could not reach SlotMind. Make sure the backend is running.';
        return EMPTY;
      }))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: data => {
        this.providers = data.providers;
        this.slots = data.slots;
        this.appointments = data.appointments;
        this.loading = false;
        this.error = '';
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
  get providersWithOpenSlots(): Provider[] {
    const providerIds = new Set(this.slots.filter(slot => slot.status === 'OPEN').map(slot => slot.providerId));
    return this.providers.filter(provider => provider.status !== 'OFFLINE' && providerIds.has(provider.id));
  }
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
    this.api.resolveSuggestion(suggestion.id, status).subscribe({
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
    this.api.updateProviderStatus(provider.id, status).subscribe({
      next: () => {
        this.refreshMessage(status === 'OFFLINE' ? `${provider.name} marked offline. Finding replacement times…` : `${provider.name} is back online.`);
        this.reload();
        window.setTimeout(() => this.reload(), 700);
      },
      error: err => this.error = err.error?.message ?? 'Could not update provider.'
    });
  }

  cancelSlot(appointment: Appointment): void {
    this.api.cancelSlot(appointment.slot.id).subscribe({
      next: () => {
        this.refreshMessage('Cancelled slot. Replacement suggestions are on the way.');
        this.reload();
        window.setTimeout(() => this.reload(), 700);
      },
      error: err => this.error = err.error?.message ?? 'Could not cancel slot.'
    });
  }

  suggest(appointment: Appointment): void {
    this.api.suggestAppointment(appointment.id).subscribe({
      next: () => { this.refreshMessage('A replacement suggestion is ready for review.'); this.reload(); },
      error: err => this.error = err.error?.message ?? 'No suitable replacement time found.'
    });
  }

  openBooking(): void {
    this.patientName = '';
    this.selectedProvider = this.providersWithOpenSlots[0]?.id ?? '';
    this.selectedSlot = '';
    this.modalOpen = true;
  }

  book(): void {
    if (!this.patientName.trim() || !this.selectedProvider || !this.selectedSlot) return;
    this.busy = true;
    this.api.createAppointment({
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
    this.api.loadDashboard().subscribe({
      next: data => { this.providers = data.providers; this.slots = data.slots; this.appointments = data.appointments; this.error = ''; },
      error: () => this.error = 'Could not refresh the schedule.'
    });
  }
}
