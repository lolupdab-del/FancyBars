package dev.fancybars.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.protection.ItemProtection;
import dev.fancybars.util.ItemUtils;
import dev.fancybars.util.SkyblockLocation;
import dev.fancybars.util.gui.Theme;

/**
 * Backpack / ender chest previews, ported from Skyblocker: whenever you close an ender chest page or a backpack,
 * its contents are remembered (per SkyBlock profile), and hovering that backpack in the Storage menu shows them.
 */
public final class BackpackPreview {
	private static final Pattern ECHEST_PATTERN = Pattern.compile("Ender Chest.*\\((\\d+)/\\d+\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern BACKPACK_PATTERN = Pattern.compile("Backpack.*\\(Slot #(\\d+)\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern STORAGE_PATTERN = Pattern.compile("Storage", Pattern.CASE_INSENSITIVE);
	private static final Pattern BACKPACK_SIZE_PATTERN = Pattern.compile("has (\\d+) slots", Pattern.CASE_INSENSITIVE);
	private static final Pattern PROFILE_ID_MESSAGE = Pattern.compile("^Profile ID: ([0-9a-fA-F-]+)");
	private static final Pattern PROFILE_NAME_MESSAGE = Pattern.compile("You are playing on profile: (\\w+)");
	private static final String TAB_PROFILE_PREFIX = "Profile: ";
	private static final int STORAGE_SIZE = 27;
	private static final Storage[] storages = new Storage[STORAGE_SIZE];

	private static final Codec<Holder<Item>> ITEM_CODEC = BuiltInRegistries.ITEM.holderByNameCodec();
	/** ItemStack codec that also allows empty stacks (so slot positions are kept). */
	private static final Codec<ItemStack> STACK_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(instance -> instance.group(
			ITEM_CODEC.fieldOf("id").forGetter(ItemStack::typeHolder),
			Codec.INT.orElse(1).fieldOf("count").forGetter(ItemStack::getCount),
			DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStack::getComponentsPatch)
	).apply(instance, ItemStack::new)));

	private static @Nullable String profileId = null;
	private static @Nullable String profileName = null;
	private static @Nullable String loaded = null;
	private static volatile @Nullable Path saveDir = null;
	private static int ticks = 0;

	private BackpackPreview() {}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?> containerScreen) {
				ScreenEvents.remove(screen).register(s -> updateStorage(containerScreen));
			}
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			String text = ChatFormatting.stripFormatting(message.getString());
			if (text == null) return;
			Matcher id = PROFILE_ID_MESSAGE.matcher(text);
			if (id.find()) profileId = id.group(1).replace("-", "");
			Matcher name = PROFILE_NAME_MESSAGE.matcher(text);
			if (name.find()) {
				profileName = name.group(1);
				profileId = null; // a new profile id message follows when switching profiles
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			profileId = null;
			profileName = null;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++ticks % 50 == 0) tick();
		});
	}

	private static void tick() {
		if (!SkyblockLocation.isOnSkyblock()) return;
		if (profileName == null) profileName = profileFromTabList();
		saveStorages();

		String profile = profileId != null ? profileId : profileName;
		if (profile == null) return; // don't know the profile yet: keep everything in memory only
		String key = Minecraft.getInstance().getUser().getProfileId().toString().replace("-", "") + "/" + profile;
		if (!key.equals(loaded)) {
			loaded = key;
			Path dir = FancyBarsMod.CONFIG_DIR.resolve("backpack-preview/" + key);
			saveDir = dir;
			CompletableFuture.runAsync(() -> {
				try {
					Files.createDirectories(dir);
				} catch (Exception e) {
					FancyBarsMod.LOGGER.error("[FancyBars] Couldn't create backpack preview folder {}", dir, e);
				}
			}, FancyBarsMod.VIRTUAL_THREAD_EXECUTOR);
			loadStorages(dir);
		}
	}

	private static @Nullable String profileFromTabList() {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) return null;
		for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
			if (info.getTabListDisplayName() == null) continue;
			String name = info.getTabListDisplayName().getString().trim();
			if (name.startsWith(TAB_PROFILE_PREFIX)) return name.substring(TAB_PROFILE_PREFIX.length()).trim().split(" ")[0];
		}
		return null;
	}

	private static @Nullable RegistryOps<Tag> ops() {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) return null;
		return client.getConnection().registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	private static void loadStorages(Path dir) {
		RegistryOps<Tag> ops = ops();
		if (ops == null) return;
		for (int index = 0; index < STORAGE_SIZE; index++) {
			int i = index;
			// keep what was seen this session before the profile was known
			if (storages[i] != null && storages[i].dirty) continue;
			storages[i] = null;
			CompletableFuture.supplyAsync(() -> {
				Path file = dir.resolve(i + ".nbt");
				if (Files.isRegularFile(file)) {
					try {
						return Storage.CODEC.parse(ops, NbtIo.read(file)).getOrThrow();
					} catch (Exception e) {
						FancyBarsMod.LOGGER.error("[FancyBars] Couldn't load backpack preview {}", file.getFileName(), e);
					}
				}
				return null;
			}, FancyBarsMod.VIRTUAL_THREAD_EXECUTOR).thenAcceptAsync(storage -> {
				if (storage != null && (storages[i] == null || !storages[i].dirty)) storages[i] = storage;
			}, Minecraft.getInstance());
		}
	}

	private static void saveStorages() {
		Path dir = saveDir;
		RegistryOps<Tag> ops = ops();
		if (dir == null || ops == null) return;
		for (int index = 0; index < STORAGE_SIZE; index++) {
			Storage storage = storages[index];
			if (storage == null || !storage.dirty) continue;
			int i = index;
			CompletableFuture.runAsync(() -> {
				Path file = dir.resolve(i + ".nbt");
				try {
					NbtIo.write((CompoundTag) Storage.CODEC.encodeStart(ops, storage).getOrThrow(), file);
				} catch (Exception e) {
					FancyBarsMod.LOGGER.error("[FancyBars] Couldn't save backpack preview {}", file.getFileName(), e);
				}
			}, FancyBarsMod.VIRTUAL_THREAD_EXECUTOR).thenRunAsync(() -> storage.dirty = false, Minecraft.getInstance());
		}
	}

	private static void updateStorage(AbstractContainerScreen<?> screen) {
		if (!SkyblockLocation.isOnSkyblock()) return;
		String title = screen.getTitle().getString();
		int index = getStorageIndexFromTitle(title);
		if (index != -1) {
			Container container = screen.getMenu().slots.getFirst().container;
			// copy the stacks: the menu's container gets reused/cleared after closing
			ItemStack[] copy = new ItemStack[container.getContainerSize()];
			for (int i = 0; i < copy.length; i++) copy[i] = container.getItem(i).copy();
			storages[index] = new Storage(new SimpleContainer(copy), title, true);
		}
		if (STORAGE_PATTERN.matcher(title).matches()) initializeStorage(screen);
	}

	/** Creates empty entries for ender chest pages / backpacks seen in the Storage menu but never opened yet. */
	private static void initializeStorage(AbstractContainerScreen<?> screen) {
		NonNullList<Slot> slots = screen.getMenu().slots;
		for (int i = 9; i < 18 && i < slots.size(); i++) {
			Slot slot = slots.get(i);
			int index = i - 9;
			if (slot.getItem().is(Items.STAINED_GLASS_PANE.red()) || storages[index] != null) continue;
			storages[index] = new Storage(emptyContainer(18), "", true);
		}
		for (int i = 27; i < 45 && i < slots.size(); i++) {
			Slot slot = slots.get(i);
			int index = i - 18;
			if (slot.getItem().is(Items.STAINED_GLASS_PANE.brown())) storages[index] = null;
			if (storages[index] != null) continue;
			for (String line : ItemUtils.getLoreStrings(slot.getItem())) {
				Matcher size = BACKPACK_SIZE_PATTERN.matcher(line);
				if (size.find()) {
					storages[index] = new Storage(emptyContainer(Integer.parseInt(size.group(1)) + 9), "", true);
					break;
				}
			}
		}
	}

	private static SimpleContainer emptyContainer(int size) {
		ItemStack[] stacks = new ItemStack[size];
		for (int i = 0; i < size; i++) stacks[i] = ItemStack.EMPTY;
		return new SimpleContainer(stacks);
	}

	public static int getStorageIndexFromTitle(String title) {
		Matcher echest = ECHEST_PATTERN.matcher(title);
		if (echest.find()) return Integer.parseInt(echest.group(1)) - 1;
		Matcher backpack = BACKPACK_PATTERN.matcher(title);
		if (backpack.find()) return Integer.parseInt(backpack.group(1)) + 8;
		return -1;
	}

	/**
	 * Whether a preview should replace the normal tooltip right now (setting + Shift behaviour).
	 */
	public static boolean wantsPreview() {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (!config.backpackPreviewEnabled || !SkyblockLocation.isOnSkyblock()) return false;
		return config.backpackPreviewNeedsShift == Minecraft.getInstance().hasShiftDown();
	}

	/**
	 * Draws the preview for the Storage menu slot {@code slotIndex}.
	 *
	 * @return true if a preview was drawn (so the normal tooltip should be skipped)
	 */
	public static boolean extractPreview(GuiGraphicsExtractor graphics, Screen screen, int slotIndex, int mouseX, int mouseY) {
		int index;
		if (slotIndex >= 9 && slotIndex < 18) index = slotIndex - 9;
		else if (slotIndex >= 27 && slotIndex < 45) index = slotIndex - 18;
		else return false;

		Storage storage = storages[index];
		if (storage == null) return false;
		int rows = Math.max(1, (storage.size() - 9) / 9);
		Font font = Minecraft.getInstance().font;

		int pad = 8;
		int w = 9 * 18 + pad * 2;
		int h = 24 + rows * 18 + pad;
		int x = mouseX + w + 12 >= screen.width ? mouseX - w - 8 : mouseX + 10;
		int y = Math.clamp(mouseY - 16, 2, Math.max(2, screen.height - h - 2));

		graphics.nextStratum();
		Theme.shadow(graphics, x, y, w, h, 6, 0, 2);
		Theme.card(graphics, x, y, w, h, 6, 0xF51B1D2B, Theme.SELECTED_BORDER);
		Component title = storage.name().isEmpty() ? Component.translatable("fancybars.backpack.unknown") : Component.literal(storage.name());
		Theme.boldInBox(graphics, font, title, x + pad, y + 4, 16, Theme.TEXT);

		int gridX = x + pad;
		int gridY = y + 22;
		for (int i = 9; i < storage.size(); i++) {
			int col = (i - 9) % 9;
			int row = (i - 9) / 9;
			int sx = gridX + col * 18;
			int sy = gridY + row * 18;
			Theme.roundedRect(graphics, sx, sy, 17, 17, 3, 0xFF262A3C);
		}
		for (int i = 9; i < storage.size(); i++) {
			ItemStack stack = storage.getStack(i);
			if (stack.isEmpty()) continue;
			int sx = gridX + (i - 9) % 9 * 18;
			int sy = gridY + (i - 9) / 9 * 18;
			if (ItemProtection.isItemProtected(stack)) ItemProtection.drawStar(graphics, sx, sy);
			graphics.item(stack, sx, sy);
			graphics.itemDecorations(font, stack, sx, sy);
		}
		return true;
	}

	public static final class Storage {
		static final Codec<Storage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("name").forGetter(Storage::name),
				STACK_CODEC.listOf().fieldOf("items").forGetter(Storage::items)
		).apply(instance, (name, items) -> new Storage(new SimpleContainer(items.toArray(ItemStack[]::new)), name, false)));

		private final Container inventory;
		private final String name;
		volatile boolean dirty;

		Storage(Container inventory, String name, boolean dirty) {
			this.inventory = inventory;
			this.name = name;
			this.dirty = dirty;
		}

		public String name() {
			return name;
		}

		public int size() {
			return inventory.getContainerSize();
		}

		public ItemStack getStack(int index) {
			return inventory.getItem(index);
		}

		private List<ItemStack> items() {
			List<ItemStack> items = new ArrayList<>();
			for (int i = 0; i < size(); i++) items.add(getStack(i));
			return items;
		}
	}
}
