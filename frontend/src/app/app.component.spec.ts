import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  const providerRows = [
    { id: 'PRV-001', name: 'Dr. Sarah Chen', specialty: 'GENERAL', status: 'AVAILABLE' },
    { id: 'PRV-002', name: 'Dr. James Okonkwo', specialty: 'SPECIALIST', status: 'AVAILABLE' }
  ];
  const slotRows = [
    { id: 'SLT-001', providerId: 'PRV-001', providerName: 'Dr. Sarah Chen', specialty: 'GENERAL', startTime: '2026-11-05T10:00:00', durationMinutes: 30, status: 'OPEN' }
  ];
  const suggestion = {
    id: 'SUG-001', appointmentId: 'APT-001', slotId: 'SLT-001', providerId: 'PRV-001',
    providerName: 'Dr. Sarah Chen', startTime: '2026-11-05T10:00:00', confidence: 0.92,
    reasoning: 'We found the next available time.', status: 'PENDING', triggerReason: 'PROVIDER_OFFLINE', createdAt: '2026-11-04T09:00:00'
  };
  const appointments = [
    {
      id: 'APT-001', patientName: 'Aisha Patel', type: 'URGENT', status: 'RESCHEDULE_PENDING',
      provider: providerRows[0], slot: { ...slotRows[0], status: 'BOOKED' }, suggestion
    },
    {
      id: 'APT-002', patientName: 'Robert Kim', type: 'ROUTINE', status: 'CONFIRMED',
      provider: providerRows[1], slot: { ...slotRows[0], id: 'SLT-002', status: 'BOOKED' }, suggestion: null
    }
  ];

  let fixture: ReturnType<typeof TestBed.createComponent<AppComponent>>;
  let http: jasmine.SpyObj<HttpClient>;

  beforeEach(async () => {
    http = jasmine.createSpyObj<HttpClient>('HttpClient', ['get', 'post', 'patch']);
    http.get.and.callFake((url: string) => {
      if (url.endsWith('/providers')) return of(providerRows) as never;
      if (url.endsWith('/slots')) return of(slotRows) as never;
      return of(appointments) as never;
    });
    http.post.and.returnValue(of({}) as never);
    http.patch.and.returnValue(of({}) as never);

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [{ provide: HttpClient, useValue: http }]
    }).compileComponents();
  });

  it('loads the clinic data and renders review suggestions', fakeAsync(() => {
    fixture = TestBed.createComponent(AppComponent);
    tick();
    fixture.detectChanges();

    const app = fixture.componentInstance;
    expect(app.providers.length).toBe(2);
    expect(app.appointments.length).toBe(2);
    expect(app.reviewCount).toBe(1);
    expect(app.openSlotCount).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Aisha Patel');
    expect(fixture.nativeElement.textContent).toContain('We found the next available time.');
    expect(http.get).toHaveBeenCalledTimes(3);
    fixture.destroy();
  }));

  it('filters the schedule to appointments needing review', fakeAsync(() => {
    fixture = TestBed.createComponent(AppComponent);
    tick();
    fixture.componentInstance.filter = 'review';
    fixture.detectChanges();

    const names = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.patient-name')).map(node => node.textContent?.trim());
    expect(names).toEqual(['Aisha Patel']);
    fixture.destroy();
  }));

  it('opens booking with a provider that has an open slot', fakeAsync(() => {
    fixture = TestBed.createComponent(AppComponent);
    tick();
    const app = fixture.componentInstance;
    app.providers = [
      { id: 'PRV-005', name: 'Dr. Ananya Iyer', specialty: 'SPECIALIST', status: 'BUSY' },
      { id: 'PRV-002', name: 'Dr. James Okonkwo', specialty: 'SPECIALIST', status: 'AVAILABLE' }
    ];
    app.slots = [
      { ...slotRows[0], id: 'SLT-006', providerId: 'PRV-005', providerName: 'Dr. Ananya Iyer', status: 'BOOKED' },
      { ...slotRows[0], id: 'SLT-004', providerId: 'PRV-002', providerName: 'Dr. James Okonkwo', status: 'OPEN' }
    ];
    app.openBooking();
    fixture.detectChanges();

    expect(app.providersWithOpenSlots.map(provider => provider.id)).toEqual(['PRV-002']);
    expect(app.selectedProvider).toBe('PRV-002');
    const selects = (fixture.nativeElement as HTMLElement).querySelectorAll('.booking-modal select');
    expect(Array.from((selects[1] as HTMLSelectElement).options).map(option => option.value)).toEqual(['', 'PRV-002']);
    expect(Array.from((selects[2] as HTMLSelectElement).options).map(option => option.value)).toEqual(['', 'SLT-004']);
    fixture.destroy();
  }));

  it('explains when no providers have open slots', fakeAsync(() => {
    fixture = TestBed.createComponent(AppComponent);
    tick();
    const app = fixture.componentInstance;
    app.slots = slotRows.map(slot => ({ ...slot, status: 'BOOKED' }));
    app.openBooking();
    fixture.detectChanges();

    expect(app.providersWithOpenSlots).toEqual([]);
    expect(app.selectedProvider).toBe('');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No providers have open times');
    const bookingButton = (fixture.nativeElement as HTMLElement).querySelector('.booking-modal .accept-button') as HTMLButtonElement;
    expect(bookingButton.disabled).toBeTrue();
    fixture.destroy();
  }));

  it('submits a new appointment and refreshes the dashboard', fakeAsync(() => {
    fixture = TestBed.createComponent(AppComponent);
    tick();
    const app = fixture.componentInstance;
    app.patientName = 'Morgan Lee';
    app.appointmentType = 'FOLLOW_UP';
    app.selectedProvider = 'PRV-001';
    app.selectedSlot = 'SLT-001';
    app.book();

    expect(http.post).toHaveBeenCalledWith('http://localhost:8080/api/appointments', {
      patientName: 'Morgan Lee', type: 'FOLLOW_UP', providerId: 'PRV-001', slotId: 'SLT-001'
    });
    expect(app.modalOpen).toBeFalse();
    expect(app.notice).toBe('Appointment booked.');
    tick(3600);
    fixture.destroy();
  }));

  it('accepts a suggestion through the API and refreshes the dashboard', fakeAsync(() => {
    fixture = TestBed.createComponent(AppComponent);
    tick();
    fixture.componentInstance.resolve(suggestion, 'ACCEPTED');

    expect(http.patch).toHaveBeenCalledWith('http://localhost:8080/api/suggestions/SUG-001', { status: 'ACCEPTED' });
    expect(fixture.componentInstance.notice).toBe('New appointment time confirmed.');
    tick(3600);
    fixture.destroy();
  }));

  it('marks a provider offline and reports the automatic reschedule trigger', fakeAsync(() => {
    fixture = TestBed.createComponent(AppComponent);
    tick();
    fixture.componentInstance.setProviderStatus(providerRows[0]);

    expect(http.patch).toHaveBeenCalledWith('http://localhost:8080/api/providers/PRV-001/status', { status: 'OFFLINE' });
    expect(fixture.componentInstance.notice).toContain('marked offline');
    tick(3600);
    fixture.destroy();
  }));

  it('cancels a booked appointment slot through the API', fakeAsync(() => {
    fixture = TestBed.createComponent(AppComponent);
    tick();
    fixture.componentInstance.cancelSlot(appointments[1]);

    expect(http.patch).toHaveBeenCalledWith('http://localhost:8080/api/slots/SLT-002/status', { status: 'CANCELLED' });
    expect(fixture.componentInstance.notice).toContain('Cancelled slot');
    tick(3600);
    fixture.destroy();
  }));

  it('requests an on-demand suggestion for a confirmed appointment', fakeAsync(() => {
    fixture = TestBed.createComponent(AppComponent);
    tick();
    fixture.componentInstance.suggest(appointments[1]);

    expect(http.post).toHaveBeenCalledWith('http://localhost:8080/api/appointments/APT-002/suggest', {});
    expect(fixture.componentInstance.notice).toBe('A replacement suggestion is ready for review.');
    tick(3600);
    fixture.destroy();
  }));
});
