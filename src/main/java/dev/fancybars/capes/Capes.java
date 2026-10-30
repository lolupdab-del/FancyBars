package dev.fancybars.capes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import dev.fancybars.FancyBarsMod;
import dev.fancybars.config.FancyBarsConfig;

/**
 * Capes: every official Minecraft cape (downloaded from Mojang's texture server / the Minecraft Wiki on first use and
 * cached), plus a custom cape from {@code config/fancybars/capes/custom}. Only you see your cape.
 * Cape list (texture URLs) from eclipseisoffline's Cape Command mod.
 */
public final class Capes {
	public static final String NONE = "none";
	public static final String CUSTOM = "custom";
	public static final Path DIR = FancyBarsMod.CONFIG_DIR.resolve("capes");
	public static final Path CACHE = DIR.resolve("cache");
	public static final Path CUSTOM_DIR = DIR.resolve("custom");

	public record Cape(String id, String name, String url) {}

	public static final List<Cape> ALL = List.of(
			new Cape("mojang_classic", "Mojang (Classic)", "https://textures.minecraft.net/texture/8f120319222a9f4a104e2f5cb97b2cda93199a2ee9e1585cb8d09d6f687cb761"),
			new Cape("mojang", "Mojang", "https://textures.minecraft.net/texture/5786fe99be377dfb6858859f926c4dbc995751e91cee373468c5fbf4865e7151"),
			new Cape("mojang_studios", "Mojang Studios", "https://textures.minecraft.net/texture/9e507afc56359978a3eb3e32367042b853cddd0995d17d0da995662913fb00f7"),
			new Cape("minecon_2011", "MineCon 2011", "https://textures.minecraft.net/texture/953cac8b779fe41383e675ee2b86071a71658f2180f56fbce8aa315ea70e2ed6"),
			new Cape("minecon_2012", "MineCon 2012", "https://textures.minecraft.net/texture/a2e8d97ec79100e90a75d369d1b3ba81273c4f82bc1b737e934eed4a854be1b6"),
			new Cape("minecon_2013", "MineCon 2013", "https://textures.minecraft.net/texture/153b1a0dfcbae953cdeb6f2c2bf6bf79943239b1372780da44bcbb29273131da"),
			new Cape("minecon_2015", "MineCon 2015", "https://textures.minecraft.net/texture/b0cc08840700447322d953a02b965f1d65a13a603bf64b17c803c21446fe1635"),
			new Cape("minecon_2016", "MineCon 2016", "https://textures.minecraft.net/texture/e7dfea16dc83c97df01a12fabbd1216359c0cd0ea42f9999b6e97c584963e980"),
			new Cape("bacon", "Bacon", "https://textures.minecraft.net/texture/fd14214cd8073059e93d9c626260f5df85e5a959181537119df56cadaf5002cc"),
			new Cape("millionth_sale", "Millionth Customer", "https://textures.minecraft.net/texture/70efffaf86fe5bc089608d3cb297d3e276b9eb7a8f9f2fe6659c23a2d8b18edf"),
			new Cape("danny_b_style", "dannyBstyle", "https://textures.minecraft.net/texture/bcfbe84c6542a4a5c213c1cacf8979b5e913dcb4ad783a8b80e3c4a7d5c8bdac"),
			new Cape("julian_clark", "Julian Clark", "https://textures.minecraft.net/texture/23ec737f18bfe4b547c95935fc297dd767bb84ee55bfd855144d279ac9bfd9fe"),
			new Cape("cheapsh0t", "cheapsh0t", "https://textures.minecraft.net/texture/ca29f5dd9e94fb1748203b92e36b66fda80750c87ebc18d6eafdb0e28cc1d05f"),
			new Cape("mr_messiah", "MrMessiah", "https://textures.minecraft.net/texture/2e002d5e1758e79ba51d08d92a0f3a95119f2f435ae7704916507b6c565a7da8"),
			new Cape("prismarine", "Prismarine", "https://textures.minecraft.net/texture/d8f8d13a1adf9636a16c31d47f3ecc9bb8d8533108aa5ad2a01b13b1a0c55eac"),
			new Cape("turtle", "Turtle", "https://textures.minecraft.net/texture/5048ea61566353397247d2b7d946034de926b997d5e66c86483dfb1e031aee95"),
			new Cape("birthday", "Birthday", "https://textures.minecraft.net/texture/2056f2eebd759cce93460907186ef44e9192954ae12b227d817eb4b55627a7fc"),
			new Cape("valentine", "Valentine", "https://textures.minecraft.net/texture/e578ef995fabcf0a94768f9651ac3aaba30c59ef85d2438e9b3e0cc1d810652b"),
			new Cape("translator", "Translator", "https://textures.minecraft.net/texture/1bf91499701404e21bd46b0191d63239a4ef76ebde88d27e4d430ac211df681e"),
			new Cape("translator_chinese", "Translator (Chinese)", "https://textures.minecraft.net/texture/2262fb1d24912209490586ecae98aca8500df3eff91f2a07da37ee524e7e3cb6"),
			new Cape("scrolls", "Scrolls", "https://textures.minecraft.net/texture/3efadf6510961830f9fcc077f19b4daf286d502b5f5aafbd807c7bbffcaca245"),
			new Cape("cobalt", "Cobalt", "https://textures.minecraft.net/texture/ca35c56efe71ed290385f4ab5346a1826b546a54d519e6a3ff01efa01acce81"),
			new Cape("mojira_moderator", "Mojira Moderator", "https://textures.minecraft.net/texture/ae677f7d98ac70a533713518416df4452fe5700365c09cf45d0d156ea9396551"),
			new Cape("realms", "Realms", "https://textures.minecraft.net/texture/17912790ff164b93196f08ba71d0e62129304776d0f347334f8a6eae509f8a56"),
			new Cape("migrator", "Migrator", "https://textures.minecraft.net/texture/2340c0e03dd24a11b15a8b33c2a7e9e32abb2051b2481d0ba7defd635ca7a933"),
			new Cape("christmas_2010", "Christmas 2010", "https://minecraft.wiki/images/archive/20200223020907!Christmas_2010_Cape_(texture).png"),
			new Cape("new_year_2011", "New Year 2011", "https://minecraft.wiki/images/archive/20200223020920!New_Years_2011_Cape_(texture).png"),
			new Cape("founders", "Founders", "https://textures.minecraft.net/texture/99aba02ef05ec6aa4d42db8ee43796d6cd50e4b2954ab29f0caeb85f96bf52a1"),
			new Cape("pancape", "Pan", "https://textures.minecraft.net/texture/28de4a81688ad18b49e735a273e086c18f1e3966956123ccb574034c06f5d336"),
			new Cape("progress_pride", "Progress Pride", "https://minecraft.wiki/images/Progress_Pride_Cape_(texture)_rv3.png"),
			new Cape("vanilla", "Vanilla", "https://textures.minecraft.net/texture/f9a76537647989f9a0b6d001e320dac591c359e9e61a31f4ce11c88f207f0ad4"),
			new Cape("cherry_blossom", "Cherry Blossom", "https://textures.minecraft.net/texture/afd553b39358a24edfe3b8a9a939fa5fa4faa4d9a9c3d6af8eafb377fa05c2bb"),
			new Cape("fifteenth_anniversary", "15th Anniversary", "https://textures.minecraft.net/texture/cd9d82ab17fd92022dbd4a86cde4c382a7540e117fae7b9a2853658505a80625"),
			new Cape("purple_heart", "Purple Heart", "https://textures.minecraft.net/texture/cb40a92e32b57fd732a00fc325e7afb00a7ca74936ad50d8e860152e482cfbde"),
			new Cape("followers", "Follower's", "https://textures.minecraft.net/texture/569b7f2a1d00d26f30efe3f9ab9ac817b1e6d35f4f3cfb0324ef2d328223d350"),
			new Cape("mcc_15th_year", "MCC 15th Year", "https://textures.minecraft.net/texture/56c35628fe1c4d59dd52561a3d03bfa4e1a76d397c8b9c476c2f77cb6aebb1df"),
			new Cape("minecraft_experience", "Minecraft Experience", "https://textures.minecraft.net/texture/7658c5025c77cfac7574aab3af94a46a8886e3b7722a895255fbf22ab8652434"),
			new Cape("mojang_office", "Mojang Office", "https://textures.minecraft.net/texture/5c29410057e32abec02d870ecb52ec25fb45ea81e785a7854ae8429d7236ca26"),
			new Cape("sniffer_cape", "Sniffer", "https://minecraft.wiki/images/UnknownCape1_texture.png"),
			new Cape("snail_cape", "Snail", "https://minecraft.wiki/images/SnailCape_texture.png"),
			new Cape("frog_cape", "Frog", "https://minecraft.wiki/images/FrogAPI_texture.png"),
			new Cape("xbox_1st_birthday", "Xbox 1st Birthday", "https://minecraft.wiki/images/Xbox_1st_Birthday_Cape_%28texture%29.png"),
			new Cape("awesom_caep", "Awesom Caep", "https://minecraft.wiki/images/Awesom_Cape.png"),
			new Cape("blonk_caep", "Blonk Caep", "https://minecraft.wiki/images/Blonk_Cape.png"),
			new Cape("no_circle_caep", "No Circle Caep", "https://minecraft.wiki/images/No_circle_Cape.png"),
			new Cape("nyan_caep", "Nyan Caep", "https://minecraft.wiki/images/Nyan_Cape.png"),
			new Cape("squid_caep", "Squid Caep", "https://minecraft.wiki/images/Squid_Cape.png"),
			new Cape("veterinarian_caep", "Veterinarian Caep", "https://minecraft.wiki/images/Veterinarian_Cape.png"),
			new Cape("menace", "Menace", "https://textures.minecraft.net/texture/dbc21e222528e30dc88445314f7be6ff12d3aeebc3c192054fba7e3b3f8c77b1"),
			new Cape("home", "Home", "https://textures.minecraft.net/texture/1de21419009db483900da6298a1e6cbf9f1bc1523a0dcdc16263fab150693edd"),
			new Cape("yearn", "Yearn", "https://textures.minecraft.net/texture/308b32a9e303155a0b4262f9e5483ad4a22e3412e84fe8385a0bdd73dc41fa89"),
			new Cape("copper", "Copper", "https://textures.minecraft.net/texture/5e6f3193e74cd16cdd6637d9bae5484e3a37ff2a14c2d157c659a07810b1bdca"),
			new Cape("common", "Common", "https://textures.minecraft.net/texture/5ec930cdd2629c8771655c60eebeb867b4b6559b0e6d3bc71c40c96347fa03f0"),
			new Cape("moonlight_trail", "Moonlight Trail", "https://textures.minecraft.net/texture/fe8a02dfe9e390e44ff33d69feef9d3943f76d3901015bbd50f0b67722d288bd"),
			new Cape("crafter", "Crafter", "https://textures.minecraft.net/texture/479eacefa3cdd7aca94207f36c0dd449653ddf259daf40544a5866baf05eee22"),
			new Cape("builder", "Builder", "https://textures.minecraft.net/texture/2c579968c64c1719740fd8c2a451461879b238002574fce48f7d1a7c36a1c7d4"),
			new Cape("zombie_horse", "Zombie Horse", "https://textures.minecraft.net/texture/a3f6e4f14801f3ea55e3d95b9b4ef3b5e8802d947f669de93d6ec4b9354a436b"),
			new Cape("oxeye", "Oxeye", "https://textures.minecraft.net/texture/7706b5f5fc90329691e59277dcc66ba20572219fa8e5da472afd5235fad12cc8"),
			new Cape("blueprint", "Blueprint", "https://textures.minecraft.net/texture/fdcf48f01ec480d1d7cbec27f7ddce48c9da2be6724641109444dae58d4cd013"),
			new Cape("aurora", "Aurora", "https://textures.minecraft.net/texture/7c418dfbd37412a55e9f1425c9807a591ad10cf71b1edb576a587be9114c277f"),
			new Cape("hero", "Hero", "https://textures.minecraft.net/texture/bb384a1079b9a6f2811520c7991b6e8150d02e5a96457e44509e30822f72f38e"),
			new Cape("twisted", "Twisted", "https://textures.minecraft.net/texture/24aafc451aa2cc34ddc7265211678585c0ef4da4d32edb75ecec1bd8b5408381"));

	/** Loaded textures: id → {texture id, width, height}. */
	public record Loaded(Identifier texture, int width, int height) {}

	private static final Map<String, Loaded> LOADED = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> REQUESTED = new ConcurrentHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

	private Capes() {}

	public static void init() {
		try {
			Files.createDirectories(CACHE);
			Files.createDirectories(CUSTOM_DIR);
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't create cape folders", e);
		}
	}

	public static @Nullable Cape byId(String id) {
		for (Cape cape : ALL) if (cape.id().equals(id)) return cape;
		return null;
	}

	/** The texture for a cape, starting its download / load if needed. Null until it's ready. */
	public static @Nullable Loaded get(String id) {
		if (id == null || id.equals(NONE)) return null;
		Loaded loaded = LOADED.get(id);
		if (loaded != null) return loaded;
		if (REQUESTED.putIfAbsent(id, true) == null) load(id);
		return null;
	}

	/** The cape currently chosen in the settings, as a skin texture (null = keep your normal cape). */
	public static ClientAsset.@Nullable Texture selectedTexture() {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (!config.capeEnabled) return null;
		Loaded loaded = get(config.cape);
		return loaded == null ? null : new ClientAsset.ResourceTexture(loaded.texture(), loaded.texture());
	}

	private static void load(String id) {
		FancyBarsMod.VIRTUAL_THREAD_EXECUTOR.execute(() -> {
			try {
				byte[] bytes = id.equals(CUSTOM) ? readCustom() : download(id);
				if (bytes == null) {
					REQUESTED.remove(id);
					return;
				}
				Minecraft.getInstance().execute(() -> upload(id, bytes));
			} catch (Exception e) {
				FancyBarsMod.LOGGER.warn("[SBCLIENT] Couldn't load cape {}", id, e);
			}
		});
	}

	private static byte @Nullable [] download(String id) throws Exception {
		Cape cape = byId(id);
		if (cape == null) return null;
		Path cached = CACHE.resolve(id + ".png");
		if (Files.isRegularFile(cached)) return Files.readAllBytes(cached);
		HttpRequest request = HttpRequest.newBuilder(URI.create(cape.url())).timeout(Duration.ofSeconds(20))
				.header("User-Agent", "SBCLIENT Minecraft mod").GET().build();
		HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Cape {} download failed: HTTP {}", id, response.statusCode());
			return null;
		}
		Files.createDirectories(CACHE);
		Files.write(cached, response.body());
		return response.body();
	}

	private static byte @Nullable [] readCustom() throws Exception {
		Path file = customFile();
		return file == null ? null : Files.readAllBytes(file);
	}

	public static @Nullable Path customFile() {
		if (!Files.isDirectory(CUSTOM_DIR)) return null;
		try (Stream<Path> files = Files.list(CUSTOM_DIR)) {
			return files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().findFirst().orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	private static void upload(String id, byte[] bytes) {
		try {
			NativeImage image = NativeImage.read(bytes);
			Identifier texture = FancyBarsMod.id("capes/" + id + "_" + Integer.toHexString(java.util.Arrays.hashCode(bytes)));
			int w = image.getWidth();
			int h = image.getHeight();
			Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "SBCLIENT cape " + id, image));
			LOADED.put(id, new Loaded(texture, w, h));
		} catch (Exception e) {
			FancyBarsMod.LOGGER.warn("[SBCLIENT] Cape {} isn't a valid PNG", id, e);
		}
	}

	/** Forget the custom cape so a new file in the folder gets picked up. */
	public static void reloadCustom() {
		Loaded old = LOADED.remove(CUSTOM);
		REQUESTED.remove(CUSTOM);
		if (old != null) Minecraft.getInstance().getTextureManager().release(old.texture());
	}

	public static void openCustomFolder() {
		try {
			Files.createDirectories(CUSTOM_DIR);
		} catch (Exception ignored) {
		}
		Util.getPlatform().openPath(CUSTOM_DIR);
	}
}
