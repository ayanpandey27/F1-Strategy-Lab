import { Component, effect, inject, input, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from './api.service';
import { lapTime, seconds, signedSeconds } from './format';
import { DriverStrategy, PitImpactReport } from './models';

/** Stints and pit-stop impact for one driver. */
@Component({
  selector: 'app-strategy-panel',
  template: `
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (strategy(); as s) {
      <div class="card-head">
        <span class="swatch" [style.background]="'var(--series-' + (slot() + 1) + ')'"></span>
        <h3>{{ s.driver.name }} <span class="muted">· {{ s.driver.team }}</span></h3>
        <span class="pill">P{{ s.driver.grid }} → {{ s.driver.positionText }}</span>
      </div>
      <h4>Stints <span class="muted">(between pit stops; Jolpica has no tyre compound data)</span></h4>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>#</th><th>Laps</th><th class="num">Length</th><th class="num">Clean</th><th class="num">Average</th><th class="num">Best</th><th class="num">Std dev</th><th class="num">Degradation / lap</th></tr>
          </thead>
          <tbody>
            @for (st of s.stints; track st.number) {
              <tr>
                <td>{{ st.number }}</td>
                <td>{{ st.startLap }}–{{ st.endLap }}</td>
                <td class="num">{{ st.laps }}</td>
                <td class="num">{{ st.cleanLaps }}</td>
                <td class="num">{{ lap(st.avgMs) }}</td>
                <td class="num">{{ lap(st.bestMs) }}</td>
                <td class="num">{{ sec(st.stdDevMs) }}</td>
                <td class="num">{{ signed(st.degradationMsPerLap) }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
    @if (impact(); as r) {
      <h4>Pit stops</h4>
      @if (r.stops.length === 0) {
        <p class="muted">No pit stops.</p>
      }
      @for (p of r.stops; track p.stopNumber) {
        <div class="stop">
          <div class="stop-head">
            <strong>Stop {{ p.stopNumber }} · lap {{ p.lap }}</strong>
            <span class="pill">pit lane {{ sec(p.pitLaneMs) }}</span>
            <span class="pill">time lost {{ sec(p.timeLostMs) }}</span>
            <span class="pill" [class.bad]="p.positionsLost > 0" [class.good]="p.positionsLost < 0">
              P{{ p.positionBefore }} → P{{ p.positionAfterOutLap ?? '–' }}
              {{ p.positionsLost > 0 ? '▼ ' + p.positionsLost : p.positionsLost < 0 ? '▲ ' + -p.positionsLost : '' }}
            </span>
            @if (p.underNeutralisation) {
              <span class="pill warn">⚠ under SC/VSC</span>
            }
          </div>
          @if (p.carsWithinPitLoss.length) {
            <p class="small">Cars closer behind than the time lost: {{ gaps(p.carsWithinPitLoss) }}</p>
          }
          @for (m of p.passedBy; track m.driverId) {
            <p class="small">▼ {{ m.code }} got ahead: {{ m.explanation }}</p>
          }
          @for (m of p.gainedOn; track m.driverId) {
            <p class="small">▲ Gained on {{ m.code }}: {{ m.explanation }}</p>
          }
          <p class="small muted">Position 3 laps later: P{{ p.positionThreeLapsLater ?? '–' }}</p>
        </div>
      }
    }
  `,
})
export class StrategyPanel {
  readonly raceId = input.required<number>();
  readonly driverId = input.required<string>();
  readonly slot = input.required<number>();

  private readonly api = inject(ApiService);
  protected readonly strategy = signal<DriverStrategy | null>(null);
  protected readonly impact = signal<PitImpactReport | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly lap = lapTime;
  protected readonly sec = seconds;
  protected readonly signed = signedSeconds;

  constructor() {
    effect((onCleanup) => {
      const raceId = this.raceId();
      const driverId = this.driverId();
      const sub = forkJoin([this.api.strategy(raceId, driverId), this.api.pitImpact(raceId, driverId)]).subscribe({
        next: ([s, p]) => {
          this.strategy.set(s);
          this.impact.set(p);
          this.error.set(null);
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
      onCleanup(() => sub.unsubscribe());
    });
  }

  protected gaps(list: { code: string; gapMs: number }[]): string {
    return list.map((g) => `${g.code} (${seconds(g.gapMs, 1)})`).join(', ');
  }
}
