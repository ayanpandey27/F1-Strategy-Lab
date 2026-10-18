import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ChatAnswer,
  Comparison,
  DriverInfo,
  DriverStrategy,
  LapChartData,
  OllamaStatus,
  PitImpactReport,
  RaceSummary,
  RaceView,
  WhatIfResult,
} from './models';

/** Thin client for the local Spring Boot API (proxied from /api to localhost:8080 in dev). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  schedule(season: number): Observable<RaceView[]> {
    return this.http.get<RaceView[]>(`/api/seasons/${season}/races`);
  }

  importRace(season: number, round: number): Observable<RaceView> {
    return this.http.post<RaceView>(`/api/seasons/${season}/races/${round}/import`, null);
  }

  drivers(raceId: number): Observable<DriverInfo[]> {
    return this.http.get<DriverInfo[]>(`/api/races/${raceId}/drivers`);
  }

  summary(raceId: number): Observable<RaceSummary> {
    return this.http.get<RaceSummary>(`/api/races/${raceId}/summary`);
  }

  laps(raceId: number): Observable<LapChartData> {
    return this.http.get<LapChartData>(`/api/races/${raceId}/laps`);
  }

  strategy(raceId: number, driverId: string): Observable<DriverStrategy> {
    return this.http.get<DriverStrategy>(`/api/races/${raceId}/drivers/${driverId}/strategy`);
  }

  pitImpact(raceId: number, driverId: string): Observable<PitImpactReport> {
    return this.http.get<PitImpactReport>(`/api/races/${raceId}/drivers/${driverId}/pit-impact`);
  }

  whatIf(raceId: number, driverId: string, stop: number, lapsEarlier: number): Observable<WhatIfResult> {
    const params = new HttpParams().set('stop', stop).set('lapsEarlier', lapsEarlier);
    return this.http.get<WhatIfResult>(`/api/races/${raceId}/drivers/${driverId}/what-if`, { params });
  }

  compare(raceId: number, a: string, b: string): Observable<Comparison> {
    const params = new HttpParams().set('a', a).set('b', b);
    return this.http.get<Comparison>(`/api/races/${raceId}/compare`, { params });
  }

  chat(raceId: number, question: string, driverIds: string[]): Observable<ChatAnswer> {
    return this.http.post<ChatAnswer>('/api/ai/chat', { raceId, question, driverIds });
  }

  aiStatus(): Observable<OllamaStatus> {
    return this.http.get<OllamaStatus>('/api/ai/status');
  }
}

/** Extracts the backend's ProblemDetail message, or a sensible fallback. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Backend not reachable. Is Spring Boot running on port 8080?';
    return err.error?.detail ?? err.message;
  }
  return String(err);
}
