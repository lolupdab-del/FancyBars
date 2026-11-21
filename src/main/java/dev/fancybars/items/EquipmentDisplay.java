package dev.fancybars.items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.ContainerScreenAccessor;
import dev.fancybars.storage.BackpackPreview;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Equipment Display: your SkyBlock equipment (necklace, cloak, belt, gloves / bracelet) in four slots to the left of
 * your inventory, like the armor column. Hypixel only sends what you're wearing while its "Your Equipment and Stats"
 * menu is open, so it's read from there (open /stats once) and remembered per profile. Clicking a slot opens /stats.
 */
public final class EquipmentDisplay {
	private EquipmentDisplay() {}

	/** Top to bottom, the same order as Hypixel's menu. */
	private static final String[] KINDS = {"NECKLACE", "CLOAK", "BELT", "GLOVES"};
	private static final String[] KIND_NAMES = {"Necklace", "Cloak", "Belt", "Gloves / Bracelet"};
	/** The rarity line at the bottom of the lore, e.g. "LEGENDARY NECKLACE" or "a MYTHIC GLOVES a" when recombobulated. */
	private static final Pattern TYPE = Pattern.compile("^(?:[a-z] )?(?:SHINY )?(?:VERY SPECIAL|UNCOMMON|COMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|ULTIMATE|ADMIN)(?: DUNGEON)? (NECKLACE|CLOAK|BELT|GLOVES|BRACELET)(?: [a-z])?$");
	/** The /stats menu's title (and its older name). */
	private static final java.util.Set<String> MENU_TITLES = java.util.Set.of("Stats & Equipment", "Your Equipment and Stats");
	/** Where the menu shows what you're wearing: necklace, cloak, belt, gloves / bracelet. */
	private static final java.util.Set<Integer> EQUIPMENT_SLOTS = java.util.Set.of(10, 19, 28, 37);
	private static final int SLOT = 16;
	private static final int GAP = 2;

	private static final ItemStack[] EQUIPPED = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
	/** Whether {@link #EQUIPPED} has been read (from the menu or the save) for the current profile. */
	private static boolean known;
	private static @Nullable Path loadedFrom;
	private static boolean dirty;

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof InventoryScreen inventory) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> draw(inventory, graphics, mouseX, mouseY));
				ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
					if (click.button() != 0 || hoveredSlot(inventory, click.x(), click.y()) < 0) return true;
					openStats();
					return false;
				});
			} else if (screen instanceof AbstractContainerScreen<?> container) {
				// the menu's items arrive after it opens: read them every frame it's open (cheap: 54 slots)
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> readMenu(container));
			}
		});
		dev.fancybars.util.Perf.onTick("Tick EquipmentDisplay", mc -> {
			if (mc.player == null || !SkyblockLocation.isOnSkyblock()) return;
			Path dir = BackpackPreview.profileDir();
			if (dir != null && !dir.equals(loadedFrom)) {
				loadedFrom = dir;
				if (!dirty) load(dir);
			}
			if (dirty && dir != null && mc.player.tickCount % 40 == 0) save(dir);
		});
	}

	public static boolean enabled() {
		return FancyBarsConfig.get().equipmentDisplayEnabled && SkyblockLocation.isOnSkyblock();
	}

	// ---------------------------------------------------------------- reading the menu

	private static void readMenu(AbstractContainerScreen<?> screen) {
		String title = ChatFormatting.stripFormatting(screen.getTitle().getString());
		// only the /stats menu ("Stats & Equipment"): other menus with "Equipment" in their name (like /eq) list
		// equipment you own but aren't wearing
		if (title == null || !MENU_TITLES.contains(title.trim())) return;
		ItemStack[] found = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
		boolean anyItems = false;
		for (Slot slot : screen.getMenu().slots) {
			// only the menu itself: equipment sitting in your own inventory isn't being worn
			if (slot.container instanceof Inventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			anyItems = true;
			// the equipment column: necklace, cloak, belt, gloves (the other slots are armor, pet and stat icons)
			if (!EQUIPMENT_SLOTS.contains(slot.index)) continue;
			int kind = kindOf(stack);
			if (kind >= 0 && found[kind].isEmpty()) found[kind] = stack.copy();
		}
		if (!anyItems) return; // still loading
		boolean changed = false;
		for (int i = 0; i < 4; i++) {
			if (!ItemStack.matches(found[i], EQUIPPED[i])) {
				EQUIPPED[i] = found[i];
				changed = true;
			}
		}
		known = true;
		if (changed) dirty = true;
	}

	/** 0-3 for necklace, cloak, belt, gloves / bracelet, or -1. */
	private static int kindOf(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return -1;
		List<Component> lines = lore.lines();
		for (int i = lines.size() - 1; i >= 0; i--) {
			String line = ChatFormatting.stripFormatting(lines.get(i).getString());
			if (line == null || line.isBlank()) continue;
			Matcher m = TYPE.matcher(line.trim());
			if (!m.matches()) continue;
			String type = m.group(1);
			if (type.equals("BRACELET")) return 3;
			for (int k = 0; k < KINDS.length; k++) if (KINDS[k].equals(type)) return k;
		}
		return -1;
	}

	// ---------------------------------------------------------------- drawing

	/** Top-left of slot {@code i}: a column just left of the inventory, lined up with the armor slots. */
	private static int[] slotPos(InventoryScreen screen, int i) {
		ContainerScreenAccessor a = (ContainerScreenAccessor) screen;
		// same size and spacing as the armor slots (16 px boxes, 18 px apart, first one 8 px down)
		return new int[]{a.fancybars$getLeftPos() - SLOT - 3, a.fancybars$getTopPos() + 8 + i * (SLOT + GAP)};
	}

	/** Shown on SkyBlock, and not while the recipe book is open (it sits exactly where the slots go). */
	private static boolean shown(InventoryScreen screen) {
		if (!enabled()) return false;
		try {
			return !((dev.fancybars.mixin.RecipeBookScreenAccessor) screen).fancybars$getRecipeBook().isVisible();
		} catch (RuntimeException | LinkageError e) {
			return true;
		}
	}

	private static int hoveredSlot(InventoryScreen screen, double mouseX, double mouseY) {
		if (!shown(screen)) return -1;
		for (int i = 0; i < 4; i++) {
			int[] p = slotPos(screen, i);
			if (mouseX >= p[0] && mouseX < p[0] + SLOT && mouseY >= p[1] && mouseY < p[1] + SLOT) return i;
		}
		return -1;
	}

	private static void draw(InventoryScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!shown(screen)) return;
		int hovered = hoveredSlot(screen, mouseX, mouseY);
		Font font = Minecraft.getInstance().font;
		for (int i = 0; i < 4; i++) {
			// just the boxes, like the armor slots: no panel or outline around them
			int[] p = slotPos(screen, i);
			Theme.roundedRect(graphics, p[0], p[1], SLOT, SLOT, 3, i == hovered ? 0xA0303036 : 0xA0101014);
			ItemStack stack = EQUIPPED[i];
			if (!stack.isEmpty()) {
				RarityBackground.draw(graphics, stack, p[0], p[1], false);
				graphics.item(stack, p[0], p[1]);
				graphics.itemDecorations(font, stack, p[0], p[1]);
			}
		}
		if (hovered >= 0) {
			ItemStack stack = EQUIPPED[hovered];
			if (!stack.isEmpty()) {
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
			} else {
				Component title = Component.literal(KIND_NAMES[hovered]).withStyle(ChatFormatting.GRAY);
				Component hint = Component.literal(known ? "Nothing equipped. Click to open /stats" : "Click to open /stats once so it can be shown here")
						.withStyle(ChatFormatting.DARK_GRAY);
				graphics.setTooltipForNextFrame(font, List.of(title, hint), java.util.Optional.empty(), mouseX, mouseY);
			}
		}
	}

	private static void openStats() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null) mc.getConnection().sendCommand("stats");
	}

	// ---------------------------------------------------------------- saving (per profile, next to Backpack Preview's)

	private static final String FILE = "equipment.nbt";

	private static void save(Path dir) {
		RegistryOps<Tag> ops = BackpackPreview.ops();
		if (ops == null) return;
		dirty = false;
		List<ItemStack> items = List.of(EQUIPPED[0].copy(), EQUIPPED[1].copy(), EQUIPPED[2].copy(), EQUIPPED[3].copy());
		Codec<List<ItemStack>> codec = BackpackPreview.stackCodec().listOf();
		CompletableFuture.runAsync(() -> {
			try {
				CompoundTag tag = new CompoundTag();
				tag.put("items", codec.encodeStart(ops, items).getOrThrow());
				Files.createDirectories(dir);
				NbtIo.write(tag, dir.resolve(FILE));
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Couldn't save equipment", e);
			}
		}, FancyBarsMod.VIRTUAL_THREAD_EXECUTOR);
	}

	private static void load(Path dir) {
		RegistryOps<Tag> ops = BackpackPreview.ops();
		for (int i = 0; i < 4; i++) EQUIPPED[i] = ItemStack.EMPTY;
		known = false;
		if (ops == null) return;
		Path file = dir.resolve(FILE);
		if (!Files.isRegularFile(file)) return;
		try {
			CompoundTag tag = NbtIo.read(file);
			if (tag == null || tag.get("items") == null) return;
			List<ItemStack> items = BackpackPreview.stackCodec().listOf().parse(ops, tag.get("items")).getOrThrow();
			for (int i = 0; i < Math.min(4, items.size()); i++) EQUIPPED[i] = items.get(i);
			known = true;
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SkyblockGuiUtils] Couldn't read saved equipment {}", file.toString().toLowerCase(Locale.ROOT), e);
		}
	}
}
