package dev.fancybars.util;

import java.nio.file.Path;
import java.util.Locale;

import dev.fancybars.FancyBarsMod;

/** Opens a folder or web page in the system's default app (Minecraft 26.3 removed its own helper). */
public final class OpenUtil {
	private OpenUtil() {}

	public static void open(Path path) {
		open(path.toAbsolutePath().toUri().toString());
	}

	public static void open(String uri) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String[] command;
		if (os.contains("win")) command = new String[]{"rundll32", "url.dll,FileProtocolHandler", uri};
		else if (os.contains("mac")) command = new String[]{"open", uri};
		else command = new String[]{"xdg-open", uri};
		try {
			new ProcessBuilder(command).start();
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("Couldn't open {}", uri, e);
		}
	}
}
