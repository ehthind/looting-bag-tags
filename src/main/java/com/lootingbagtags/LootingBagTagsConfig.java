package com.lootingbagtags;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("lootingbagtags")
public interface LootingBagTagsConfig extends Config
{
	@ConfigItem(
		keyName = "openAfterDeposit",
		name = "Open newly created tab",
		description = "Show the bag tag when first created. Later deposits add item types without switching tabs. Quantities are bank totals.",
		position = 0
	)
	default boolean openAfterDeposit()
	{
		return true;
	}
}
