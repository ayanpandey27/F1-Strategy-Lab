package com.f1.AI.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeParserTest {

    @Test
    void parsesErgastTimeFormats() {
        assertThat(TimeParser.toMillis("1:37.284")).isEqualTo(97_284);
        assertThat(TimeParser.toMillis("22.345")).isEqualTo(22_345);
        assertThat(TimeParser.toMillis("1:31:44.742")).isEqualTo(5_504_742);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> TimeParser.toMillis(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsLapTimes() {
        assertThat(TimeParser.format(97_284)).isEqualTo("1:37.284");
        assertThat(TimeParser.format(22_345)).isEqualTo("22.345");
    }
}
