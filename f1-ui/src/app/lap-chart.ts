import { Component, computed, inject, input, signal } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { ChartTheme, axisStyle, baseOptions } from './chart-theme';
import { lapTime } from './format';
import { LapChartData } from './models';

export interface SelectedDriver {
  id: string;
  code: string;
  slot: number;
}

type Mode = 'time' | 'position';

/** Lap times or running position for the selected drivers, with pit laps and suspected SC/VSC laps marked. */
@Component({
  selector: 'app-lap-chart',
  imports: [NgxEchartsDirective],
  template: `
    <div class="card-head">
      <h3>{{ mode() === 'time' ? 'Lap times' : 'Running position' }}</h3>
      <div class="seg" role="group" aria-label="Chart mode">
        <button [class.on]="mode() === 'time'" (click)="mode.set('time')">Lap time</button>
        <button [class.on]="mode() === 'position'" (click)="mode.set('position')">Position</button>
      </div>
      @if (mode() === 'time') {
        <label class="check"><input type="checkbox" [checked]="cleanOnly()" (change)="cleanOnly.set(!cleanOnly())" /> Hide lap 1, pit &amp; SC laps</label>
      }
    </div>
    @if (selected().length === 0) {
      <p class="muted">Select up to 3 drivers above to plot them.</p>
    } @else {
      <div echarts class="chart" [options]="options()" [autoResize]="true"></div>
      <p class="caption">Dashed lines mark pit laps. Shaded bands are suspected Safety Car / VSC laps (field running &gt;15% slower than normal).</p>
    }
  `,
})
export class LapChart {
  readonly data = input.required<LapChartData>();
  readonly selected = input.required<SelectedDriver[]>();

  protected readonly mode = signal<Mode>('time');
  protected readonly cleanOnly = signal(true);
  private readonly theme = inject(ChartTheme);

  protected readonly options = computed(() => {
    const t = this.theme.tokens();
    const data = this.data();
    const sc = new Set(data.neutralisedLaps);
    const isTime = this.mode() === 'time';

    const series = this.selected().flatMap((sel, i) => {
      const d = data.drivers.find((x) => x.driverId === sel.id);
      if (!d) return [];
      const color = t.series[sel.slot];
      const hidden = new Set<number>([1, ...d.pitLaps, ...d.pitLaps.map((l) => l + 1), ...sc]);
      const points = d.laps.map((l) => {
        if (!isTime) return [l.lap, l.position];
        return this.cleanOnly() && hidden.has(l.lap) ? [l.lap, null] : [l.lap, +(l.timeMs / 1000).toFixed(3)];
      });
      return [
        {
          name: d.code,
          type: 'line',
          step: isTime ? undefined : 'end',
          data: points,
          showSymbol: false,
          symbolSize: 8,
          connectNulls: false,
          lineStyle: { width: 2, color },
          itemStyle: { color },
          emphasis: { focus: 'series' },
          endLabel: { show: true, formatter: d.code, color: t.ink, fontWeight: 600 },
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { color, type: 'dashed', width: 1 },
            label: { formatter: `${d.code} pit`, color: t.secondary, fontSize: 10, position: 'insideEndTop' },
            data: d.pitLaps.map((lap) => ({ xAxis: lap })),
          },
          markArea:
            i === 0
              ? { silent: true, itemStyle: { color: t.band }, data: ranges(data.neutralisedLaps).map(([a, b]) => [{ xAxis: a - 0.5 }, { xAxis: b + 0.5 }]) }
              : undefined,
        },
      ];
    });

    return {
      ...baseOptions(t),
      grid: { left: 56, right: 56, top: 40, bottom: 44 },
      tooltip: {
        ...baseOptions(t).tooltip,
        valueFormatter: (v: number | null) => (v == null ? 'hidden' : isTime ? lapTime(v * 1000) : `P${v}`),
      },
      xAxis: { type: 'value', name: 'Lap', nameLocation: 'middle', nameGap: 28, min: 1, max: data.totalLaps, ...axisStyle(t), splitLine: { show: false } },
      yAxis: isTime
        ? { type: 'value', name: 'Lap time (s)', scale: true, ...axisStyle(t) }
        : { type: 'value', name: 'Position', inverse: true, min: 1, max: data.drivers.length, interval: 1, ...axisStyle(t) },
      series,
    };
  });
}

function ranges(laps: number[]): [number, number][] {
  const sorted = [...laps].sort((a, b) => a - b);
  const out: [number, number][] = [];
  for (const lap of sorted) {
    const last = out[out.length - 1];
    if (last && lap === last[1] + 1) last[1] = lap;
    else out.push([lap, lap]);
  }
  return out;
}
