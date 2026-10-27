package com.lootingbagtags;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class LootingBagTagsLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LootingBagTagsPlugin.class);
		RuneLite.main(args);
	}
}
