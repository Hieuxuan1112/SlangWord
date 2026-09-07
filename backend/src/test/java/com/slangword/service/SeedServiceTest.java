package com.slangword.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SeedServiceTest {

    @Test
    void parsesWordAndSingleDefinition() {
        List<SeedService.SeedEntry> entries = SeedService.parse(List.of("BBC`British Broadcasting Corporation"));

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.word()).isEqualTo("BBC");
            assertThat(entry.definitions()).containsExactly("British Broadcasting Corporation");
        });
    }

    @Test
    void splitsMultipleSensesOnPipe() {
        List<SeedService.SeedEntry> entries = SeedService.parse(List.of("JCB`J C Bamford | excavator manufacturer"));

        assertThat(entries).singleElement()
                .extracting(SeedService.SeedEntry::definitions)
                .isEqualTo(List.of("J C Bamford", "excavator manufacturer"));
    }

    @Test
    void skipsBlankAndMalformedLines() {
        assertThat(SeedService.parse(List.of("", "no-backtick-here", "A`", "`B"))).isEmpty();
    }

    @Test
    void keepsLastOccurrenceOfDuplicateWord() {
        List<SeedService.SeedEntry> entries = SeedService.parse(List.of("X`first", "X`second"));

        assertThat(entries).singleElement()
                .extracting(SeedService.SeedEntry::definitions)
                .isEqualTo(List.of("second"));
    }
}
