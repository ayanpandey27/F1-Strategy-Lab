package com.f1.AI.ai;

import com.f1.AI.analysis.AnalysisService;
import com.f1.AI.analysis.StrategyAnalyzer;
import com.f1.AI.analysis.StrategyAnalyzerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RaceChatServiceTest {

    static final OllamaStatusService.Status UP =
            new OllamaStatusService.Status(true, true, "llama3.2", "http://localhost:11434", List.of("llama3.2:latest"), null);
    static final OllamaStatusService.Status DOWN =
            new OllamaStatusService.Status(false, false, "llama3.2", "http://localhost:11434", List.of(), "Ollama is not running");

    AnalysisService analysis = mock(AnalysisService.class);
    OllamaStatusService ollama = mock(OllamaStatusService.class);
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    RaceChatService service;

    @BeforeEach
    void setUp() {
        when(analysis.analyzer(1L)).thenReturn(new StrategyAnalyzer(StrategyAnalyzerTest.race(Set.of())));
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        service = new RaceChatService(analysis, ollama, builder, "llama3.2");
    }

    @Test
    void groundsFactsInComputedAnalysis() {
        String facts = service.buildFacts(analysis.analyzer(1L), "What if Alpha had pitted five laps earlier?", List.of());

        assertThat(facts)
                .contains("Winner: Alice Alpha (Team A)")
                .contains("STRATEGY: AAA")
                .contains("PIT STOP IMPACT: AAA")
                .contains("WHAT-IF: AAA stop 1 on lap 5 instead of lap 10")
                .contains("difference +2.500s");
    }

    @Test
    void explainsLostPositionsInPlainWords() {
        String facts = service.buildFacts(analysis.analyzer(1L), "Why did Alpha lose positions after the pit stop?", List.of());

        assertThat(facts)
                .contains("KEY FINDING: AAA dropped 2 place(s) because the stop cost 20.100s and 2 car(s) were closer behind than that.")
                .contains("1 of the cars that got ahead had not pitted yet")
                .contains("1 to cars that did not stop in that window")
                .contains("Cars BEHIND AAA before the stop")
                // driver-specific questions skip the full classification to keep small-model prompts short
                .doesNotContain("CLASSIFICATION AND STRATEGIES");
    }

    @Test
    void comparesTwoSelectedDrivers() {
        String facts = service.buildFacts(analysis.analyzer(1L), "Compare their consistency", List.of("aaa", "bbb"));
        assertThat(facts)
                .contains("HEAD TO HEAD: AAA vs BBB")
                .contains("KEY FINDING: AAA was faster by")
                .contains("AAA ran 2 stints: 10 laps (tyres slowing +0.100s/lap), 10 laps (tyres slowing +0.100s/lap).");
    }

    @Test
    void skipsLlmWhenOllamaIsDown() {
        when(ollama.status()).thenReturn(DOWN);

        RaceChatService.ChatAnswer answer = service.ask(1L, "Summarize the race", List.of());

        assertThat(answer.llmUsed()).isFalse();
        assertThat(answer.facts()).contains("Winner:");
        verify(client, never()).prompt();
    }

    @Test
    void fallsBackToFactsWhenLlmCallFails() {
        when(ollama.status()).thenReturn(UP);
        when(client.prompt()).thenThrow(new RuntimeException("Connection refused"));

        RaceChatService.ChatAnswer answer = service.ask(1L, "Summarize the race", List.of());

        assertThat(answer.llmUsed()).isFalse();
        assertThat(answer.answer()).contains("ollama pull llama3.2");
    }
}
