import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SchedulingApiService } from './scheduling-api.service';

describe('SchedulingApiService', () => {
  let api: SchedulingApiService;
  let http: HttpTestingController;
  const baseUrl = 'http://localhost:8080/api';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SchedulingApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    api = TestBed.inject(SchedulingApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the dashboard from the three read endpoints', () => {
    api.loadDashboard().subscribe(data => {
      expect(data.providers).toEqual([]);
      expect(data.slots).toEqual([]);
      expect(data.appointments).toEqual([]);
    });

    http.expectOne(`${baseUrl}/providers`).flush([]);
    http.expectOne(`${baseUrl}/slots`).flush([]);
    http.expectOne(`${baseUrl}/appointments`).flush([]);
  });

  it('sends mutations to their matching API endpoints', () => {
    api.updateProviderStatus('PRV-001', 'OFFLINE').subscribe();
    const providerRequest = http.expectOne(`${baseUrl}/providers/PRV-001/status`);
    expect(providerRequest.request.method).toBe('PATCH');
    expect(providerRequest.request.body).toEqual({ status: 'OFFLINE' });
    providerRequest.flush(null);

    api.cancelSlot('SLT-003').subscribe();
    const slotRequest = http.expectOne(`${baseUrl}/slots/SLT-003/status`);
    expect(slotRequest.request.method).toBe('PATCH');
    expect(slotRequest.request.body).toEqual({ status: 'CANCELLED' });
    slotRequest.flush(null);

    api.suggestAppointment('APT-001').subscribe();
    const suggestionRequest = http.expectOne(`${baseUrl}/appointments/APT-001/suggest`);
    expect(suggestionRequest.request.method).toBe('POST');
    suggestionRequest.flush({});

    api.resolveSuggestion('SUG-001', 'ACCEPTED').subscribe();
    const resolveRequest = http.expectOne(`${baseUrl}/suggestions/SUG-001`);
    expect(resolveRequest.request.method).toBe('PATCH');
    expect(resolveRequest.request.body).toEqual({ status: 'ACCEPTED' });
    resolveRequest.flush({});

    const booking = { patientName: 'Sam Lee', type: 'ROUTINE', providerId: 'PRV-002', slotId: 'SLT-004' };
    api.createAppointment(booking).subscribe();
    const bookingRequest = http.expectOne(`${baseUrl}/appointments`);
    expect(bookingRequest.request.method).toBe('POST');
    expect(bookingRequest.request.body).toEqual(booking);
    bookingRequest.flush({});
  });
});
