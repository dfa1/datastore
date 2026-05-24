package io.github.dfa1.datastore;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OhlcGeneratorTest {

    private static final LocalDate START = LocalDate.of(2020, 1, 1);
    private static final int COUNT = 1_000;

    private final OhlcGenerator generator = new OhlcGenerator("ACME", START, 100.0, 42L);
    private final List<OhlcRecord> records = generator.generate(COUNT);

    @Test
    void correctCount() {
        assertEquals(COUNT, records.size());
    }

    @Test
    void noWeekends() {
        for (var r : records) {
            var day = r.date().getDayOfWeek();
            assertNotEquals(DayOfWeek.SATURDAY, day, "got Saturday: " + r.date());
            assertNotEquals(DayOfWeek.SUNDAY,   day, "got Sunday: "   + r.date());
        }
    }

    @Test
    void ohlcInvariants() {
        for (var r : records) {
            assertTrue(r.high() >= r.open(),  "high < open at "  + r.date());
            assertTrue(r.high() >= r.close(), "high < close at " + r.date());
            assertTrue(r.low()  <= r.open(),  "low > open at "   + r.date());
            assertTrue(r.low()  <= r.close(), "low > close at "  + r.date());
            assertTrue(r.low()  > 0,          "non-positive low at " + r.date());
            assertTrue(r.volume() > 0,        "non-positive volume at " + r.date());
        }
    }

    @Test
    void datesAscending() {
        for (int i = 1; i < records.size(); i++) {
            assertTrue(records.get(i).date().isAfter(records.get(i - 1).date()),
                    "dates not ascending at index " + i);
        }
    }

    @Test
    void deterministicWithSameSeed() {
        var gen2 = new OhlcGenerator("ACME", START, 100.0, 42L);
        assertEquals(records, gen2.generate(COUNT));
    }

    @Test
    void differentSeedProducesDifferentData() {
        var gen2 = new OhlcGenerator("ACME", START, 100.0, 99L);
        assertNotEquals(records, gen2.generate(COUNT));
    }
}
