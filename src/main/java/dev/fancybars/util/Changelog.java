package dev.fancybars.util;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import dev.fancybars.FancyBarsMod;

/** The update history shipped with the mod (assets/fancybars/changelog.json), newest first. */
public final class Changelog {
	public record Entry(String version, String date, List<String> added, List<String> fixed) {}

	private static List<Entry> entries;

	private Changelog() {}

	public static List<Entry> entries() {
		if (entries == null) {
			try (var reader = new InputStreamReader(Changelog.class.getResourceAsStream("/assets/fancybars/changelog.json"), StandardCharsets.UTF_8)) {
				entries = new Gson().fromJson(reader, new TypeToken<List<Entry>>() {}.getType());
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't read the changelog", e);
				entries = List.of();
			}
		}
		return entries;
	}
}
