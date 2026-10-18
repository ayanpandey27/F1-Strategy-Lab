import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { ApiService, errorMessage } from './api.service';
import { ChartTheme, axisStyle, baseOptions } from './chart-theme';
import { lapTime, seconds, signedSeconds } from './format';
import { SelectedDriver } from './lap-chart';
import { Comparison } from './models';

/** Head-to-head consistency table plus the race-time gap between two drivers lap by lap. */
@Component({
  selector: 'app-compare-panel',
  imports: [NgxEchartsDirective],
  template: `
    @if (error()) {
      <p class="error">{{ error() }}</p>
    }
    @if (cmp(); as c) {
      <div class="card-head"><h3>{{ c.first.driver.code }} vs {{ c.second.driver.code }}</h3></div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th></th><th class="num">{{ c.first.driver.code }}</th><th class="num">{{ c.second.driver.code }}</th></tr>
          </thead>
          <tbody>
            <tr><th>Grid → finish</th><td class="num">P{{ c.first.driver.grid }} → {{ c.first.driver.positionText }}</td><td class="num">P{{ c.second.driver.grid }} → {{ c.second.driver.positionText }}</td></tr>
            <tr><th>Stops (laps)</th><td class="num">{{ c.first.stops }} ({{ c.first.pitLaps.join(', ') || '–' }})</td><td class="num">{{ c.second.stops }} ({{ c.second.pitLaps.join(', ') || '–' }})</td></tr>
            <tr><th>Clean-lap average</th><td class="num">{{ lap(c.first.cleanAvgMs) }}</td><td class="num">{{ lap(c.second.cleanAvgMs) }}</td></tr>
            <tr><th>Std deviation</th><td class="num">{{ sec(c.first.cleanStdDevMs) }}</td><td class="num">{{ sec(c.second.cleanStdDevMs) }}</td></tr>
            <tr><th>Variation (CoV)</th><td class="num">{{ c.first.coefficientOfVariationPct }}%</td><td class="num">{{ c.second.coefficientOfVariationPct }}%</td></tr>
            <tr><th>Faster on shared clean laps</th><td class="num">{{ c.headToHead.firstDriverFasterLaps }}</td><td class="num">{{ c.headToHead.secondDriverFasterLaps }}</td></tr>
          </tbody>
        </table>
      </div>
      <p class="caption">
        Mean delta on {{ c.headToHead.commonCleanLaps }} shared clean laps: {{ signed(c.headToHead.meanDeltaMs) }}
        ({{ c.first.driver.code }} minus {{ c.second.driver.code }}; negative = {{ c.first.driver.code }} faster). Lower CoV = more consistent.
      </p>
      <h4>Gap from {{ c.first.driver.code }} to {{ c.second.driver.code }} (s, below zero = {{ c.first.driver.code }} ahead)</h4>
      <div echarts class="chart short" [options]="options()" [autoResize]="true"></div>
    }
  `,
})
export class ComparePanel {
  readonly raceId = input.required<number>();
  readonly pair = input.required<[SelectedDriver, SelectedDriver]>();

  private readonly api = inject(ApiService);
  private readonly theme = inject(ChartTheme);
  protected readonly cmp = signal<Comparison | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly lap = lapTime;
  protected readonly sec = seconds;
  protected readonly signed = signedSeconds;

  constructor() {
    effect((onCleanup) => {
      const [a, b] = this.pair();
      const sub = this.api.compare(this.raceId(), a.id, b.id).subscribe({
        next: (c) => {
          this.cmp.set(c);
          this.error.set(null);
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
      onCleanup(() => sub.unsubscribe());
    });
  }

  protected readonly options = computed(() => {
    const t = this.theme.tokens();
    const c = this.cmp();
    if (!c) return {};
    const color = t.series[this.pair()[0].slot];
    const lastLap = c.gapByLap.at(-1)?.lap ?? 1;
    return {
      ...baseOptions(t),
      legend: { show: false },
      grid: { left: 56, right: 24, top: 16, bottom: 44 },
      tooltip: { ...baseOptions(t).tooltip, valueFormatter: (v: number) => signedSeconds(v * 1000) },
      xAxis: { type: 'value', name: 'Lap', nameLocation: 'middle', nameGap: 28, min: 1, max: lastLap, ...axisStyle(t), splitLine: { show: false } },
      yAxis: { type: 'value', ...axisStyle(t) },
      series: [
        {
          name: 'Gap',
          type: 'line',
          showSymbol: false,
          symbolSize: 8,
          lineStyle: { width: 2, color },
          itemStyle: { color },
          data: c.gapByLap.map((g) => [g.lap, +(g.gapMs / 1000).toFixed(3)]),
          markLine: { silent: true, symbol: 'none', lineStyle: { color: t.axis, type: 'solid' }, label: { show: false }, data: [{ yAxis: 0 }] },
        },
      ],
    };
  });
}
