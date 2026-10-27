package com.lootingbagtags;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.util.Text;

/**
 * Maintains one ordinary Bank Tags tab. All methods must run on the client thread.
 */
@Singleton
public class LootingBagTagWriter
{
	private static final String CONFIG_GROUP = "lootingbagtags";
	private static final String MANAGED_TAG_KEY = "managedTag";
	private static final String DEFAULT_TAG_NAME = "looting bag deposits";

	private final ConfigManager configManager;
	private final PluginManager pluginManager;

	@Inject
	LootingBagTagWriter(ConfigManager configManager, PluginManager pluginManager)
	{
		this.configManager = configManager;
		this.pluginManager = pluginManager;
	}

	public String getTagName()
	{
		String name = configManager.getConfiguration(CONFIG_GROUP, MANAGED_TAG_KEY);
		return name == null || name.isEmpty() ? DEFAULT_TAG_NAME : name;
	}

	/**
	 * Creates and fills the managed tab only when the user has deleted it.
	 * An existing tab, including its item tags and icon, is left entirely unchanged.
	 *
	 * @return whether a tab was created
	 */
	public boolean createIfMissing(Set<Integer> canonicalIds)
	{
		String savedTabs = configManager.getConfiguration(
			BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG);
		List<String> tabNames = new ArrayList<>(Text.fromCSV(savedTabs == null ? "" : savedTabs));
		String managedName = configManager.getConfiguration(CONFIG_GROUP, MANAGED_TAG_KEY);
		if (managedName != null && !managedName.isEmpty() && tabNames.contains(managedName))
		{
			return false;
		}

		Plugin bankTags = getBankTagsPlugin();
		TagManager tagManager = bankTags.getInjector().getInstance(TagManager.class);
		if (managedName == null || managedName.isEmpty())
		{
			managedName = DEFAULT_TAG_NAME;
			int suffix = 2;
			while (tabNames.contains(managedName) || !tagManager.getItemsForTag(managedName).isEmpty())
			{
				managedName = DEFAULT_TAG_NAME + " " + suffix++;
			}
			configManager.setConfiguration(CONFIG_GROUP, MANAGED_TAG_KEY, managedName);
		}

		// Deleting a tab can leave its old item tags behind. Clear only our own tag
		// before recreating it so this new bag never inherits a previous bag's loot.
		tagManager.removeTag(managedName);
		for (Integer itemId : canonicalIds)
		{
			if (itemId != null && itemId > 0)
			{
				tagManager.addTag(itemId, managedName, false);
			}
		}

		configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP,
			BankTagsPlugin.TAG_ICON_PREFIX + managedName, ItemID.LOOTING_BAG);
		tabNames.add(managedName);
		configManager.setConfiguration(
			BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG, Text.toCSV(tabNames));

		// Use Bank Tags' own injector so its live sidebar sees this exact manager.
		TabManager tabManager = bankTags.getInjector().getInstance(TabManager.class);
		TagTab tab = tabManager.find(managedName);
		if (tab == null)
		{
			tab = new TagTab();
			tab.setTag(managedName);
			tabManager.add(tab);
		}
		tab.setIconItemId(ItemID.LOOTING_BAG);

		// Do not call TabManager.save(): its list can be empty while the bank is closed.
		// The configuration merge above preserves every existing tab in that case.
		bankTags.getInjector().getInstance(BankSearch.class).layoutBank();
		return true;
	}

	private Plugin getBankTagsPlugin()
	{
		return pluginManager.getPlugins().stream()
			.filter(BankTagsPlugin.class::isInstance)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Bank Tags must be loaded"));
	}
}
