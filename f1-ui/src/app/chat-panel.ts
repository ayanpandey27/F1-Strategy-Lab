import { Component, ElementRef, effect, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from './api.service';
import { OllamaStatus } from './models';

interface Message {
  role: 'user' | 'ai';
  text: string;
  facts?: string;
  llmUsed?: boolean;
}

/** Chat with the local LLM. Every answer comes with the computed facts it was given. */
@Component({
  selector: 'app-chat-panel',
  imports: [FormsModule],
  template: `
    <div class="card-head">
      <h3>Ask the strategist</h3>
      @if (status(); as s) {
        <span class="pill" [class.good]="s.running && s.modelAvailable" [class.warn]="!s.running || !s.modelAvailable"
              [title]="s.hint ?? ''">
          {{ s.running && s.modelAvailable ? '● ' + s.model + ' (local)' : '○ AI offline' }}
        </span>
      }
    </div>
    @if (status(); as s) {
      @if (s.hint) {
        <p class="small warn-text">{{ s.hint }}. Answers will show computed facts only.</p>
      }
    }
    <div class="messages" #scroller>
      @if (messages().length === 0) {
        <p class="muted small">Try one of these. Mention drivers by name, or select them in the dashboard.</p>
        @for (q of suggestions(); track q) {
          <button class="suggestion" (click)="ask(q)">{{ q }}</button>
        }
      }
      @for (m of messages(); track $index) {
        <div class="msg" [class.user]="m.role === 'user'">
          <div class="bubble">{{ m.text }}</div>
          @if (m.facts) {
            <details>
              <summary>Data used ({{ m.llmUsed ? 'the AI was only given these facts' : 'computed facts' }})</summary>
              <pre>{{ m.facts }}</pre>
            </details>
          }
        </div>
      }
      @if (busy()) {
        <div class="msg"><div class="bubble muted">Thinking locally… (first answer can take 10–60 s on a laptop CPU)</div></div>
      }
    </div>
    <form class="ask" (ngSubmit)="ask(question)">
      <input [(ngModel)]="question" name="q" placeholder="e.g. Why did Leclerc lose positions after his first stop?" maxlength="1000" />
      <button type="submit" class="primary" [disabled]="busy() || !question.trim()">Ask</button>
    </form>
  `,
})
export class ChatPanel {
  readonly raceId = input.required<number>();
  readonly driverIds = input.required<string[]>();
  readonly driverNames = input.required<string[]>();

  private readonly api = inject(ApiService);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  protected question = '';
  protected readonly busy = signal(false);
  protected readonly messages = signal<Message[]>([]);
  protected readonly status = signal<OllamaStatus | null>(null);
  protected readonly suggestions = signal<string[]>([]);

  constructor() {
    this.api.aiStatus().subscribe({ next: (s) => this.status.set(s), error: () => this.status.set(null) });
    effect(() => {
      this.raceId();
      this.messages.set([]);
    });
    effect(() => {
      const [a, b] = this.driverNames();
      const first = a ?? 'the winner';
      this.suggestions.set([
        'Summarize the race strategy and explain the key decisions.',
        `Why did ${first} lose positions after the pit stop?`,
        `Compare the tyre stints and lap-time consistency of ${first} and ${b ?? 'the runner-up'}.`,
        `What if ${first} had pitted five laps earlier?`,
      ]);
    });
  }

  protected ask(question: string): void {
    const q = question.trim();
    if (!q || this.busy()) return;
    this.question = '';
    this.messages.update((m) => [...m, { role: 'user', text: q }]);
    this.busy.set(true);
    this.scroll();
    this.api.chat(this.raceId(), q, this.driverIds()).subscribe({
      next: (a) => {
        this.messages.update((m) => [...m, { role: 'ai', text: a.answer, facts: a.facts, llmUsed: a.llmUsed }]);
        this.busy.set(false);
        this.scroll();
      },
      error: (e) => {
        this.messages.update((m) => [...m, { role: 'ai', text: errorMessage(e) }]);
        this.busy.set(false);
      },
    });
  }

  private scroll(): void {
    setTimeout(() => {
      const el = this.scroller()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
