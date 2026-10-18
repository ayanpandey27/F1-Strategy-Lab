import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { lapTime, signedSeconds } from './format';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('loads the season calendar on start', async () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/seasons/2024/races').flush([
      { id: null, season: 2024, round: 1, name: 'Bahrain Grand Prix', circuit: 'Bahrain', country: 'Bahrain', date: '2024-03-02', totalLaps: 0, imported: false },
    ]);
    await fixture.whenStable();
    const options = (fixture.nativeElement as HTMLElement).querySelectorAll('select option');
    expect(Array.from(options).map((o) => o.textContent?.trim())).toContain('R1 · Bahrain Grand Prix');
    http.verify();
  });
});

describe('format', () => {
  it('formats lap times and signed deltas', () => {
    expect(lapTime(97284)).toBe('1:37.284');
    expect(lapTime(22345)).toBe('22.345');
    expect(signedSeconds(2500)).toBe('+2.500s');
    expect(signedSeconds(-8877)).toBe('−8.877s');
  });
});
