package dev.fancybars.dungeons;

import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;

import dev.fancybars.util.SkyblockLocation;

/** Tracks floor 7 phase 3 (terminals): from Goldor's first line until the Core entrance opens. */
public final class F7Phase {
	private static final Pattern P3_START = Pattern.compile("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$");
	private static final Pattern CORE_OPENING = Pattern.compile("^The Core entrance is opening!$");
	private static boolean p3;
	private static final java.util.List<Runnable> START = new java.util.ArrayList<>();
	private static final java.util.List<Runnable> END = new java.util.ArrayList<>();

	private F7Phase() {}

	public static void init() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			String text = ChatFormatting.stripFormatting(message.getString());
			if (text == null) return;
			text = text.trim();
			if (P3_START.matcher(text).matches()) {
				p3 = true;
				START.forEach(Runnable::run);
			} else if (CORE_OPENING.matcher(text).matches()) {
				p3 = false;
				END.forEach(Runnable::run);
			}
		});
		SkyblockLocation.onLocationChange(() -> {
			if (p3) END.forEach(Runnable::run);
			p3 = false;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (p3 && !SkyblockLocation.isInDungeons()) {
				p3 = false;
				END.forEach(Runnable::run);
			}
		});
	}

	public static boolean inP3() {
		return p3;
	}

	public static void onStart(Runnable r) {
		START.add(r);
	}

	public static void onEnd(Runnable r) {
		END.add(r);
	}
}
