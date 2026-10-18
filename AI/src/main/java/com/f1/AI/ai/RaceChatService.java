package com.f1.AI.ai;

import com.f1.AI.ai.QuestionParser.ParsedQuestion;
import com.f1.AI.analysis.AnalysisService;
import com.f1.AI.analysis.StrategyAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Answers race questions with a local Ollama model. The backend computes every number first (see
 * {@link FactsBuilder}); the model only explains those facts. If Ollama is not running, the facts are returned as-is.
 */
@Service
public class RaceChatService {

    private static final Logger log = LoggerFactory.getLogger(RaceChatService.class);

    static final String SYSTEM_PROMPT = """
            You are a Formula 1 race strategy analyst explaining data to a fan.
            Rules:
            - Use ONLY the FACTS. Every number you write must appear in the FACTS, copied exactly.
            - If a line says KEY FINDING, base your answer on it.
            - Do not invent causes that are not in the FACTS (no crew errors, damage, team orders or weather).
            - Tyre compounds are NOT in the data: never mention soft, medium or hard tyres.
            - If the FACTS do not answer the question, say what is missing.
            - Only for a what-if question: give the estimated time difference and finish, then the warnings.
            Answer in plain English in 3 to 6 short sentences or bullet points. No headings.
            """;

    private final AnalysisService analysis;
    private final OllamaStatusService ollama;
    private final ChatClient chatClient;
    private final String model;

    public RaceChatService(AnalysisService analysis, OllamaStatusService ollama, ChatClient.Builder chatClientBuilder,
                           @Value("${spring.ai.ollama.chat.model:llama3.2}") String model) {
        this.analysis = analysis;
        this.ollama = ollama;
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.model = model;
    }

    public record ChatAnswer(String answer, String facts, List<String> driversUsed, boolean llmUsed, String model) {
    }

    public ChatAnswer ask(long raceId, String question, List<String> selectedDriverIds) {
        StrategyAnalyzer analyzer = analysis.analyzer(raceId);
        String facts = buildFacts(analyzer, question, selectedDriverIds);
        ParsedQuestion parsed = QuestionParser.parse(question, analyzer.drivers(), selectedDriverIds);
        OllamaStatusService.Status status = ollama.status();
        if (!status.running() || !status.modelAvailable()) {
            return new ChatAnswer("The local AI model is not ready (" + status.hint() + "), so here are the computed facts instead.",
                    facts, parsed.driverIds(), false, model);
        }
        try {
            String answer = chatClient.prompt()
                    .user(u -> u.text("FACTS:\n{facts}\n\nQUESTION: {question}")
                            .param("facts", facts)
                            .param("question", question))
                    .call()
                    .content();
            return new ChatAnswer(answer, facts, parsed.driverIds(), true, model);
        } catch (Exception e) {
            log.warn("Local LLM unavailable ({}); returning computed facts only", e.getMessage());
            String fallback = "The local AI model is not reachable, so here are the computed facts instead. "
                    + "Start Ollama and run `ollama pull " + model + "` to get written explanations.";
            return new ChatAnswer(fallback, facts, parsed.driverIds(), false, model);
        }
    }

    /** Public so the UI can show exactly what the model was given ("Data used"). */
    public String buildFacts(StrategyAnalyzer analyzer, String question, List<String> selectedDriverIds) {
        ParsedQuestion parsed = QuestionParser.parse(question, analyzer.drivers(), selectedDriverIds);
        List<String> ids = parsed.driverIds();
        FactsBuilder facts = new FactsBuilder().summary(analyzer.summary(), ids.isEmpty());
        for (String id : ids.stream().limit(3).toList()) {
            facts.strategy(analyzer.strategy(id)).pitImpact(analyzer.pitImpact(id));
        }
        if (ids.size() >= 2) {
            facts.comparison(analyzer.compare(ids.get(0), ids.get(1)));
        }
        parsed.whatIf().ifPresent(w -> {
            if (ids.isEmpty()) {
                facts.note("The what-if needs a driver: mention one by name or select one in the dashboard.");
                return;
            }
            try {
                facts.whatIf(analyzer.whatIf(ids.get(0), w.stopNumber(), w.lapsEarlier()));
            } catch (RuntimeException e) {
                facts.note("What-if could not be computed: " + e.getMessage());
            }
        });
        return facts.build();
    }
}
