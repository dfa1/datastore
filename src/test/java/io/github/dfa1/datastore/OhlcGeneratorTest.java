package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OhlcGeneratorTest {

    private static final LocalDate START = LocalDate.of(2020, 1, 1);
    private static final int COUNT = 1_000;

    private static OhlcGenerator gen(long seed) {
        return new OhlcGenerator(new Symbol("ACME"), START, 100.0, seed);
    }

    @Test
    void correctCount() {
        // Given
        OhlcGenerator generator = gen(42);

        // When
        long result = generator.stream(COUNT).count();

        // Then
        assertEquals(COUNT, result);
    }

    @Test
    void noWeekends() {
        // Given
        OhlcGenerator generator = gen(42);

        // When
        List<OhlcRecord> records = generator.stream(COUNT).toList();

        // Then
        records.forEach(r -> {
            var day = r.date().getDayOfWeek();
            assertNotEquals(DayOfWeek.SATURDAY, day, "got Saturday: " + r.date());
            assertNotEquals(DayOfWeek.SUNDAY,   day, "got Sunday: "   + r.date());
        });
    }

    @Test
    void ohlcInvariants() {
        // Given
        OhlcGenerator generator = gen(42);

        // When
        List<OhlcRecord> records = generator.stream(COUNT).toList();

        // Then
        records.forEach(r -> {
            assertTrue(r.high() >= r.open(),  "high < open at "  + r.date());
            assertTrue(r.high() >= r.close(), "high < close at " + r.date());
            assertTrue(r.low()  <= r.open(),  "low > open at "   + r.date());
            assertTrue(r.low()  <= r.close(), "low > close at "  + r.date());
            assertTrue(r.low()  > 0,          "non-positive low at " + r.date());
            assertTrue(r.volume() > 0,        "non-positive volume at " + r.date());
        });
    }

    @Test
    void datesAscending() {
        // Given
        OhlcGenerator generator = gen(42);

        // When
        List<OhlcRecord> records = generator.stream(COUNT).toList();

        // Then
        for (int i = 1; i < records.size(); i++) {
            assertTrue(records.get(i).date().isAfter(records.get(i - 1).date()),
                    "dates not ascending at index " + i);
        }
    }

    @Test
    void deterministicWithSameSeed() {
        // Given
        // Two generators with the same seed

        // When
        List<OhlcRecord> a = gen(42).stream(COUNT).toList();
        List<OhlcRecord> b = gen(42).stream(COUNT).toList();

        // Then
        assertEquals(a, b);
    }

    @Test
    void differentSeedProducesDifferentData() {
        // Given
        // Two generators with different seeds

        // When
        List<OhlcRecord> a = gen(42).stream(COUNT).toList();
        List<OhlcRecord> b = gen(99).stream(COUNT).toList();

        // Then
        assertNotEquals(a, b);
    }
}
