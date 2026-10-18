import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from './api.service';
import { lapTime, seconds, signedSeconds } from './format';
import { DriverInfo, WhatIfResult } from './models';

/** "What if they had pitted N laps earlier/later?" – runs the backend degradation model. */
@Component({
  selector: 'app-what-if-panel',
  imports: [FormsModule],
  template: `
    <div class="card-head"><h3>What-if: move a pit stop</h3></div>
    <form class="row" (ngSubmit)="run()">
      <label>Driver
        <select [(ngModel)]="driverId" name="driver">
          @for (d of drivers(); track d.id) {
            <option [value]="d.id">{{ d.code }} – {{ d.name }}</option>
          }
        </select>
      </label>
      <label>Stop
        <input type="number" min="1" max="5" [(ngModel)]="stop" name="stop" />
      </label>
      <label>Laps earlier <span class="muted">(negative = later)</span>
        <input type="number" min="-30" max="30" [(ngModel)]="lapsEarlier" name="laps" />
      </label>
      <button type="submit" class="primary" [disabled]="busy() || !driverId">Run</button>
    </form>
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (result(); as w) {
      <div class="whatif">
        <p class="headline" [class.good]="w.deltaMs < 0" [class.bad]="w.deltaMs > 0">
          {{ w.driver.code }} pitting on lap {{ w.newPitLap }} instead of {{ w.originalPitLap }}:
          <strong>{{ signed(w.deltaMs) }}</strong> over laps {{ w.segmentStartLap }}–{{ w.segmentEndLap }}
          ({{ w.deltaMs < 0 ? 'faster' : 'slower' }})
        </p>
        <p>Estimated finish: <strong>P{{ w.estimatedPosition ?? '–' }}</strong> (actual P{{ w.actualPosition ?? '–' }})</p>
        @for (c of w.positionChanges; track c) {
          <p class="small">• {{ c }}</p>
        }
        <p class="small">
          Real {{ lap(w.originalSegmentMs) }} vs modelled {{ lap(w.newSegmentMs) }} ·
          degradation {{ signed(w.oldStintDegradationMsPerLap) }}/lap before, {{ signed(w.newStintDegradationMsPerLap) }}/lap after ·
          pit loss {{ sec(w.estimatedPitLossMs) }}
        </p>
        @for (warn of w.warnings; track warn) {
          <p class="small warn-text">⚠ {{ warn }}</p>
        }
        <details>
          <summary>Model assumptions</summary>
          <ul>
            @for (a of w.assumptions; track a) {
              <li>{{ a }}</li>
            }
          </ul>
        </details>
      </div>
    }
  `,
})
export class WhatIfPanel {
  readonly raceId = input.required<number>();
  readonly drivers = input.required<DriverInfo[]>();
  readonly preferredDriverId = input<string | null>(null);

  private readonly api = inject(ApiService);
  protected driverId = '';
  protected stop = 1;
  protected lapsEarlier = 5;
  protected readonly busy = signal(false);
  protected readonly result = signal<WhatIfResult | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly lap = lapTime;
  protected readonly sec = seconds;
  protected readonly signed = signedSeconds;

  constructor() {
    effect(() => {
      const preferred = this.preferredDriverId();
      const list = this.drivers();
      if (preferred) this.driverId = preferred;
      else if (!list.some((d) => d.id === this.driverId)) this.driverId = list[0]?.id ?? '';
    });
  }

  protected run(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.whatIf(this.raceId(), this.driverId, this.stop, this.lapsEarlier).subscribe({
      next: (w) => {
        this.result.set(w);
        this.busy.set(false);
      },
      error: (e) => {
        this.result.set(null);
        this.error.set(errorMessage(e));
        this.busy.set(false);
      },
    });
  }
}
