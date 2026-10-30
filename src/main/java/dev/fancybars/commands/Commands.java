package dev.fancybars.commands;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import dev.fancybars.config.FancyBarsConfig;
import dev.fancybars.util.gui.Theme;

/**
 * Chat command extras:
 * - Calculator: /ca 5m/5b (trigger words are configurable)
 * - Command aliases: /max → /call maxwell (any command, any arguments after it are kept)
 * - /nuke party: the next party you join gets "!pt" in party chat, and once it's yours, /p disband
 * - Shitter list: /sl &lt;ign&gt; adds / removes someone; they get kicked from any party you lead when they join
 */
public final class Commands {
	private static final Pattern JOINED_PARTY = Pattern.compile("^(?:You have joined |Party Finder > You joined ).*party", Pattern.CASE_INSENSITIVE);
	private static final Pattern TRANSFERRED = Pattern.compile("^The party was transferred to (?:\\[[^]]+] )?(\\w+)");
	private static final Pattern MEMBER_JOINED = Pattern.compile("^(?:\\[[^]]+] )?(\\w+) joined the party\\.?$");
	private static final Pattern PF_JOINED = Pattern.compile("^Party Finder > (\\w+) joined the (?:dungeon )?group!");
	private static final long NUKE_TIMEOUT = 5 * 60_000;

	private static long nukeArmedAt = 0;
	private static boolean nukeJoined = false;

	private Commands() {}

	public static void init() {
		// calculator + aliases run on outgoing commands, so changes apply immediately (no rejoin needed)
		ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !handleCalculator(command));
		ClientSendMessageEvents.MODIFY_COMMAND.register(Commands::expandAlias);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) onChat(message);
		});
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("nuke").then(ClientCommands.literal("party").executes(ctx -> {
			armNuke();
			return 1;
		})));
		dispatcher.register(ClientCommands.literal("sl")
				.executes(ctx -> {
					listShitters();
					return 1;
				})
				.then(ClientCommands.literal("list").executes(ctx -> {
					listShitters();
					return 1;
				}))
				.then(ClientCommands.literal("remove").then(ClientCommands.argument("ign", StringArgumentType.word()).executes(ctx -> {
					removeShitter(StringArgumentType.getString(ctx, "ign"));
					return 1;
				})))
				.then(ClientCommands.argument("ign", StringArgumentType.word()).executes(ctx -> {
					String ign = StringArgumentType.getString(ctx, "ign");
					if (isShitter(ign)) removeShitter(ign);
					else addShitter(ign);
					return 1;
				})));
	}

	public static void armNuke() {
		nukeArmedAt = System.currentTimeMillis();
		nukeJoined = false;
		say(Component.translatable("fancybars.nuke.armed").withStyle(ChatFormatting.RED));
	}

	private static void say(Component message) {
		if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(Theme.chatPrefix().append(message));
	}

	private static void send(String command) {
		if (Minecraft.getInstance().getConnection() != null) Minecraft.getInstance().getConnection().sendCommand(command);
	}

	// ---------------------------------------------------------------- Calculator

	private static boolean handleCalculator(String command) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (!config.calculatorEnabled) return false;
		int space = command.indexOf(' ');
		String word = (space < 0 ? command : command.substring(0, space)).toLowerCase(Locale.ROOT);
		boolean match = false;
		for (String trigger : config.calculatorTriggers.split(",")) {
			if (trigger.trim().replace("/", "").equalsIgnoreCase(word)) match = true;
		}
		if (!match) return false;
		String expression = space < 0 ? "" : command.substring(space + 1).trim();
		if (expression.isEmpty()) {
			say(Component.translatable("fancybars.calc.usage", "/" + word).withStyle(ChatFormatting.GRAY));
			return true;
		}
		try {
			double result = Calculator.evaluate(expression);
			String full = Calculator.format(result);
			String shortForm = Calculator.shortForm(result);
			Component line = Component.literal(expression + " = ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(full).withColor(0x7CC8FF))
					.append(shortForm.isEmpty() ? Component.empty() : Component.literal(" (" + shortForm + ")").withStyle(ChatFormatting.DARK_GRAY));
			say(line);
		} catch (Exception e) {
			say(Component.translatable("fancybars.calc.error", e.getMessage() == null ? "?" : e.getMessage()).withStyle(ChatFormatting.RED));
		}
		return true;
	}

	// ---------------------------------------------------------------- Aliases

	private static String expandAlias(String command) {
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.commandAliases.isEmpty()) return command;
		int space = command.indexOf(' ');
		String word = space < 0 ? command : command.substring(0, space);
		String rest = space < 0 ? "" : command.substring(space);
		for (FancyBarsConfig.CommandAlias alias : config.commandAliases) {
			String from = clean(alias.from);
			String to = clean(alias.to);
			if (!from.isEmpty() && !to.isEmpty() && from.equalsIgnoreCase(word)) return to + rest;
		}
		return command;
	}

	/** "/call maxwell " → "call maxwell". */
	public static String clean(String s) {
		if (s == null) return "";
		s = s.trim();
		while (s.startsWith("/")) s = s.substring(1);
		return s.trim();
	}

	// ---------------------------------------------------------------- Nuke + shitter list

	private static void onChat(Component message) {
		String text = ChatFormatting.stripFormatting(message.getString());
		if (text == null) return;
		text = text.trim();
		Minecraft mc = Minecraft.getInstance();
		String me = mc.player != null ? mc.player.getGameProfile().name() : "";

		// /nuke party
		if (nukeArmedAt > 0) {
			if (System.currentTimeMillis() - nukeArmedAt > NUKE_TIMEOUT) {
				nukeArmedAt = 0;
				say(Component.translatable("fancybars.nuke.expired").withStyle(ChatFormatting.GRAY));
			} else if (!nukeJoined && JOINED_PARTY.matcher(text).find()) {
				nukeJoined = true;
				send("pc !pt");
			} else if (nukeJoined) {
				Matcher m = TRANSFERRED.matcher(text);
				if (m.find() && m.group(1).equalsIgnoreCase(me)) {
					send("p disband");
					nukeArmedAt = 0;
					nukeJoined = false;
					say(Component.translatable("fancybars.nuke.done").withStyle(ChatFormatting.RED));
				}
			}
		}

		// shitter list
		FancyBarsConfig config = FancyBarsConfig.get();
		if (config.shitterList.isEmpty()) return;
		String joined = null;
		Matcher member = MEMBER_JOINED.matcher(text);
		if (member.find()) joined = member.group(1);
		Matcher pf = PF_JOINED.matcher(text);
		if (joined == null && pf.find()) joined = pf.group(1);
		if (joined != null && !joined.equalsIgnoreCase(me) && isShitter(joined)) {
			say(Component.translatable("fancybars.sl.joined", joined).withStyle(ChatFormatting.RED));
			if (config.shitterAutoKick) send("p kick " + joined);
		}
	}

	public static boolean isShitter(String ign) {
		for (String s : FancyBarsConfig.get().shitterList) if (s.equalsIgnoreCase(ign)) return true;
		return false;
	}

	public static void addShitter(String ign) {
		if (isShitter(ign)) return;
		FancyBarsConfig.get().shitterList.add(ign);
		FancyBarsConfig.save();
		say(Component.translatable("fancybars.sl.added", ign).withStyle(ChatFormatting.RED));
	}

	public static void removeShitter(String ign) {
		FancyBarsConfig.get().shitterList.removeIf(s -> s.equalsIgnoreCase(ign));
		FancyBarsConfig.save();
		say(Component.translatable("fancybars.sl.removed", ign).withStyle(ChatFormatting.GREEN));
	}

	private static void listShitters() {
		java.util.List<String> list = FancyBarsConfig.get().shitterList;
		if (list.isEmpty()) {
			say(Component.translatable("fancybars.sl.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		say(Component.translatable("fancybars.sl.list", list.size(), String.join(", ", list)).withStyle(ChatFormatting.GRAY));
	}
}
