import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from './api.service';
import { MAX_SELECTED } from './chart-theme';
import { ChatPanel } from './chat-panel';
import { ComparePanel } from './compare-panel';
import { seconds } from './format';
import { LapChart, SelectedDriver } from './lap-chart';
import { DriverInfo, LapChartData, RaceSummary, RaceView } from './models';
import { StrategyPanel } from './strategy-panel';
import { WhatIfPanel } from './what-if-panel';

@Component({
  selector: 'app-root',
  imports: [FormsModule, LapChart, ComparePanel, StrategyPanel, WhatIfPanel, ChatPanel],
  templateUrl: './app.html',
})
export class App {
  private readonly api = inject(ApiService);

  protected season = 2024;
  protected readonly races = signal<RaceView[]>([]);
  protected readonly raceRound = signal<number | null>(null);
  protected readonly raceId = signal<number | null>(null);
  protected readonly summary = signal<RaceSummary | null>(null);
  protected readonly drivers = signal<DriverInfo[]>([]);
  protected readonly laps = signal<LapChartData | null>(null);
  protected readonly loading = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly maxSelected = MAX_SELECTED;
  protected readonly sec = seconds;

  /** Selected drivers keep their colour slot until deselected (colour follows the driver, not the order). */
  protected readonly selected = signal<SelectedDriver[]>([]);
  protected readonly selectedIds = computed(() => this.selected().map((s) => s.id));
  protected readonly selectedNames = computed(() =>
    this.selected().map((s) => this.drivers().find((d) => d.id === s.id)?.name.split(' ').pop() ?? s.code),
  );
  protected readonly pair = computed(() => {
    const s = this.selected();
    return s.length >= 2 ? ([s[0], s[1]] as [SelectedDriver, SelectedDriver]) : null;
  });
  protected readonly sortedDrivers = computed(() =>
    [...this.drivers()].sort((a, b) => (a.position ?? 99) - (b.position ?? 99) || b.lapsCompleted - a.lapsCompleted),
  );

  constructor() {
    // Deep link: ?season=2024&round=1 opens that race directly.
    const params = new URLSearchParams(window.location.search);
    const season = Number(params.get('season'));
    if (season) this.season = season;
    this.loadSeason(Number(params.get('round')) || null);
  }

  protected loadSeason(openRound: number | null = null): void {
    this.error.set(null);
    this.loading.set(`Loading ${this.season} calendar…`);
    this.api.schedule(this.season).subscribe({
      next: (r) => {
        this.races.set(r);
        this.loading.set(null);
        if (openRound) this.selectRace(openRound);
      },
      error: (e) => this.fail(e),
    });
  }

  protected selectRace(round: number | null): void {
    this.raceRound.set(round);
    const race = this.races().find((r) => r.round === round);
    if (!race) return;
    history.replaceState(null, '', `?season=${race.season}&round=${race.round}`);
    this.error.set(null);
    this.loading.set(race.imported ? 'Loading race…' : `Downloading ${race.name} from Jolpica (first time only, ~20 s)…`);
    this.api.importRace(race.season, race.round).subscribe({
      next: (imported) => {
        this.races.update((list) => list.map((r) => (r.round === imported.round ? imported : r)));
        this.loadRace(imported.id!);
      },
      error: (e) => this.fail(e),
    });
  }

  private loadRace(id: number): void {
    forkJoin([this.api.summary(id), this.api.drivers(id), this.api.laps(id)]).subscribe({
      next: ([summary, drivers, laps]) => {
        this.raceId.set(id);
        this.summary.set(summary);
        this.drivers.set(drivers);
        this.laps.set(laps);
        const top = [...drivers].sort((a, b) => (a.position ?? 99) - (b.position ?? 99)).slice(0, 2);
        this.selected.set(top.map((d, i) => ({ id: d.id, code: d.code, slot: i })));
        this.loading.set(null);
      },
      error: (e) => this.fail(e),
    });
  }

  protected isSelected(id: string): boolean {
    return this.selected().some((s) => s.id === id);
  }

  protected slotOf(id: string): number {
    return this.selected().find((s) => s.id === id)?.slot ?? -1;
  }

  protected toggle(d: DriverInfo): void {
    const current = this.selected();
    if (this.isSelected(d.id)) {
      this.selected.set(current.filter((s) => s.id !== d.id));
      return;
    }
    if (current.length >= MAX_SELECTED) return;
    const used = new Set(current.map((s) => s.slot));
    const slot = [0, 1, 2].find((i) => !used.has(i))!;
    this.selected.set([...current, { id: d.id, code: d.code, slot }]);
  }

  private fail(e: unknown): void {
    this.error.set(errorMessage(e));
    this.loading.set(null);
  }
}
