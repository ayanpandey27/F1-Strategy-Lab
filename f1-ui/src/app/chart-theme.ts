import { Injectable, signal } from '@angular/core';

/** Number of driver colour slots; the first three palette slots pass every colour-blind pair check. */
export const MAX_SELECTED = 3;

export interface ChartTokens {
  surface: string;
  ink: string;
  secondary: string;
  muted: string;
  grid: string;
  axis: string;
  band: string;
  series: string[];
}

/**
 * Reads chart colours from CSS custom properties (styles.css) so ECharts follows light/dark mode.
 * `tokens` changes whenever the OS colour scheme flips, and charts re-render from it.
 */
@Injectable({ providedIn: 'root' })
export class ChartTheme {
  readonly tokens = signal<ChartTokens>(read());

  constructor() {
    window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener('change', () => this.tokens.set(read()));
  }
}

function read(): ChartTokens {
  const css = getComputedStyle(document.documentElement);
  const v = (name: string) => css.getPropertyValue(name).trim();
  return {
    surface: v('--surface-1'),
    ink: v('--text-primary'),
    secondary: v('--text-secondary'),
    muted: v('--text-muted'),
    grid: v('--grid'),
    axis: v('--axis'),
    band: v('--band'),
    series: [v('--series-1'), v('--series-2'), v('--series-3')],
  };
}

/** Shared tooltip / axis styling so every chart looks like part of one system. */
export function baseOptions(t: ChartTokens) {
  return {
    backgroundColor: 'transparent',
    textStyle: { color: t.secondary, fontFamily: 'inherit' },
    grid: { left: 56, right: 24, top: 40, bottom: 44 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: t.axis } },
      backgroundColor: t.surface,
      borderColor: t.axis,
      textStyle: { color: t.ink, fontSize: 12 },
    },
    legend: { top: 0, left: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 4, textStyle: { color: t.secondary } },
  };
}

export function axisStyle(t: ChartTokens) {
  return {
    axisLine: { lineStyle: { color: t.axis } },
    axisTick: { show: false },
    axisLabel: { color: t.muted },
    splitLine: { lineStyle: { color: t.grid } },
    nameTextStyle: { color: t.muted },
  };
}
