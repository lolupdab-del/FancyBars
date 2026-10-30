package dev.fancybars.util;

import java.util.regex.Matcher;

public class RegexUtils {
	public static int parseIntFromMatcher(Matcher matcher, int group) {
		return Integer.parseInt(matcher.group(group).replace(",", ""));
	}

	public static int parseIntFromMatcher(Matcher matcher, String group) {
		return Integer.parseInt(matcher.group(group).replace(",", ""));
	}
}
