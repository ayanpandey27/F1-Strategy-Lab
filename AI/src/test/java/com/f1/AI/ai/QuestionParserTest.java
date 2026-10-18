package com.f1.AI.ai;

import com.f1.AI.ai.QuestionParser.ParsedQuestion;
import com.f1.AI.analysis.RaceData.DriverInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionParserTest {

    static final List<DriverInfo> DRIVERS = List.of(
            driver("max_verstappen", "VER", "Max Verstappen"),
            driver("perez", "PER", "Sergio Pérez"),
            driver("leclerc", "LEC", "Charles Leclerc"),
            driver("sainz", "SAI", "Carlos Sainz"));

    static DriverInfo driver(String id, String code, String name) {
        return new DriverInfo(id, code, name, "Team", 1, 1, "1", "Finished", 0, 57, null, null);
    }

    @Test
    void findsDriversInMentionOrder() {
        ParsedQuestion q = QuestionParser.parse("Compare Leclerc and Verstappen's tyre stints", DRIVERS, List.of());
        assertThat(q.driverIds()).containsExactly("leclerc", "max_verstappen");
        assertThat(q.whatIf()).isEmpty();
    }

    @Test
    void matchesAccentInsensitiveAndCapitalisedCodes() {
        assertThat(QuestionParser.parse("Why did Perez lose time?", DRIVERS, List.of()).driverIds()).containsExactly("perez");
        assertThat(QuestionParser.parse("How did SAI do?", DRIVERS, List.of()).driverIds()).containsExactly("sainz");
        // lower-case "per" is an English word, not a driver code
        assertThat(QuestionParser.parse("lap time per stint", DRIVERS, List.of()).driverIds()).isEmpty();
    }

    @Test
    void addsSelectedDriversAfterMentionedOnes() {
        ParsedQuestion q = QuestionParser.parse("How did Sainz do?", DRIVERS, List.of("leclerc", "sainz"));
        assertThat(q.driverIds()).containsExactly("sainz", "leclerc");
    }

    @Test
    void parsesWhatIfInDigitsOrWords() {
        assertThat(QuestionParser.parse("What if he had pitted five laps earlier?", DRIVERS, List.of()).whatIf())
                .hasValue(new QuestionParser.WhatIf(5, 1));
        assertThat(QuestionParser.parse("What if Leclerc made his second stop 3 laps later?", DRIVERS, List.of()).whatIf())
                .hasValue(new QuestionParser.WhatIf(-3, 2));
    }
}
