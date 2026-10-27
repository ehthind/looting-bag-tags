package com.lootingbagtags;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;

@Slf4j
@PluginDescriptor(
	name = "Looting Bag Tags",
	internalName = "looting-bag-tags",
	description = "Adds looting bag deposits to a dedicated bank tag tab",
	tags = {"bank", "looting", "bag", "tags", "deposits"}
)
@PluginDependency(BankTagsPlugin.class)
public class LootingBagTagsPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ItemManager itemManager;
	@Inject private ConfigManager configManager;
	@Inject private LootingBagTagsConfig config;
	@Inject private LootingBagTagWriter tagWriter;
	@Inject private BankTagsService bankTagsService;

	private final BagDepositTracker tracker = new BagDepositTracker();
	private boolean bankWasOpen;
	private boolean containersChanged;

	@Provides
	LootingBagTagsConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(LootingBagTagsConfig.class);
	}

	@Override
	protected void startUp()
	{
		reset();
		clientThread.invokeLater(() ->
		{
			configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, "useTabs", true);
		});
		log.info("Looting Bag Tags enabled");
	}

	@Override
	protected void shutDown()
	{
		reset();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING
			|| state == GameState.CONNECTION_LOST || state == GameState.LOGGING_IN)
		{
			reset();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.LOOTING_BAG
			|| event.getContainerId() == InventoryID.BANK)
		{
			containersChanged = true;
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		updateBankVisit();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateBankVisit();
		if (bankWasOpen && containersChanged)
		{
			reconcileDeposits();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		int component = event.getParam1();
		if (component != InterfaceID.Bankmain.DEPOSITCONTAINERS
			&& component != InterfaceID.Bankside.LOOTINGBAG_BANKALL
			&& component != InterfaceID.Bankside.LOOTINGBAG_ITEMS)
		{
			return;
		}
		updateBankVisit();
		if (bankWasOpen)
		{
			// Finish any preceding deposit before another click can reset its baseline.
			reconcileDeposits();
			tracker.prime(snapshot(InventoryID.LOOTING_BAG), snapshot(InventoryID.BANK), true);
		}
	}

	private void updateBankVisit()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
		boolean open = client.getGameState() == GameState.LOGGED_IN
			&& bank != null && !bank.isHidden();
		if (open == bankWasOpen)
		{
			return;
		}
		bankWasOpen = open;
		tracker.prime(snapshot(InventoryID.LOOTING_BAG), open ? snapshot(InventoryID.BANK) : null, open);
		containersChanged = false;
	}

	private void reconcileDeposits()
	{
		Set<Integer> deposited = tracker.detect(
			snapshot(InventoryID.LOOTING_BAG), snapshot(InventoryID.BANK), true);
		containersChanged = false;
		if (deposited.isEmpty())
		{
			return;
		}

		boolean created = tagWriter.createIfMissing(deposited);
		if (created && config.openAfterDeposit())
		{
			bankTagsService.openBankTag(tagWriter.getTagName(), BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		}
		log.debug("Observed {} looting bag item types; created tab: {}", deposited.size(), created);
	}

	private Map<Integer, Integer> snapshot(int containerId)
	{
		ItemContainer container = client.getItemContainer(containerId);
		if (container == null)
		{
			return null;
		}
		Map<Integer, Integer> items = new HashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() >= 0 && item.getQuantity() > 0)
			{
				int id = itemManager.canonicalize(item.getId());
				// Use saturation for large stacks instead of overflowing an integer.
				items.merge(id, item.getQuantity(), (left, right) ->
					(int) Math.min(Integer.MAX_VALUE, (long) left + right));
			}
		}
		return items;
	}

	private void reset()
	{
		tracker.reset();
		bankWasOpen = false;
		containersChanged = false;
	}
}
