package dev.fancybars.mining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.storage.BackpackPreview;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Forge Tracker: what's in your Forge (Dwarven Mines) and how long until each item is done, read from Hypixel's
 * "The Forge" menu whenever you open it and remembered per profile. When something finishes, a chat message says so;
 * clicking it runs /warp forge.
 */
public final class ForgeTracker {
	private ForgeTracker() {}

	private static final String MENU_TITLE = "The Forge";
	/** "Time Remaining: 1d 2h 3m 4s" (any of the parts, short or spelled out). */
	private static final Pattern REMAINING = Pattern.compile("Time Remaining:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern PART = Pattern.compile("(\\d+)\\s*(d|day|days|h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)\\b", Pattern.CASE_INSENSITIVE);
	/** Lore of an item that's finished and waiting to be picked up. */
	private static final Pattern DONE = Pattern.compile("(?i)^(?:completed!?|click to claim!?|ready to claim!?|ready!?)$");

	/**
	 * One forge slot: what's in it and when it's done (epoch ms). Hypixel's times are cut off ("4h 59m" could be anything
	 * up to 4h 59m 59s), so each item keeps the earliest and latest it can finish; every reading narrows that window,
	 * and it closes in on the exact second each time a number ticks over.
	 */
	private static final class Entry {
		final int slot;
		final ItemStack stack;
		final String name;
		long lo, hi;
		/** Best guess: the middle of the window. */
		long end;
		boolean notified;

		Entry(int slot, ItemStack stack, String name, long lo, long hi, boolean notified) {
			this.slot = slot;
			this.stack = stack;
			this.name = name;
			this.lo = lo;
			this.hi = Math.max(lo, hi);
			this.end = (this.lo + this.hi) / 2;
			this.notified = notified;
		}

		void set(long lo, long hi) {
			this.lo = lo;
			this.hi = Math.max(lo, hi);
			this.end = (this.lo + this.hi) / 2;
		}
	}

	/** Leeway for a reading being a moment old. */
	private static final long SLACK = 1500;

	/** A reading of "{@code text} left" taken now: the window it allows, or null if it isn't a time. */
	private static long @Nullable [] window(String text, long now) {
		long ms = parseDuration(text);
		if (ms < 0) return null;
		return new long[]{now + ms - SLACK, now + ms + smallestUnit(text) + SLACK};
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static @Nullable Path loadedFrom;
	private static boolean dirty;
	private static boolean loggedUnknown;

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?> container) {
				// the menu's items arrive after it opens (and its timers count down while open), so read it every frame
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> readMenu(container));
			}
		});
		dev.fancybars.util.Perf.onTick("Tick ForgeTracker", mc -> {
			if (mc.player == null || !SkyblockLocation.isOnSkyblock()) return;
			Path dir = BackpackPreview.profileDir();
			if (dir != null && !dir.equals(loadedFrom)) {
				loadedFrom = dir;
				if (!dirty) load(dir);
			}
			if (mc.player.tickCount % 20 == 0) {
				readTab(mc);
				checkDone();
			}
			if (dirty && dir != null && mc.player.tickCount % 40 == 0) save(dir);
		});
		dev.fancybars.util.Perf.attachElementAfter(VanillaHudElements.HOTBAR, FancyBarsMod.id("forge_tracker"), (graphics, delta) -> {
			if (shouldShow()) {
				List<Row> rows = rows();
				float[] pos = position(graphics.guiWidth(), graphics.guiHeight(), rows);
				renderAt(graphics, pos[0], pos[1], FancyBarsConfig.get().forgeTrackerScale, rows, 1);
			}
		});
	}

	public static boolean shouldShow() {
		return FancyBarsConfig.get().forgeTrackerEnabled && SkyblockLocation.isOnSkyblock() && !ENTRIES.isEmpty()
				&& dev.fancybars.util.HudVisibility.allowed();
	}

	// ---------------------------------------------------------------- reading the menu

	private static void readMenu(AbstractContainerScreen<?> screen) {
		String title = ChatFormatting.stripFormatting(screen.getTitle().getString());
		if (title == null || !title.trim().equals(MENU_TITLE)) return;
		long now = System.currentTimeMillis();
		List<Entry> found = new ArrayList<>();
		boolean anyItems = false;
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			anyItems = true;
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) continue;
			long[] w = null;
			for (Component line : lore.lines()) {
				String text = ChatFormatting.stripFormatting(line.getString());
				if (text == null) continue;
				text = text.trim();
				Matcher m = REMAINING.matcher(text);
				if (m.find()) {
					w = window(m.group(1), now);
					break;
				}
				if (DONE.matcher(text).matches()) {
					w = new long[]{Long.MIN_VALUE, now};
					break;
				}
			}
			if (w == null) continue;
			String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
			found.add(new Entry(slot.index, stack.copy(), name == null ? "?" : name.trim(), w[0], w[1], false));
		}
		if (!anyItems) return; // still loading
		if (found.isEmpty() && !loggedUnknown) {
			// nothing recognised: write the menu's lore to the log once so the format can be checked
			loggedUnknown = true;
			StringBuilder sb = new StringBuilder("[SkyblockGuiUtils] Forge menu had nothing forging. Items:");
			for (Slot slot : screen.getMenu().slots) {
				if (slot.container instanceof Inventory || slot.getItem().isEmpty() || slot.getItem().getHoverName().getString().isBlank()) continue;
				sb.append("\n  ").append(slot.index).append(": ").append(slot.getItem().getHoverName().getString());
				ItemLore lore = slot.getItem().get(DataComponents.LORE);
				if (lore != null) for (Component l : lore.lines()) sb.append("\n      ").append(l.getString());
			}
			FancyBarsMod.LOGGER.info(sb.toString());
		}
		merge(found, true);
	}

	/**
	 * Replaces what's tracked with {@code found}, keeping each item's announced state (matched by name) and its end
	 * time unless it really moved. {@code viewing}: the player is looking at the Forge right now, so finished items
	 * needn't be announced.
	 */
	private static void merge(List<Entry> found, boolean viewing) {
		long now = System.currentTimeMillis();
		boolean changed = found.size() != ENTRIES.size();
		List<Entry> unused = new ArrayList<>(ENTRIES);
		List<Entry> result = new ArrayList<>();
		for (Entry e : found) {
			// same name; with two of the same item, the one finishing closest to this reading
			Entry old = null;
			for (Entry o : unused) {
				if (o.name.equals(e.name) && (old == null || Math.abs(o.end - e.end) < Math.abs(old.end - e.end))) old = o;
			}
			if (old != null) {
				unused.remove(old);
				// the menu's own item is the best icon: keep it when the new one is from the tab list (or missing)
				ItemStack stack = !old.stack.isEmpty() && (e.stack.isEmpty() || !viewing) ? old.stack : e.stack;
				long lo = Math.max(old.lo, e.lo), hi = Math.min(old.hi, e.hi);
				// readings that don't overlap mean it really changed (restarted, or sped up): start again from this one
				if (lo > hi) {
					lo = e.lo;
					hi = e.hi;
				}
				// (tiny wobbles aren't worth saving the file for)
				if (Math.abs(lo - old.lo) > 1000 || Math.abs(hi - old.hi) > 1000 || stack != old.stack) changed = true;
				else {
					// keep the same entry (it's the one in the list), just a little sharper
					old.set(lo, hi);
					if (viewing && old.hi <= now && !old.notified) {
						old.notified = true;
						dirty = true;
					}
					result.add(old);
					continue;
				}
				Entry merged = new Entry(e.slot, stack, e.name, lo, hi, old.notified);
				e = merged;
			} else {
				changed = true;
			}
			if (viewing && e.hi <= now) e.notified = true;
			result.add(e);
		}
		if (changed) {
			ENTRIES.clear();
			ENTRIES.addAll(result);
			dirty = true;
		}
	}

	// ---------------------------------------------------------------- the tab list's Forges widget

	/** "1) Refined Mithril: 4h 59m 3s", "2) Refined Diamond: Ready!", "3) EMPTY", "4) LOCKED". */
	private static final Pattern WIDGET_LINE = Pattern.compile("^(\\d+)\\)\\s*(.+?)(?::\\s*(.+))?$");
	private static boolean loggedWidget;

	/**
	 * Hypixel's tab list can show a "Forges:" widget with every forge slot and its time left. When it's there, the
	 * tracker follows it, so it stays right without opening the Forge (icons still come from the menu or the item list).
	 */
	private static void readTab(Minecraft mc) {
		if (mc.getConnection() == null) return;
		List<net.minecraft.client.multiplayer.PlayerInfo> infos = new ArrayList<>(mc.getConnection().getOnlinePlayers());
		infos.sort(java.util.Comparator.comparing(info -> info.getProfile().name()));
		boolean inSection = false;
		List<String> raw = new ArrayList<>();
		List<Entry> found = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (var info : infos) {
			if (info.getTabListDisplayName() == null) continue;
			String line = ChatFormatting.stripFormatting(info.getTabListDisplayName().getString());
			if (line == null) continue;
			line = line.trim();
			if (line.equals("Forges:") || line.equals("Forge:")) {
				inSection = true;
				continue;
			}
			if (!inSection) continue;
			if (line.isEmpty() || line.endsWith(":") && !line.contains(")")) break;
			raw.add(line);
			Matcher m = WIDGET_LINE.matcher(line);
			if (!m.matches()) continue;
			String name = m.group(2).trim();
			String status = m.group(3) == null ? "" : m.group(3).trim();
			if (name.equalsIgnoreCase("EMPTY") || name.equalsIgnoreCase("LOCKED") || name.startsWith("Slot")) continue;
			if (status.isEmpty()) continue;
			long[] w;
			if (DONE.matcher(status).matches() || status.equalsIgnoreCase("done") || status.equalsIgnoreCase("ready")) {
				w = new long[]{Long.MIN_VALUE, now};
			} else {
				w = window(status, now);
				if (w == null) continue;
			}
			found.add(new Entry(Integer.parseInt(m.group(1)), iconFor(name), name, w[0], w[1], false));
		}
		if (!inSection) return;
		if (found.isEmpty() && raw.stream().anyMatch(r -> !r.matches("\\d+\\)\\s*(EMPTY|LOCKED).*")) && !loggedWidget) {
			loggedWidget = true;
			FancyBarsMod.LOGGER.info("[SkyblockGuiUtils] Forges tab widget not understood: {}", raw);
		}
		// the widget only has a few lines: if it shows nothing forging, everything's been claimed
		if (raw.isEmpty()) return;
		merge(found, false);
	}

	private static java.util.Map<String, ItemStack> iconsByName = java.util.Map.of();
	private static int iconsFrom = -1;

	/** The item list's icon for an item name ("Refined Mithril"), or EMPTY. */
	private static ItemStack iconFor(String name) {
		List<dev.fancybars.items.list.ItemRepo.Entry> entries = dev.fancybars.items.list.ItemRepo.entries();
		if (entries.size() != iconsFrom) {
			java.util.Map<String, ItemStack> map = new java.util.HashMap<>();
			for (var e : entries) {
				String plain = ChatFormatting.stripFormatting(e.name());
				if (plain != null) map.putIfAbsent(plain.trim(), e.stack());
			}
			iconsByName = map;
			iconsFrom = entries.size();
		}
		ItemStack stack = iconsByName.get(name);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	static long parseDuration(String text) {
		Matcher m = PART.matcher(text);
		long ms = 0;
		boolean any = false;
		while (m.find()) {
			any = true;
			long n = Long.parseLong(m.group(1));
			char unit = Character.toLowerCase(m.group(2).charAt(0));
			ms += switch (unit) {
				case 'd' -> n * 86_400_000L;
				case 'h' -> n * 3_600_000L;
				case 'm' -> n * 60_000L;
				default -> n * 1000L;
			};
		}
		return any ? ms : -1;
	}

	/** The smallest unit in a time ("4h 59m" → a minute): how much the real time can be over what's shown. */
	static long smallestUnit(String text) {
		Matcher m = PART.matcher(text);
		long unit = 1000;
		boolean any = false;
		while (m.find()) {
			long u = switch (Character.toLowerCase(m.group(2).charAt(0))) {
				case 'd' -> 86_400_000L;
				case 'h' -> 3_600_000L;
				case 'm' -> 60_000L;
				default -> 1000L;
			};
			unit = any ? Math.min(unit, u) : u;
			any = true;
		}
		return unit;
	}

	// ---------------------------------------------------------------- done messages

	private static void checkDone() {
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		List<Entry> done = new ArrayList<>();
		// announced once it's surely done (the latest it could finish has passed)
		for (Entry e : ENTRIES) if (!e.notified && e.hi <= now) done.add(e);
		if (done.isEmpty()) return;
		boolean notify = FancyBarsConfig.get().forgeNotify && mc.player != null;
		// at most one message a minute: anything finishing in between waits and goes in the next one
		if (notify && now - lastMessageAt < 60_000) return;
		for (Entry e : done) e.notified = true;
		dirty = true;
		if (!notify) return;
		lastMessageAt = now;
		MutableComponent items = Component.empty();
		for (int i = 0; i < done.size(); i++) {
			if (i > 0) items.append(Component.literal(i == done.size() - 1 ? " and " : ", ").withStyle(ChatFormatting.GRAY));
			Entry e = done.get(i);
			items.append(e.stack.isEmpty() ? Component.literal(e.name).withStyle(ChatFormatting.WHITE) : e.stack.getHoverName().copy());
		}
		MutableComponent msg = Component.literal("Your ").withStyle(ChatFormatting.GRAY)
				.append(items)
				.append(Component.literal(done.size() == 1 ? " is done forging! " : " are done forging! ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("[Warp to the Forge]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		msg.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/warp forge"))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to /warp forge").withStyle(ChatFormatting.YELLOW))));
		mc.player.sendSystemMessage(Theme.chatPrefix().append(msg));
		mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.4f));
	}

	private static long lastMessageAt;

	// ---------------------------------------------------------------- HUD

	public record Row(ItemStack stack, Component text) {}

	private static final int ROW_H = 12;
	private static final int ICON = 10;
	private static final int PAD = 3;

	public static List<Row> rows() {
		long now = System.currentTimeMillis();
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(ItemStack.EMPTY, Component.literal("Forge").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		List<Entry> sorted = new ArrayList<>(ENTRIES);
		sorted.sort((a, b) -> Long.compare(a.end, b.end));
		for (Entry e : sorted) {
			long left = e.end - now;
			Component time = left <= 0 ? Component.literal("Ready!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
					: Component.literal(duration(left)).withStyle(ChatFormatting.YELLOW);
			MutableComponent name = e.stack.isEmpty() ? Component.literal(e.name).withStyle(ChatFormatting.WHITE) : e.stack.getHoverName().copy();
			rows.add(new Row(e.stack, name.append(Component.literal(" ")).append(time)));
		}
		return rows;
	}

	/** A sample for the HUD editor when nothing is known yet. */
	public static List<Row> shownRows() {
		if (!ENTRIES.isEmpty()) return rows();
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(ItemStack.EMPTY, Component.literal("Forge").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		rows.add(new Row(new ItemStack(Items.PRISMARINE_CRYSTALS), Component.literal("Refined Mithril ").withStyle(ChatFormatting.GREEN).append(Component.literal("4h 12m").withStyle(ChatFormatting.YELLOW))));
		rows.add(new Row(new ItemStack(Items.DIAMOND_PICKAXE), Component.literal("Gemstone Drill LT-522 ").withStyle(ChatFormatting.BLUE).append(Component.literal("Ready!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))));
		return rows;
	}

	private static String duration(long ms) {
		long s = (ms + 999) / 1000;
		long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
		if (d > 0) return d + "d " + h + "h " + m + "m";
		if (h > 0) return h + "h " + m + "m " + sec + "s";
		if (m > 0) return m + "m " + sec + "s";
		return sec + "s";
	}

	public static float[] size(List<Row> rows) {
		Font font = Minecraft.getInstance().font;
		int w = 0;
		for (Row r : rows) w = Math.max(w, font.width(r.text()) + (r.stack().isEmpty() ? 0 : ICON + 2));
		return new float[]{w + PAD * 2, rows.size() * ROW_H + PAD * 2 - 2};
	}

	public static float[] position(int screenW, int screenH, List<Row> rows) {
		FancyBarsConfig c = FancyBarsConfig.get();
		if (c.forgeTrackerX < 0 || c.forgeTrackerY < 0) return new float[]{6, Math.round(screenH * 0.55f)};
		return new float[]{c.forgeTrackerX * screenW, c.forgeTrackerY * screenH};
	}

	public static void renderAt(GuiGraphicsExtractor graphics, float x, float y, float scale, List<Row> rows, float alpha) {
		Font font = Minecraft.getInstance().font;
		int a = Math.round(alpha * 255);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		float[] size = size(rows);
		if (FancyBarsConfig.get().forgeTrackerBackground) Theme.hudPanel(graphics, 0, 0, Math.round(size[0]), Math.round(size[1]), 4, 0.88f, a);
		int ty = PAD;
		for (Row r : rows) {
			int tx = PAD;
			if (!r.stack().isEmpty()) {
				pose.pushMatrix();
				pose.translate(tx, ty - 1);
				pose.scale(ICON / 16f, ICON / 16f);
				graphics.item(r.stack(), 0, 0);
				pose.popMatrix();
				tx += ICON + 2;
			}
			graphics.text(font, r.text(), tx, ty + 1, (a << 24) | 0xFFFFFF, true);
			ty += ROW_H;
		}
		pose.popMatrix();
	}

	// ---------------------------------------------------------------- saving (per profile, next to Backpack Preview's)

	private static final String FILE = "forge.nbt";

	private static void save(Path dir) {
		RegistryOps<Tag> ops = BackpackPreview.ops();
		if (ops == null) return;
		dirty = false;
		ListTag list = new ListTag();
		for (Entry e : ENTRIES) {
			CompoundTag t = new CompoundTag();
			t.putInt("slot", e.slot);
			t.putString("name", e.name);
			t.putLong("end", e.end);
			t.putLong("lo", e.lo);
			t.putLong("hi", e.hi);
			t.putBoolean("notified", e.notified);
			BackpackPreview.stackCodec().encodeStart(ops, e.stack).result().ifPresent(item -> t.put("item", item));
			list.add(t);
		}
		CompletableFuture.runAsync(() -> {
			try {
				CompoundTag tag = new CompoundTag();
				tag.put("entries", list);
				Files.createDirectories(dir);
				NbtIo.write(tag, dir.resolve(FILE));
			} catch (Exception ex) {
				FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Couldn't save the forge", ex);
			}
		}, FancyBarsMod.VIRTUAL_THREAD_EXECUTOR);
	}

	private static void load(Path dir) {
		ENTRIES.clear();
		RegistryOps<Tag> ops = BackpackPreview.ops();
		if (ops == null) return;
		Path file = dir.resolve(FILE);
		if (!Files.isRegularFile(file)) return;
		try {
			CompoundTag tag = NbtIo.read(file);
			if (tag == null) return;
			for (Tag t : tag.getListOrEmpty("entries")) {
				if (!(t instanceof CompoundTag c)) continue;
				ItemStack stack = c.get("item") == null ? ItemStack.EMPTY
						: BackpackPreview.stackCodec().parse(ops, c.get("item")).result().orElse(ItemStack.EMPTY);
				long end = c.getLongOr("end", 0);
				ENTRIES.add(new Entry(c.getIntOr("slot", -1), stack, c.getStringOr("name", "?"), c.getLongOr("lo", end), c.getLongOr("hi", end),
						c.getBooleanOr("notified", true)));
			}
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Couldn't read the saved forge {}", file.toString().toLowerCase(Locale.ROOT), e);
		}
	}
}
