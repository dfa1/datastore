package io.github.dfa1.datastore;

import dev.vortex.api.Partition;
import dev.vortex.api.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Minimal reproducer for <a href="https://github.com/vortex-data/vortex/issues/8075">vortex 8075</a>.
 */
class CrashTest {

	@Test
	void reproducer(@TempDir Path dir) throws IOException {
		VortexOhlcStore store = new VortexOhlcStore();
		int i = 0;
		while (true) {
			System.out.println(i);
			OhlcGenerator aaa = new OhlcGenerator(new Symbol("AAA"), LocalDate.now(), 10, 10);
			store.write(aaa.stream(10), dir.resolve("a" + i++));
		}
	}
}
