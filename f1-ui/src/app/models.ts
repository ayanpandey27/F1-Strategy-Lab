// Mirrors the Spring Boot DTOs in com.f1.AI (all times in milliseconds).

export interface RaceView {
  id: number | null;
  season: number;
  round: number;
  name: string;
  circuit: string;
  country: string;
  date: string;
  totalLaps: number;
  imported: boolean;
}

export interface DriverInfo {
  id: string;
  code: string;
  name: string;
  team: string;
  grid: number;
  position: number | null;
  positionText: string;
  status: string;
  points: number;
  lapsCompleted: number;
  fastestLapRank: number | null;
  fastestLapTime: string | null;
}

export interface Stint {
  number: number;
  startLap: number;
  endLap: number;
  laps: number;
  cleanLaps: number;
  avgMs: number | null;
  medianMs: number | null;
  stdDevMs: number | null;
  bestMs: number | null;
  degradationMsPerLap: number | null;
}

export interface DriverStrategy {
  driver: DriverInfo;
  stops: number;
  pitLaps: number[];
  stints: Stint[];
  cleanAvgMs: number | null;
  cleanStdDevMs: number | null;
  coefficientOfVariationPct: number | null;
}

export interface RivalMove {
  driverId: string;
  code: string;
  positionBefore: number;
  positionAfter: number;
  explanation: string;
}

export interface GapBehind {
  code: string;
  gapMs: number;
}

export interface PitStopImpact {
  stopNumber: number;
  lap: number;
  pitLaneMs: number;
  timeLostMs: number | null;
  positionBefore: number;
  positionAfterOutLap: number | null;
  positionThreeLapsLater: number | null;
  positionsLost: number;
  underNeutralisation: boolean;
  carsBehindBefore: GapBehind[];
  carsWithinPitLoss: GapBehind[];
  passedBy: RivalMove[];
  gainedOn: RivalMove[];
}

export interface PitImpactReport {
  driver: DriverInfo;
  stops: PitStopImpact[];
}

export interface WhatIfResult {
  driver: DriverInfo;
  stopNumber: number;
  originalPitLap: number;
  newPitLap: number;
  segmentStartLap: number;
  segmentEndLap: number;
  originalSegmentMs: number;
  newSegmentMs: number;
  deltaMs: number;
  oldStintDegradationMsPerLap: number | null;
  newStintDegradationMsPerLap: number | null;
  estimatedPitLossMs: number;
  actualPosition: number | null;
  estimatedPosition: number | null;
  positionChanges: string[];
  warnings: string[];
  assumptions: string[];
}

export interface GapPoint {
  lap: number;
  gapMs: number;
}

export interface Comparison {
  first: DriverStrategy;
  second: DriverStrategy;
  headToHead: {
    commonCleanLaps: number;
    meanDeltaMs: number | null;
    firstDriverFasterLaps: number;
    secondDriverFasterLaps: number;
  };
  gapByLap: GapPoint[];
}

export interface DriverLine {
  code: string;
  name: string;
  team: string;
  grid: number;
  finish: string;
  status: string;
  stops: number;
  pitLaps: number[];
  stintLengths: number[];
}

export interface Movement {
  code: string;
  grid: number;
  finish: number;
  change: number;
}

export interface RaceSummary {
  race: { id: number; season: number; round: number; name: string; circuit: string; country: string; date: string; totalLaps: number };
  winner: string | null;
  podium: string[];
  fastestLap: { code: string; time: string; lap: number | null } | null;
  classification: DriverLine[];
  mostCommonStopCount: string | null;
  biggestGainers: Movement[];
  biggestLosers: Movement[];
  retirements: string[];
  neutralisedLapRanges: string[];
  stopsUnderNeutralisation: { code: string; lap: number; stopNumber: number }[];
  fastestPitStop: { code: string; pitLaneMs: number; lap: number } | null;
}

export interface LapPoint {
  lap: number;
  timeMs: number;
  position: number;
}

export interface DriverLaps {
  driverId: string;
  code: string;
  name: string;
  team: string;
  laps: LapPoint[];
  pitLaps: number[];
}

export interface LapChartData {
  totalLaps: number;
  neutralisedLaps: number[];
  drivers: DriverLaps[];
}

export interface ChatAnswer {
  answer: string;
  facts: string;
  driversUsed: string[];
  llmUsed: boolean;
  model: string;
}

export interface OllamaStatus {
  running: boolean;
  modelAvailable: boolean;
  model: string;
  baseUrl: string;
  installedModels: string[];
  hint: string | null;
}
