package com.lootingbagtags;

import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.util.Text;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class LootingBagTagWriterTest
{
	private static final String TAG_NAME = "looting bag deposits";
	private static final String OWNED_NAME_KEY = "lootingbagtags.managedTag";
	private static final String TAB_NAMES_KEY = "banktags.tagtabs";

	private final Map<String, String> configuration = new HashMap<>();
	private final Map<Integer, Set<String>> itemTags = new HashMap<>();
	private final List<TagTab> liveTabs = new ArrayList<>();

	private ConfigManager configManager;
	private PluginManager pluginManager;
	private Injector injector;
	private TagManager tagManager;
	private TabManager tabManager;
	private BankSearch bankSearch;
	private LootingBagTagWriter writer;

	@Before
	public void setUp()
	{
		configManager = mock(ConfigManager.class);
		pluginManager = mock(PluginManager.class);
		injector = mock(Injector.class);
		tagManager = mock(TagManager.class);
		tabManager = mock(TabManager.class);
		bankSearch = mock(BankSearch.class);
		writer = new LootingBagTagWriter(configManager, pluginManager);

		when(configManager.getConfiguration(anyString(), anyString())).thenAnswer(invocation ->
			configuration.get(invocation.getArgument(0) + "." + invocation.getArgument(1)));
		Answer<Void> saveConfiguration = invocation ->
		{
			configuration.put(invocation.getArgument(0) + "." + invocation.getArgument(1),
				String.valueOf((Object) invocation.getArgument(2)));
			return null;
		};
		doAnswer(saveConfiguration).when(configManager).setConfiguration(anyString(), anyString(), anyString());
		doAnswer(saveConfiguration).when(configManager).setConfiguration(anyString(), anyString(), any(Object.class));

		when(pluginManager.getPlugins()).thenReturn(Collections.singletonList(new TestBankTagsPlugin(injector)));
		when(injector.getInstance(TagManager.class)).thenReturn(tagManager);
		when(injector.getInstance(TabManager.class)).thenReturn(tabManager);
		when(injector.getInstance(BankSearch.class)).thenReturn(bankSearch);

		when(tagManager.getItemsForTag(anyString())).thenAnswer(invocation ->
		{
			String tag = invocation.getArgument(0);
			return itemTags.entrySet().stream()
				.filter(entry -> entry.getValue().contains(tag))
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
		});
		doAnswer(invocation ->
		{
			String tag = invocation.getArgument(0);
			itemTags.values().forEach(tags -> tags.remove(tag));
			return null;
		}).when(tagManager).removeTag(anyString());
		doAnswer(invocation ->
		{
			Integer itemId = invocation.getArgument(0);
			String tag = invocation.getArgument(1);
			itemTags.computeIfAbsent(itemId, ignored -> new HashSet<>()).add(tag);
			return null;
		}).when(tagManager).addTag(anyInt(), anyString(), anyBoolean());

		when(tabManager.find(anyString())).thenAnswer(invocation ->
		{
			String name = invocation.getArgument(0);
			return liveTabs.stream().filter(tab -> tab.getTag().equals(name)).findFirst().orElse(null);
		});
		doAnswer(invocation ->
		{
			liveTabs.add(invocation.getArgument(0));
			return null;
		}).when(tabManager).add(any(TagTab.class));
	}

	@Test
	public void existingManagedTabIsAnEntirelyUnchangedNoOp()
	{
		configuration.put(OWNED_NAME_KEY, TAG_NAME);
		configuration.put(TAB_NAMES_KEY, "gear," + TAG_NAME);
		configuration.put("banktags.icon_" + TAG_NAME, "1234");
		itemTags.put(100, tags(TAG_NAME, "gear"));
		Map<String, String> originalConfiguration = new HashMap<>(configuration);

		assertFalse(writer.createIfMissing(ids(200, 300)));

		assertEquals(originalConfiguration, configuration);
		assertEquals(tags(TAG_NAME, "gear"), itemTags.get(100));
		assertFalse(itemTags.containsKey(200));
		verify(configManager).getConfiguration("banktags", "tagtabs");
		verify(configManager).getConfiguration("lootingbagtags", "managedTag");
		verifyNoMoreInteractions(configManager);
		verifyNoInteractions(pluginManager, injector, tagManager, tabManager, bankSearch);
	}

	@Test
	public void creatingATabKeepsOtherTabsIconsAndItemTags()
	{
		configuration.put(OWNED_NAME_KEY, TAG_NAME);
		configuration.put(TAB_NAMES_KEY, "gear,skilling");
		configuration.put("banktags.icon_gear", "9876");
		itemTags.put(100, tags("gear"));
		itemTags.put(300, tags("skilling"));

		assertTrue(writer.createIfMissing(ids(100, 200)));

		assertEquals(Arrays.asList("gear", "skilling", TAG_NAME), savedTabs());
		assertEquals("9876", configuration.get("banktags.icon_gear"));
		assertEquals(String.valueOf(ItemID.LOOTING_BAG), configuration.get("banktags.icon_" + TAG_NAME));
		assertEquals(tags("gear", TAG_NAME), itemTags.get(100));
		assertEquals(tags(TAG_NAME), itemTags.get(200));
		assertEquals(tags("skilling"), itemTags.get(300));
		ArgumentCaptor<TagTab> createdTab = ArgumentCaptor.forClass(TagTab.class);
		verify(tabManager).add(createdTab.capture());
		assertEquals(TAG_NAME, createdTab.getValue().getTag());
		assertEquals(ItemID.LOOTING_BAG, createdTab.getValue().getIconItemId());
		verify(bankSearch).layoutBank();
	}

	@Test
	public void firstUseChoosesANewNameWhenExistingUserTabsOrTagsCollide()
	{
		configuration.put(TAB_NAMES_KEY, TAG_NAME + ",gear," + TAG_NAME + " 2");
		configuration.put("banktags.icon_" + TAG_NAME, "9876");
		itemTags.put(100, tags(TAG_NAME, "gear"));
		itemTags.put(200, tags(TAG_NAME + " 3"));
		String chosenName = TAG_NAME + " 4";

		assertTrue(writer.createIfMissing(ids(300)));

		assertEquals(chosenName, writer.getTagName());
		assertEquals(chosenName, configuration.get(OWNED_NAME_KEY));
		assertEquals(Arrays.asList(TAG_NAME, "gear", TAG_NAME + " 2", chosenName), savedTabs());
		assertEquals("9876", configuration.get("banktags.icon_" + TAG_NAME));
		assertEquals(tags(TAG_NAME, "gear"), itemTags.get(100));
		assertEquals(tags(TAG_NAME + " 3"), itemTags.get(200));
		assertEquals(tags(chosenName), itemTags.get(300));
		verify(tagManager, never()).removeTag(TAG_NAME);
		verify(tagManager, never()).removeTag(TAG_NAME + " 3");
	}

	@Test
	public void deletingWithoutUntaggingThenRecreatingClearsOnlyOurOldMemberships()
	{
		configuration.put(OWNED_NAME_KEY, TAG_NAME);
		configuration.put(TAB_NAMES_KEY, "gear," + TAG_NAME);
		itemTags.put(100, tags(TAG_NAME, "gear"));
		itemTags.put(200, tags(TAG_NAME));
		itemTags.put(300, tags("supplies"));
		assertFalse(writer.createIfMissing(ids(400)));

		// RuneLite permits deleting a tab while retaining its item tags.
		configuration.put(TAB_NAMES_KEY, "gear");
		assertTrue(writer.createIfMissing(ids(300)));

		assertEquals(Arrays.asList("gear", TAG_NAME), savedTabs());
		assertEquals(tags("gear"), itemTags.get(100));
		assertTrue(itemTags.get(200).isEmpty());
		assertEquals(tags("supplies", TAG_NAME), itemTags.get(300));
		assertFalse(itemTags.containsKey(400));
		verify(tagManager).removeTag(TAG_NAME);
		verify(tagManager).addTag(300, TAG_NAME, false);
		verifyNoMoreInteractions(tagManager);
	}

	@Test
	public void emptyLiveManagerWhileBankClosedDoesNotErasePersistedTabs()
	{
		configuration.put(OWNED_NAME_KEY, TAG_NAME);
		configuration.put(TAB_NAMES_KEY, "gear,skilling,quests");
		assertTrue(liveTabs.isEmpty());

		assertTrue(writer.createIfMissing(ids(100)));

		assertEquals(Arrays.asList("gear", "skilling", "quests", TAG_NAME), savedTabs());
		assertEquals(1, liveTabs.size());
		assertEquals(TAG_NAME, liveTabs.get(0).getTag());
		verify(tabManager, never()).save();
	}

	private List<String> savedTabs()
	{
		return Text.fromCSV(configuration.get(TAB_NAMES_KEY));
	}

	private static Set<Integer> ids(Integer... itemIds)
	{
		return new HashSet<>(Arrays.asList(itemIds));
	}

	private static Set<String> tags(String... values)
	{
		return new HashSet<>(Arrays.asList(values));
	}

	/** Supplies Plugin's protected injector without reflection or mocking final methods. */
	private static final class TestBankTagsPlugin extends BankTagsPlugin
	{
		private TestBankTagsPlugin(Injector injector)
		{
			this.injector = injector;
		}
	}
}
