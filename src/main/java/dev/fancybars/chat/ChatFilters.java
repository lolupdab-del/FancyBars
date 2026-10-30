package dev.fancybars.chat;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.SkyblockLocation;

/**
 * Chat toggles ported from Skytils' Spam Hider: each one hides a kind of SkyBlock chat spam.
 * (Skytils can also move them to a separate spam window; here they are simply hidden.)
 */
public final class ChatFilters {
	private static final Set<String> SEA_CREATURES = Set.of(
			"Frozen Steve fell into the pond long ago, never to resurface...until now!", "It's a snowman! He looks harmless",
			"The Grinch stole Jerry's Gifts...get them back!", "Phew! It's only a Scarecrow.",
			"You hear trotting from beneath the waves, you caught a Nightmare.", "It must be a full moon, a Werewolf appears.",
			"A tiny fin emerges from the water, you've caught a Nurse Shark.", "You spot a fin as blue as the water it came from, it's a Blue Shark.",
			"A striped beast bounds from the depths, the wild Tiger Shark!", "A Squid appeared.", "You caught a Sea Walker.",
			"Pitch darkness reveals a Night Squid.", "You stumbled upon a Sea Guardian.",
			"It looks like you've disrupted the Sea Witch's brewing session. Watch out, she's furious!", "You reeled in a Sea Archer.",
			"The Monster of the Deep has emerged.", "Huh? A Catfish!", "Is this even a Fish? It's the Carrot King!", "Gross! A Sea Leech!",
			"You've discovered a Guardian Defender of the sea.", "You have awoken the Deep Sea Protector, prepare for a battle!");
	private static final Set<String> LEGENDARY_SEA_CREATURES = Set.of(
			"What is this creature!?", "The spirit of a long lost Phantom Fisher has come to haunt you.",
			"This can't be! The manifestation of death himself!",
			"Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!",
			"The Water Hydra has come to test your strength.", "The Sea Emperor arises from the depths.");
	private static final Pattern HEALED = Pattern.compile("healed .* for [\\d,.]+ health|healed you for");

	/** One chat toggle: id (used for config / translation) and the test (stripped text, in dungeons). */
	public record Filter(String id, String group, BiPredicate<String, Boolean> test) {}

	public static final List<Filter> ALL = List.of(
			// General
			new Filter("profile", "general", (s, d) -> s.startsWith("You are playing on profile:")),
			new Filter("autopet", "general", (s, d) -> s.startsWith("Autopet equipped your")),
			new Filter("cooldown", "general", (s, d) -> s.contains(" cooldown") && !s.contains(":")),
			new Filter("notEnoughMana", "general", (s, d) -> s.contains("You do not have enough mana to do this!") || s.startsWith("Not enough mana!")),
			new Filter("blocksInTheWay", "general", (s, d) -> s.contains("There are blocks in the way")),
			new Filter("noEnemies", "general", (s, d) -> s.startsWith("There are no enemies nearby!")),
			new Filter("combo", "general", (s, d) -> s.contains(" Combo") && !s.contains(":")),
			new Filter("blessingEnchant", "general", (s, d) -> s.startsWith("Your Blessing enchant")),
			new Filter("autoRecomb", "general", (s, d) -> s.startsWith("Your Auto-Recomb")),
			new Filter("miningAbility", "general", (s, d) -> !s.contains(":") && (s.contains("Mining Speed Boost") || s.contains("Pickobulus"))
					&& (s.contains("is now available!") || s.contains("has expired!") || s.contains("You used your"))),
			// Item ability damage
			new Filter("implosion", "abilities", (s, d) -> s.contains("Your Implosion hit ")),
			new Filter("moltenWave", "abilities", (s, d) -> s.contains("Your Molten Wave hit ")),
			new Filter("spiritSceptre", "abilities", (s, d) -> s.contains("Your Spirit Sceptre hit ")),
			new Filter("giantsSword", "abilities", (s, d) -> s.contains("Your Giant's Sword hit ")),
			new Filter("lividDagger", "abilities", (s, d) -> s.contains("Your Livid Dagger hit")),
			new Filter("rayOfHope", "abilities", (s, d) -> s.startsWith("Your Ray of Hope hit")),
			new Filter("otherAbilityDamage", "abilities", (s, d) -> s.contains("Your Guided Sheep hit") || s.contains("Your Thunderstorm hit")
					|| s.contains("Your Throwing Axe hit") || s.contains("Your Explosive Shot hit") || s.contains("Your Seismic Wave hit")),
			// Dungeons
			new Filter("mort", "dungeons", (s, d) -> s.startsWith("[NPC] Mort")),
			new Filter("boss", "dungeons", (s, d) -> s.startsWith("[BOSS]") && !s.startsWith("[BOSS] The Watcher")),
			new Filter("oruo", "dungeons", (s, d) -> s.startsWith("[STATUE] Oruo the Omniscient: ") && !s.contains("You should have listened")
					&& !s.contains("Yikes") && !s.contains("chose the wrong answer") && !s.contains("thinks the answer is")),
			new Filter("cantUseAbility", "dungeons", (s, d) -> s.startsWith("You cannot use abilities in this room!")),
			new Filter("blessings", "dungeons", (s, d) -> s.contains("DUNGEON BUFF!") || d && s.contains("Blessing of ")),
			new Filter("witherKey", "dungeons", (s, d) -> d && (s.contains("Wither Key") || s.contains("RIGHT CLICK on a WITHER door to open it."))),
			new Filter("bloodKey", "dungeons", (s, d) -> d && (s.contains("Blood Key") || s.contains("RIGHT CLICK on the BLOOD DOOR to open it."))),
			new Filter("superboom", "dungeons", (s, d) -> d && s.contains("Superboom TNT")),
			new Filter("reviveStone", "dungeons", (s, d) -> d && s.contains("Revive Stone")),
			new Filter("dungeonAbilities", "dungeons", (s, d) -> d && !s.contains(":") && (s.contains("is ready to use!") || s.startsWith("Used ")
					|| s.contains("is now available!") && !s.contains("Mining Speed Boost") && !s.contains("Pickobulus"))),
			new Filter("dungeonCountdown", "dungeons", (s, d) -> d && (s.contains("has started the dungeon countdown") || s.contains("is now ready!") || s.contains("Dungeon starts in"))),
			new Filter("tether", "dungeons", (s, d) -> s.startsWith("You formed a tether")),
			new Filter("ownOrbs", "dungeons", (s, d) -> s.contains("You picked up a") && s.contains("Orb")),
			new Filter("otherOrbs", "dungeons", (s, d) -> s.contains("picked up your ") && s.contains("Orb")),
			new Filter("healing", "dungeons", (s, d) -> HEALED.matcher(s).find()),
			new Filter("traps", "dungeons", (s, d) -> s.startsWith("The Tripwire Trap") || s.startsWith("The Flamethrower") || s.startsWith("The Arrow Trap") || s.startsWith("The Crusher")),
			new Filter("witherEssence", "dungeons", (s, d) -> s.contains("unlocked Wither Essence x")),
			new Filter("undeadEssence", "dungeons", (s, d) -> s.contains("unlocked Undead Essence x")),
			// Fishing
			new Filter("seaCreatures", "fishing", (s, d) -> SEA_CREATURES.contains(s)),
			new Filter("legendarySeaCreatures", "fishing", (s, d) -> LEGENDARY_SEA_CREATURES.contains(s)),
			new Filter("goodCatch", "fishing", (s, d) -> s.startsWith("GOOD CATCH! You found")),
			new Filter("greatCatch", "fishing", (s, d) -> s.startsWith("GREAT CATCH! You found")),
			new Filter("compact", "fishing", (s, d) -> s.startsWith("COMPACT! You found")),
			new Filter("pristine", "fishing", (s, d) -> s.startsWith("PRISTINE! You found")),
			new Filter("blessedBait", "fishing", (s, d) -> s.startsWith("Your bait got you double")),
			new Filter("wind", "fishing", (s, d) -> s.contains("The wind has changed direction!")));

	private ChatFilters() {}

	public static void init() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !shouldHide(message));
	}

	public static boolean isOn(String id) {
		return FancyBarsConfig.get().chatFilters.contains(id);
	}

	private static boolean shouldHide(Component message) {
		Set<String> on = FancyBarsConfig.get().chatFilters;
		if (on.isEmpty() || !SkyblockLocation.isOnSkyblock()) return false;
		String stripped = ChatFormatting.stripFormatting(message.getString());
		if (stripped == null) return false;
		stripped = stripped.trim();
		boolean dungeon = SkyblockLocation.isInDungeons();
		for (Filter filter : ALL) {
			if (on.contains(filter.id()) && filter.test().test(stripped, dungeon)) return true;
		}
		return false;
	}
}
