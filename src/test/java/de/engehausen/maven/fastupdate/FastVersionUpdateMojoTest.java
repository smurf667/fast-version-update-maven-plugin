package de.engehausen.maven.fastupdate;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FastVersionUpdateMojoTest {

	@Test
	public void testExecution() throws IOException, InterruptedException {
		final Path root = Paths.get("").toAbsolutePath();
		final File workingDirectory = root.resolve("target/test-project").toFile();
		FileUtils.deleteDirectory(workingDirectory);
		FileUtils.copyDirectory(root.resolve("examples/spring-boot-multi-module").toFile(), workingDirectory);

		final ProcessBuilder processBuilder = new ProcessBuilder(SystemUtils.IS_OS_WINDOWS ? "mvn.cmd" : "mvn", "de.engehausen:fast-version-update-maven-plugin:update", "-DgenerateBackupPoms=false");
		processBuilder.directory(workingDirectory);
		processBuilder.redirectErrorStream(true);
		final File log = new File(workingDirectory, "output.log");
		processBuilder.redirectOutput(Redirect.to(log));

		final Process process = processBuilder.start();
		if (process.waitFor(2, TimeUnit.MINUTES)) {
			final String logContents = FileUtils.readFileToString(log, StandardCharsets.UTF_8);
			for (final String needle : new String[] {
				"BUILD SUCCESS",
				"Updated org.apache.lucene:lucene-spellchecker:jar:3.6.1",
				"Updated ${pdfbox.version} from 2.0.22",
				"Updating parent from 2.4.6"
			}) {
				Assertions.assertTrue(logContents.contains(needle), String.format("'%s' not found", needle));
			}
		} else {
			process.destroyForcibly();
			Assertions.fail("Timeout after two minutes!");
		}
	}

}
