package dev.fancybars.util;

import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Item helpers ported from Skyblocker's ItemUtils / ItemAbility.
 */
public class ItemUtils {
	private static final Pattern ABILITY_NAME_PATTERN = Pattern.compile("(?:⦾ )?Ability: (.+) {2}(RIGHT CLICK|LEFT CLICK|SNEAK RIGHT CLICK|SNEAK LEFT CLICK)");
	private static final Pattern MANA_COST_PATTERN = Pattern.compile("Mana Cost: (\\d+)");

	public static List<ItemStack> getArmor(LivingEntity entity) {
		return EquipmentSlotGroup.ARMOR.slots().stream()
				.filter(es -> es.getType() == EquipmentSlot.Type.HUMANOID_ARMOR)
				.map(entity::getItemBySlot)
				.toList();
	}

	public static List<String> getLoreStrings(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).styledLines().stream().map(Component::getString).toList();
	}

	/**
	 * @return the mana cost of the first right click ability on the item, if any.
	 */
	public static OptionalInt getRightClickManaCost(ItemStack stack) {
		boolean inRightClickAbility = false;
		for (String line : getLoreStrings(stack)) {
			String string = ChatFormatting.stripFormatting(line).trim();
			Matcher matcher = ABILITY_NAME_PATTERN.matcher(string);
			if (matcher.matches()) {
				if (inRightClickAbility) return OptionalInt.empty(); // previous right click ability had no mana cost
				inRightClickAbility = matcher.group(2).endsWith("RIGHT CLICK");
				continue;
			}
			if (!inRightClickAbility) continue;
			matcher = MANA_COST_PATTERN.matcher(string);
			if (matcher.matches()) {
				try {
					return OptionalInt.of(Integer.parseInt(matcher.group(1)));
				} catch (NumberFormatException _) {
					return OptionalInt.empty();
				}
			}
		}
		return OptionalInt.empty();
	}
}
