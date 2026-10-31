package dev.fancybars.items;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

import dev.fancybars.bars.EditBarColorPopup;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.mixin.ModelManagerAccessor;
import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.gui.ModernButton;
import dev.fancybars.util.gui.ModernSlider;
import dev.fancybars.util.gui.ModernToggle;
import dev.fancybars.util.gui.SearchField;
import dev.fancybars.util.gui.Theme;

/**
 * Item customisation, everything Skyblocker's customiser has: pick one of your items on the left, then change its
 * name, glint and model (any vanilla or SkyBlock resource pack model) on the Item tab, or its dye (static, SkyBlock
 * dyes, animated), trim, worn armour look and head texture (SkyBlock heads, animated heads) on the Armor tab.
 */
public class ItemChangerScreen extends Screen {
	private static final int SLOT = 18;
	private static final int COLS = 9;
	private static final int[] SLOT_ORDER = buildOrder();
	private static final String[] ARMOR_LOOKS = {"", "minecraft:leather", "minecraft:chainmail", "minecraft:iron", "minecraft:gold", "minecraft:diamond",
			"minecraft:netherite", "minecraft:copper", "minecraft:turtle_scute"};
	private static final ChatFormatting[] COLORS = {ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
			ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY, ChatFormatting.DARK_GRAY, ChatFormatting.BLUE,
			ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE};

	public enum Tab {ITEM, ARMOR}

	private final @Nullable Screen parent;
	private int selectedSlot;
	private Tab tab;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int gridX;
	private int gridY;
	private int rightX;
	private int rightW;
	private @Nullable SearchField nameField;
	private int gradientFrom = 0xFF8FC8;
	private int gradientTo = 0x7CC8FF;
	private final List<int[]> colorButtons = new ArrayList<>(); // {x, y, size, index}
	private final List<int[]> keyframeChips = new ArrayList<>(); // {x, y, size, index}

	public ItemChangerScreen(@Nullable Screen parent) {
		this(parent, -1, Tab.ITEM);
	}

	public ItemChangerScreen(@Nullable Screen parent, int slot, Tab tab) {
		super(Component.translatable("fancybars.items.title"));
		this.parent = parent;
		this.selectedSlot = slot;
		this.tab = tab;
	}

	private static int[] buildOrder() {
		int[] order = new int[41];
		int i = 0;
		for (int s = 9; s < 36; s++) order[i++] = s;
		for (int s = 0; s < 9; s++) order[i++] = s;
		for (int s = 39; s >= 36; s--) order[i++] = s;
		order[i] = 40;
		return order;
	}

	private void reopen(int slot, Tab newTab) {
		ItemChangerScreen next = new ItemChangerScreen(parent, slot, newTab);
		next.gradientFrom = gradientFrom;
		next.gradientTo = gradientTo;
		minecraft.gui.setScreen(next);
	}

	private void refresh() {
		Customization.clearCaches();
		FancyBarsConfig.save();
	}

	private ItemStack stack(int slot) {
		return minecraft.player == null || slot < 0 ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(slot);
	}

	private ItemStack selected() {
		return stack(selectedSlot);
	}

	private String uuid() {
		return ItemProtection.getUuid(selected());
	}

	private FancyBarsConfig config() {
		return FancyBarsConfig.get();
	}

	// ================================================================ Layout / widgets

	@Override
	protected void init() {
		if (selectedSlot < 0 && minecraft.player != null) {
			// start with the held item, else the first customisable one
			if (!ItemProtection.getUuid(minecraft.player.getMainHandItem()).isEmpty()) selectedSlot = minecraft.player.getInventory().getSelectedSlot();
			else for (int slot : SLOT_ORDER) if (!ItemProtection.getUuid(stack(slot)).isEmpty()) {
				selectedSlot = slot;
				break;
			}
		}
		pw = Math.min(width - 12, 470);
		ph = Math.min(height - 12, 290);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		gridX = px + 12;
		gridY = py + 42;
		rightX = gridX + COLS * SLOT + 14;
		rightW = px + pw - 12 - rightX;
		colorButtons.clear();
		keyframeChips.clear();

		// tabs
		int tabW = 70;
		addTab(Tab.ITEM, rightX, tabW);
		addTab(Tab.ARMOR, rightX + tabW + 4, tabW);

		ModernButton done = new ModernButton(70, 18, Component.translatable("gui.done"), ModernButton.Style.PRIMARY, this::onClose);
		done.setPosition(px + pw - 12 - 70, py + ph - 26);
		addRenderableWidget(done);
		ModernButton resetAll = new ModernButton(90, 18, Component.translatable("fancybars.items.resetItem"), () -> {
			resetItem(uuid());
			reopen(selectedSlot, tab);
		});
		resetAll.setPosition(done.getX() - 94, py + ph - 26);
		addRenderableWidget(resetAll);

		if (uuid().isEmpty()) return;
		int y = py + 42;
		if (tab == Tab.ITEM) initItemTab(y);
		else initArmorTab(y);
	}

	private void addTab(Tab t, int x, int w) {
		ModernButton button = new ModernButton(w, 18, Component.translatable("fancybars.items.tab." + t.name().toLowerCase(Locale.ROOT)),
				t == tab ? ModernButton.Style.PRIMARY : ModernButton.Style.SECONDARY, () -> reopen(selectedSlot, t));
		button.setPosition(x, py + 18);
		addRenderableWidget(button);
	}

	private ModernButton button(int x, int y, int w, String key, Runnable action) {
		ModernButton b = new ModernButton(w, 16, Component.translatable(key), action);
		b.setPosition(x, y);
		return addRenderableWidget(b);
	}

	private void initItemTab(int y) {
		String uuid = uuid();
		// Name
		nameField = new SearchField(rightW, 16, config().customItemNames.getOrDefault(uuid, ""), Component.translatable("fancybars.items.namePlaceholder"), text -> {
			if (text.isBlank()) config().customItemNames.remove(uuid);
			else config().customItemNames.put(uuid, text);
			refresh();
		}).maxLength(400);
		nameField.setPosition(rightX, y + 12);
		addRenderableWidget(nameField);
		// colour + format buttons (drawn in render, clicks handled in mouseClicked)
		int cx = rightX;
		int cy = y + 32;
		for (int i = 0; i < COLORS.length; i++) colorButtons.add(new int[]{cx + i * 12, cy, 10, i});
		String[] formats = {"l", "o", "n", "m", "k", "r"};
		for (int i = 0; i < formats.length; i++) colorButtons.add(new int[]{cx + i * 16, cy + 13, 14, 100 + i});
		// gradient
		int gy = cy + 30;
		colorButtons.add(new int[]{rightX + 54, gy, 12, 200});
		colorButtons.add(new int[]{rightX + 70, gy, 12, 201});
		button(rightX + 88, gy - 2, 90, "fancybars.items.applyGradient", () -> {
			if (nameField == null) return;
			String text = nameField.getText().isBlank() ? stripCodes(selected().getHoverName().getString()) : nameField.getText();
			String formats2 = text.contains("&l") ? "&l" : "";
			nameField.setText(Customization.gradient(text, gradientFrom, gradientTo, formats2));
		});

		// Glint
		int ry = gy + 22;
		Boolean glint = config().customGlint.get(uuid);
		ModernButton glintButton = new ModernButton(rightW, 18, Component.translatable("fancybars.items.glint",
				Component.translatable(glint == null ? "fancybars.items.default" : glint ? "options.on" : "options.off")), () -> {
			Boolean current = config().customGlint.get(uuid);
			if (current == null) config().customGlint.put(uuid, true);
			else if (current) config().customGlint.put(uuid, false);
			else config().customGlint.remove(uuid);
			refresh();
			reopen(selectedSlot, tab);
		});
		glintButton.setPosition(rightX, ry);
		addRenderableWidget(glintButton);

		// Model
		int my = ry + 34;
		int half = (rightW - 4) / 2;
		button(rightX, my, half, "fancybars.items.pickModel", () -> minecraft.gui.setScreen(new GridPickerScreen<>(Component.translatable("fancybars.items.pickModel"), this, modelEntries(), id -> {
			config().customItemModels.put(uuid, id.toString());
			refresh();
		})));
		button(rightX + half + 4, my, rightW - half - 4, "fancybars.items.resetModel", () -> {
			config().customItemModels.remove(uuid);
			refresh();
		});
	}

	private void initArmorTab(int y) {
		String uuid = uuid();
		ItemStack stack = selected();
		int half = (rightW - 4) / 2;
		boolean dyeable = stack.is(ItemTags.CAULDRON_CAN_REMOVE_DYE);
		boolean head = stack.is(Items.PLAYER_HEAD);
		boolean armor = stack.has(DataComponents.EQUIPPABLE) && !head;

		int ry = y + 12;
		if (dyeable) {
			// static dye
			colorButtons.add(new int[]{rightX, ry + 2, 12, 300});
			button(rightX + 18, ry, 96, "fancybars.items.skyblockDyes", () -> minecraft.gui.setScreen(new GridPickerScreen<>(Component.translatable("fancybars.items.skyblockDyes"), this, dyeEntries(), pick -> {
				if (pick instanceof Integer color) {
					config().customAnimatedDyes.remove(uuid);
					config().customDyeColors.put(uuid, color);
				} else if (pick instanceof List<?> colors) {
					FancyBarsConfig.AnimatedDye dye = new FancyBarsConfig.AnimatedDye();
					for (Object c : colors) dye.colors.add((Integer) c);
					dye.duration = Math.max(0.5f, colors.size() / 20f);
					dye.cycleBack = false;
					config().customAnimatedDyes.put(uuid, dye);
				}
				refresh();
			})));
			button(rightX + 118, ry, rightW - 118, "fancybars.items.clearDye", () -> {
				config().customDyeColors.remove(uuid);
				config().customAnimatedDyes.remove(uuid);
				refresh();
				reopen(selectedSlot, tab);
			});
			// animated dye keyframes
			ry += 34;
			FancyBarsConfig.AnimatedDye dye = config().customAnimatedDyes.get(uuid);
			List<Integer> colors = dye != null ? dye.colors : List.of();
			for (int i = 0; i < Math.min(colors.size(), 8); i++) keyframeChips.add(new int[]{rightX + i * 16, ry + 2, 12, i});
			int addX = rightX + Math.min(colors.size(), 8) * 16;
			if (colors.size() < 8) colorButtons.add(new int[]{addX, ry + 2, 12, 400});
			ModernToggle cycle = new ModernToggle(ModernToggle.SWITCH_WIDTH, ModernToggle.SWITCH_HEIGHT, null, dye == null || dye.cycleBack, value -> {
				FancyBarsConfig.AnimatedDye d = config().customAnimatedDyes.get(uuid);
				if (d != null) {
					d.cycleBack = value;
					refresh();
				}
			});
			cycle.setPosition(rightX + rightW - ModernToggle.SWITCH_WIDTH, ry);
			addRenderableWidget(cycle);
			ry += 20;
			ModernSlider duration = new ModernSlider(rightW - 80, 14, 0.1f, 10, 0.1f, dye != null ? dye.duration : 2, value -> {
				FancyBarsConfig.AnimatedDye d = config().customAnimatedDyes.get(uuid);
				if (d != null) d.duration = value;
			}, this::refresh);
			duration.setPosition(rightX + 80, ry);
			addRenderableWidget(duration);
			ry += 16;
			ModernSlider delay = new ModernSlider(rightW - 80, 14, 0, 5, 0.1f, dye != null ? dye.delay : 0, value -> {
				FancyBarsConfig.AnimatedDye d = config().customAnimatedDyes.get(uuid);
				if (d != null) d.delay = value;
			}, this::refresh);
			delay.setPosition(rightX + 80, ry);
			addRenderableWidget(delay);
			ry += 22;
		}
		if (armor) {
			// trim
			String[] trim = config().customArmorTrims.get(uuid);
			List<String> materials = Customization.trimIds(true);
			List<String> patterns = Customization.trimIds(false);
			addRenderableWidget(trimDropdown(rightX, ry, half, uuid, materials, patterns, true));
			addRenderableWidget(trimDropdown(rightX + half + 4, ry, rightW - half - 4, uuid, materials, patterns, false));
			ry += 18;
			button(rightX, ry, half, "fancybars.items.clearTrim", () -> {
				config().customArmorTrims.remove(uuid);
				refresh();
			});
			// worn look
			String look = config().customArmorModel.getOrDefault(uuid, "");
			List<Component> lookOptions = new java.util.ArrayList<>();
			for (String l : ARMOR_LOOKS) lookOptions.add(l.isEmpty() ? Component.translatable("fancybars.items.default") : Component.literal(Customization.prettyId(Identifier.parse(l).getPath())));
			dev.fancybars.util.gui.Dropdown lookButton = new dev.fancybars.util.gui.Dropdown(rightW - half - 4, 16, Component.translatable("fancybars.items.look"), lookOptions, () -> {
				String current = config().customArmorModel.getOrDefault(uuid, "");
				for (int i = 0; i < ARMOR_LOOKS.length; i++) if (ARMOR_LOOKS[i].equals(current)) return i;
				return 0;
			}, i -> {
				String next = ARMOR_LOOKS[i];
				if (next.isEmpty()) config().customArmorModel.remove(uuid);
				else config().customArmorModel.put(uuid, next);
				refresh();
			});
			lookButton.setPosition(rightX + half + 4, ry);
			addRenderableWidget(lookButton);
			ry += 26;
			if (trim != null) ry += 0;
		}
		if (head) {
			button(rightX, ry, half, "fancybars.items.pickHead", () -> minecraft.gui.setScreen(new GridPickerScreen<>(Component.translatable("fancybars.items.pickHead"), this, headEntries(), hash -> {
				config().customAnimatedHelmetTextures.remove(uuid);
				config().customHelmetTextures.put(uuid, hash);
				refresh();
			})));
			button(rightX + half + 4, ry, rightW - half - 4, "fancybars.items.pickAnimatedHead", () -> minecraft.gui.setScreen(new GridPickerScreen<>(Component.translatable("fancybars.items.pickAnimatedHead"), this, animatedHeadEntries(), id -> {
				config().customHelmetTextures.remove(uuid);
				config().customAnimatedHelmetTextures.put(uuid, id);
				refresh();
			})));
			ry += 18;
			button(rightX, ry, half, "fancybars.items.clearHead", () -> {
				config().customHelmetTextures.remove(uuid);
				config().customAnimatedHelmetTextures.remove(uuid);
				refresh();
			});
		}
	}

	private dev.fancybars.util.gui.Dropdown trimDropdown(int x, int y, int w, String uuid, List<String> materials, List<String> patterns, boolean material) {
		List<String> ids = material ? materials : patterns;
		List<Component> options = new java.util.ArrayList<>();
		options.add(Component.translatable("fancybars.items.none"));
		for (String id : ids) options.add(Component.literal(Customization.prettyId(Identifier.parse(id).getPath())));
		dev.fancybars.util.gui.Dropdown d = new dev.fancybars.util.gui.Dropdown(w, 16, Component.translatable(material ? "fancybars.items.material" : "fancybars.items.pattern"), options, () -> {
			String[] trim = config().customArmorTrims.get(uuid);
			return trim == null ? 0 : ids.indexOf(trim[material ? 0 : 1]) + 1;
		}, i -> {
			if (i == 0) config().customArmorTrims.remove(uuid);
			else if (!materials.isEmpty() && !patterns.isEmpty()) {
				String[] trim = config().customArmorTrims.getOrDefault(uuid, new String[]{materials.getFirst(), patterns.getFirst()}).clone();
				trim[material ? 0 : 1] = ids.get(i - 1);
				config().customArmorTrims.put(uuid, trim);
			}
			refresh();
		});
		d.setPosition(x, y);
		return d;
	}

	private void cycleTrim(String uuid, List<String> materials, List<String> patterns, boolean material) {
		if (materials.isEmpty() || patterns.isEmpty()) return;
		String[] trim = config().customArmorTrims.getOrDefault(uuid, new String[]{materials.getFirst(), patterns.getFirst()});
		if (material) trim[0] = materials.get((Math.max(0, materials.indexOf(trim[0])) + 1) % materials.size());
		else trim[1] = patterns.get((Math.max(0, patterns.indexOf(trim[1])) + 1) % patterns.size());
		if (!config().customArmorTrims.containsKey(uuid)) {
			trim[0] = material ? trim[0] : materials.getFirst();
		}
		config().customArmorTrims.put(uuid, trim);
		refresh();
	}

	private static void resetItem(String uuid) {
		if (uuid.isEmpty()) return;
		FancyBarsConfig c = FancyBarsConfig.get();
		c.customItemNames.remove(uuid);
		c.customGlint.remove(uuid);
		c.customItemModels.remove(uuid);
		c.customDyeColors.remove(uuid);
		c.customAnimatedDyes.remove(uuid);
		c.customArmorTrims.remove(uuid);
		c.customArmorModel.remove(uuid);
		c.customHelmetTextures.remove(uuid);
		c.customAnimatedHelmetTextures.remove(uuid);
		Customization.clearCaches();
		FancyBarsConfig.save();
	}

	public static boolean isCustomized(String uuid) {
		FancyBarsConfig c = FancyBarsConfig.get();
		return c.customItemNames.containsKey(uuid) || c.customGlint.containsKey(uuid) || c.customItemModels.containsKey(uuid)
				|| c.customDyeColors.containsKey(uuid) || c.customAnimatedDyes.containsKey(uuid) || c.customArmorTrims.containsKey(uuid)
				|| c.customArmorModel.containsKey(uuid) || c.customHelmetTextures.containsKey(uuid) || c.customAnimatedHelmetTextures.containsKey(uuid);
	}

	private static String stripCodes(String s) {
		String stripped = ChatFormatting.stripFormatting(s);
		return stripped == null ? s : stripped;
	}

	// ================================================================ Picker entries

	private List<GridPickerScreen.Entry<Identifier>> modelEntries() {
		List<GridPickerScreen.Entry<Identifier>> list = new ArrayList<>();
		Map<Identifier, ?> models = ((ModelManagerAccessor) minecraft.getModelManager()).fancybars$getBakedItemStackModels();
		List<Identifier> ids = new ArrayList<>(models.keySet());
		ids.sort((a, b) -> {
			// SkyBlock (resource pack) models first
			boolean sa = a.getNamespace().equals("minecraft");
			boolean sb = b.getNamespace().equals("minecraft");
			return sa == sb ? a.toString().compareTo(b.toString()) : sa ? 1 : -1;
		});
		for (Identifier id : ids) {
			ItemStack icon = new ItemStack(Items.PAPER.builtInRegistryHolder(), 1, DataComponentPatch.builder().set(DataComponents.ITEM_MODEL, id).build());
			list.add(new GridPickerScreen.Entry<>(icon, Component.literal(id.toString()), id.toString().replace('_', ' ').toLowerCase(Locale.ROOT) + " " + id, id));
		}
		return list;
	}

	private static List<GridPickerScreen.Entry<String>> headEntries() {
		List<GridPickerScreen.Entry<String>> list = new ArrayList<>();
		for (Customization.Head head : Customization.HEADS) {
			list.add(new GridPickerScreen.Entry<>(Customization.headStack(head.hash()), Component.literal(head.name()),
					(head.name() + " " + head.id()).toLowerCase(Locale.ROOT), head.hash()));
		}
		return list;
	}

	private static List<GridPickerScreen.Entry<String>> animatedHeadEntries() {
		List<GridPickerScreen.Entry<String>> list = new ArrayList<>();
		for (Customization.AnimatedHead head : Customization.ANIMATED_HEADS.values()) {
			String name = Customization.prettyId(head.id());
			list.add(new GridPickerScreen.Entry<>(Customization.headStack(head.frames().getFirst()), Component.literal(name), name.toLowerCase(Locale.ROOT), head.id()));
		}
		return list;
	}

	private static List<GridPickerScreen.Entry<Object>> dyeEntries() {
		List<GridPickerScreen.Entry<Object>> list = new ArrayList<>();
		for (Map.Entry<String, Integer> e : Customization.REPO_DYES.entrySet()) {
			list.add(new GridPickerScreen.Entry<>(dyed(e.getValue()), Component.literal(Customization.prettyId(e.getKey()) + " Dye").withColor(e.getValue()),
					e.getKey().toLowerCase(Locale.ROOT), e.getValue()));
		}
		for (Map.Entry<String, List<Integer>> e : Customization.REPO_ANIMATED_DYES.entrySet()) {
			list.add(new GridPickerScreen.Entry<>(dyed(e.getValue().getFirst()), Component.literal(Customization.prettyId(e.getKey()) + " Dye (animated)").withColor(e.getValue().getFirst()),
					e.getKey().toLowerCase(Locale.ROOT) + " animated", e.getValue()));
		}
		return list;
	}

	private static ItemStack dyed(int rgb) {
		ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return stack;
	}

	// ================================================================ Rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.backdrop(graphics, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Theme.panel(graphics, px, py, pw, ph);
		Theme.titleInBox(graphics, font, Component.translatable("fancybars.items.title"), px + 12, py + 2, 18, Theme.TEXT);
		Theme.textInBox(graphics, font, Component.translatable("fancybars.items.yourItems"), gridX, py + 24, 14, Theme.TEXT_MUTED);

		// your items
		for (int i = 0; i < SLOT_ORDER.length; i++) {
			int slot = SLOT_ORDER[i];
			int sx = gridX + i % COLS * SLOT;
			int sy = gridY + i / COLS * SLOT + (i >= 36 ? 4 : i >= 27 ? 2 : 0);
			ItemStack stack = stack(slot);
			String uuid = ItemProtection.getUuid(stack);
			boolean usable = !uuid.isEmpty();
			boolean sel = slot == selectedSlot;
			boolean hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
			Theme.roundedRect(graphics, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, sel ? Theme.SELECTED : hovered && usable ? Theme.SURFACE_HOVER : usable ? Theme.SURFACE : Theme.SURFACE_DISABLED);
			if (sel) Theme.roundedOutline(graphics, sx, sy, SLOT, SLOT, 3, Theme.ACCENT);
			if (!stack.isEmpty()) {
				graphics.item(stack, sx + 1, sy + 1);
				if (usable && isCustomized(uuid)) {
					graphics.nextStratum();
					Theme.circle(graphics, sx + SLOT - 6, sy + 1, 5, Theme.ACCENT);
				}
				if (hovered) {
					graphics.setTooltipForNextFrame(font, usable ? stack.getHoverName() : Component.translatable("fancybars.items.noUuid"), mouseX, mouseY);
					if (usable) graphics.requestCursor(CursorTypes.POINTING_HAND);
				}
			}
		}

		// big preview of the selected item
		ItemStack sel = selected();
		int previewY = gridY + 5 * SLOT + 12;
		if (!uuid().isEmpty()) {
			Theme.roundedRect(graphics, gridX, previewY, COLS * SLOT, py + ph - 34 - previewY, 5, 0xCC141622);
			org.joml.Matrix3x2fStack pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(gridX + COLS * SLOT / 2f - 24, previewY + 6);
			pose.scale(3, 3);
			graphics.item(sel, 0, 0);
			pose.popMatrix();
			Component name = sel.getHoverName();
			float nameScale = Math.min(1, (COLS * SLOT - 8f) / Math.max(1, font.width(name)));
			pose.pushMatrix();
			pose.translate(gridX + COLS * SLOT / 2f, previewY + 60);
			pose.scale(nameScale, nameScale);
			graphics.text(font, name, -font.width(name) / 2, 0, 0xFFFFFFFF, true);
			pose.popMatrix();
		} else {
			Theme.textInBox(graphics, font, Component.translatable("fancybars.items.pick"), gridX, previewY, 14, Theme.TEXT_MUTED);
		}

		if (!uuid().isEmpty()) {
			if (tab == Tab.ITEM) renderItemTab(graphics, mouseX, mouseY);
			else renderArmorTab(graphics, mouseX, mouseY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		dev.fancybars.util.gui.Dropdown.renderOverlay(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (dev.fancybars.util.gui.Dropdown.scrollOverlay(mouseX, mouseY, vertical)) return true;
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	private void label(GuiGraphicsExtractor graphics, String key, int y, Object... args) {
		Theme.boldInBox(graphics, font, Component.translatable(key, args), rightX, y, 12, Theme.TEXT_SOFT);
	}

	private void renderItemTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = py + 42;
		label(graphics, "fancybars.items.name", y - 2);
		renderColorButtons(graphics, mouseX, mouseY);
		Theme.textInBox(graphics, font, Component.translatable("fancybars.items.gradient"), rightX, y + 62, 12, Theme.TEXT_MUTED);
		String uuid = uuid();
		String model = config().customItemModels.get(uuid);
		int my = y + 32 + 30 + 22 + 22;
		label(graphics, "fancybars.items.model", my);
		Component current = model == null ? Component.translatable("fancybars.items.default") : Component.literal(model);
		Theme.textInBox(graphics, font, current, rightX + Theme.boldWidth(font, Component.translatable("fancybars.items.model")) + 8, my, 12, Theme.TEXT_MUTED);
	}

	private void renderArmorTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = py + 42;
		ItemStack stack = selected();
		boolean dyeable = stack.is(ItemTags.CAULDRON_CAN_REMOVE_DYE);
		boolean head = stack.is(Items.PLAYER_HEAD);
		boolean armor = stack.has(DataComponents.EQUIPPABLE) && !head;
		if (!dyeable && !head && !armor) {
			Theme.textInBox(graphics, font, Component.translatable("fancybars.items.notArmor"), rightX, y + 4, 14, Theme.TEXT_MUTED);
			return;
		}
		int ry = y;
		if (dyeable) {
			label(graphics, "fancybars.items.dye", ry - 2);
			ry += 34;
			label(graphics, "fancybars.items.animatedDye", ry - 4);
			Theme.textInBox(graphics, font, Component.translatable("fancybars.items.cycleBack"), rightX + rightW - ModernToggle.SWITCH_WIDTH - 70, ry - 4, 12, Theme.TEXT_MUTED);
			ry += 20;
			Theme.textInBox(graphics, font, Component.translatable("fancybars.items.duration",
					String.format("%.1f", config().customAnimatedDyes.containsKey(uuid()) ? config().customAnimatedDyes.get(uuid()).duration : 2f)), rightX, ry, 14, Theme.TEXT_MUTED);
			ry += 16;
			Theme.textInBox(graphics, font, Component.translatable("fancybars.items.delay",
					String.format("%.1f", config().customAnimatedDyes.containsKey(uuid()) ? config().customAnimatedDyes.get(uuid()).delay : 0f)), rightX, ry, 14, Theme.TEXT_MUTED);
			ry += 22;
		}
		if (armor) {
			String[] trim = config().customArmorTrims.get(uuid());
			label(graphics, "fancybars.items.trim", ry - 12, trim == null ? Component.translatable("fancybars.items.default")
					: Component.literal(Customization.prettyId(Identifier.parse(trim[0]).getPath()) + " / " + Customization.prettyId(Identifier.parse(trim[1]).getPath())));
			ry += 44;
		}
		if (head) {
			String hash = config().customHelmetTextures.get(uuid());
			String animated = config().customAnimatedHelmetTextures.get(uuid());
			label(graphics, "fancybars.items.head", ry - 12, hash != null ? Component.translatable("fancybars.items.custom")
					: animated != null ? Component.literal(Customization.prettyId(animated)) : Component.translatable("fancybars.items.default"));
		}
		renderColorButtons(graphics, mouseX, mouseY);
		// animated dye keyframes
		FancyBarsConfig.AnimatedDye dye = config().customAnimatedDyes.get(uuid());
		for (int[] chip : keyframeChips) {
			int color = dye != null && chip[3] < dye.colors.size() ? dye.colors.get(chip[3]) : 0xFFFFFF;
			Theme.roundedRect(graphics, chip[0], chip[1], chip[2], chip[2], 3, 0xFF000000 | color);
			boolean hovered = mouseX >= chip[0] && mouseX < chip[0] + chip[2] && mouseY >= chip[1] && mouseY < chip[1] + chip[2];
			Theme.roundedOutline(graphics, chip[0], chip[1], chip[2], chip[2], 3, hovered ? Theme.ACCENT : 0x66FFFFFF);
			if (hovered) {
				graphics.setTooltipForNextFrame(font, Component.translatable("fancybars.items.keyframeTip"), mouseX, mouseY);
				graphics.requestCursor(CursorTypes.POINTING_HAND);
			}
		}
	}

	private void renderColorButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		for (int[] b : colorButtons) {
			boolean hovered = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[2];
			int index = b[3];
			Component tip = null;
			if (index < 100) {
				int color = Customization.LEGACY_COLORS[index];
				Theme.roundedRect(graphics, b[0], b[1], b[2], b[2], 2, 0xFF000000 | color);
				tip = Component.literal("&" + "0123456789abcdef".charAt(index) + " " + Customization.prettyId(COLORS[index].name())).withColor(color == 0 ? 0x555555 : color);
			} else if (index < 200) {
				String[] labels = {"B", "I", "U", "S", "K", "R"};
				ChatFormatting[] formats = {ChatFormatting.BOLD, ChatFormatting.ITALIC, ChatFormatting.UNDERLINE, ChatFormatting.STRIKETHROUGH, ChatFormatting.OBFUSCATED, ChatFormatting.RESET};
				Theme.roundedRect(graphics, b[0], b[1], b[2], b[2] - 2, 3, hovered ? Theme.SURFACE_HOVER : Theme.SURFACE);
				Theme.centeredInBox(graphics, font, Component.literal(labels[index - 100]).withStyle(formats[index - 100] == ChatFormatting.RESET ? ChatFormatting.RESET : formats[index - 100]),
						b[0] + b[2] / 2f, b[1], b[2] - 2, Theme.TEXT);
				tip = Component.literal("&" + "lonmkr".charAt(index - 100) + " " + Customization.prettyId(formats[index - 100].name()));
			} else if (index == 200 || index == 201) {
				Theme.roundedRect(graphics, b[0], b[1], b[2], b[2], 3, 0xFF000000 | (index == 200 ? gradientFrom : gradientTo));
				tip = Component.translatable(index == 200 ? "fancybars.items.gradientFrom" : "fancybars.items.gradientTo");
			} else if (index == 300) {
				Integer dye = config().customDyeColors.get(uuid());
				Theme.roundedRect(graphics, b[0], b[1], b[2], b[2], 3, 0xFF000000 | (dye == null ? 0xA06540 : dye));
				tip = Component.translatable("fancybars.items.pickDye");
			} else if (index == 400) {
				Theme.roundedRect(graphics, b[0], b[1], b[2], b[2], 3, hovered ? Theme.SURFACE_HOVER : Theme.SURFACE);
				Theme.centeredInBox(graphics, font, Component.literal("+"), b[0] + b[2] / 2f, b[1], b[2], Theme.TEXT);
				tip = Component.translatable("fancybars.items.addKeyframe");
			}
			if (hovered) {
				Theme.roundedOutline(graphics, b[0] - 1, b[1] - 1, b[2] + 2, b[2] + 2, 3, Theme.ACCENT);
				if (tip != null) graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
				graphics.requestCursor(CursorTypes.POINTING_HAND);
			}
		}
	}

	// ================================================================ Input

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (dev.fancybars.util.gui.Dropdown.clickOverlay(click.x(), click.y())) return true;
		if (super.mouseClicked(click, doubled)) return true;
		double mx = click.x();
		double my = click.y();
		for (int i = 0; i < SLOT_ORDER.length; i++) {
			int sx = gridX + i % COLS * SLOT;
			int sy = gridY + i / COLS * SLOT + (i >= 36 ? 4 : i >= 27 ? 2 : 0);
			if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
				if (!ItemProtection.getUuid(stack(SLOT_ORDER[i])).isEmpty()) reopen(SLOT_ORDER[i], tab);
				return true;
			}
		}
		String uuid = uuid();
		for (int[] b : colorButtons) {
			if (mx < b[0] || mx >= b[0] + b[2] || my < b[1] || my >= b[1] + b[2]) continue;
			int index = b[3];
			if (index < 100 && nameField != null) {
				nameField.setText(nameField.getText() + "&" + "0123456789abcdef".charAt(index));
			} else if (index < 200 && nameField != null) {
				nameField.setText(nameField.getText() + "&" + "lonmkr".charAt(index - 100));
			} else if (index == 200 || index == 201) {
				boolean from = index == 200;
				minecraft.gui.setScreen(new EditBarColorPopup(Component.translatable(from ? "fancybars.items.gradientFrom" : "fancybars.items.gradientTo"), this,
						color -> {
							if (from) gradientFrom = color.getRGB() & 0xFFFFFF;
							else gradientTo = color.getRGB() & 0xFFFFFF;
						}, from ? gradientFrom : gradientTo));
			} else if (index == 300) {
				Integer current = config().customDyeColors.get(uuid);
				minecraft.gui.setScreen(new EditBarColorPopup(Component.translatable("fancybars.items.pickDye"), this, color -> {
					config().customAnimatedDyes.remove(uuid);
					config().customDyeColors.put(uuid, color.getRGB() & 0xFFFFFF);
					refresh();
				}, current == null ? 0xA06540 : current));
			} else if (index == 400) {
				FancyBarsConfig.AnimatedDye dye = config().customAnimatedDyes.computeIfAbsent(uuid, u -> new FancyBarsConfig.AnimatedDye());
				int last = dye.colors.isEmpty() ? 0xFF8FC8 : dye.colors.getLast();
				minecraft.gui.setScreen(new EditBarColorPopup(Component.translatable("fancybars.items.addKeyframe"), this, color -> {
					dye.colors.add(color.getRGB() & 0xFFFFFF);
					config().customDyeColors.remove(uuid);
					refresh();
					reopen(selectedSlot, tab);
				}, last));
			}
			return true;
		}
		for (int[] chip : keyframeChips) {
			if (mx < chip[0] || mx >= chip[0] + chip[2] || my < chip[1] || my >= chip[1] + chip[2]) continue;
			FancyBarsConfig.AnimatedDye dye = config().customAnimatedDyes.get(uuid);
			if (dye == null || chip[3] >= dye.colors.size()) return true;
			if (click.button() == 1) {
				dye.colors.remove(chip[3]);
				if (dye.colors.isEmpty()) config().customAnimatedDyes.remove(uuid);
				refresh();
				reopen(selectedSlot, tab);
			} else {
				int index = chip[3];
				minecraft.gui.setScreen(new EditBarColorPopup(Component.translatable("fancybars.items.animatedDye"), this, color -> {
					dye.colors.set(index, color.getRGB() & 0xFFFFFF);
					refresh();
				}, dye.colors.get(index)));
			}
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		dev.fancybars.util.gui.Dropdown.close();
		FancyBarsConfig.save();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@SuppressWarnings("unused")
	private static Color awt(int rgb) {
		return new Color(rgb);
	}

	@SuppressWarnings("unused")
	private static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}
}
