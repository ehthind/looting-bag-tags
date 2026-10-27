package com.lootingbagtags;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BagDepositTrackerTest
{
	@Test
	public void reportsOnlyBagItemsThatActuallyArrivedInBank()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37, 200, 2), items(100, 500, 300, 25), true);

		assertEquals(ids(100, 200), tracker.detect(items(), items(100, 537, 200, 2, 300, 30), true));
	}

	@Test
	public void ignoresOrdinaryInventoryDeposits()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37), items(100, 500), true);

		assertTrue(tracker.detect(items(100, 37), items(100, 537, 200, 2), true).isEmpty());
	}

	@Test
	public void ignoresBagLossesWithoutMatchingBankGains()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37, 200, 2), items(100, 500), true);

		assertEquals(ids(100), tracker.detect(items(), items(100, 537, 300, 2), true));
	}

	@Test
	public void handlesPartialBagDepositsAndDoesNotRepeatThem()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37), items(100, 500), true);

		assertEquals(ids(100), tracker.detect(items(100, 27), items(100, 510), true));
		assertTrue(tracker.detect(items(100, 27), items(100, 510), true).isEmpty());
		assertEquals(ids(100), tracker.detect(items(), items(100, 537), true));
	}

	@Test
	public void acceptsOnlyTheMatchingPartOfABagLoss()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37), items(100, 500), true);

		assertEquals(ids(100), tracker.detect(items(), items(100, 501), true));
	}

	@Test
	public void emptyBagContainerIsNotTheSameAsUnknownBagContainer()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37), items(100, 500), true);

		assertTrue(tracker.detect(null, items(100, 537), true).isEmpty());
		assertTrue(tracker.detect(items(), items(100, 537), true).isEmpty());
	}

	@Test
	public void doesNotAttributeAChangeAcrossAnUnknownBankSnapshot()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37), items(100, 500), true);

		assertTrue(tracker.detect(items(100, 37), null, true).isEmpty());
		assertTrue(tracker.detect(items(), items(100, 537), true).isEmpty());
	}

	@Test
	public void firstSnapshotNeverReportsHistoricalLoot()
	{
		BagDepositTracker tracker = new BagDepositTracker();

		assertTrue(tracker.detect(items(100, 37), items(100, 500), true).isEmpty());
		assertEquals(ids(100), tracker.detect(items(), items(100, 537), true));
	}

	@Test
	public void bagLossOutsideBankAndLaterInventoryDepositAreNotLootBagDeposits()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.detect(items(100, 37), null, false);
		assertTrue(tracker.detect(items(), null, false).isEmpty());
		assertTrue(tracker.detect(items(), items(100, 500), true).isEmpty());
		assertTrue(tracker.detect(items(), items(100, 537), true).isEmpty());
	}

	@Test
	public void eachBankVisitNeedsAFreshBeforeDepositSnapshot()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37), items(100, 500), true);
		assertEquals(ids(100), tracker.detect(items(), items(100, 537), true));

		tracker.detect(items(200, 2), items(100, 537), false);
		assertTrue(tracker.detect(items(200, 2), items(100, 537), true).isEmpty());
		assertEquals(ids(200), tracker.detect(items(), items(100, 537, 200, 2), true));
	}

	@Test
	public void resetPreventsCrossAccountOrWorldHopMatches()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37), items(100, 500), true);
		tracker.reset();

		assertTrue(tracker.detect(items(), items(100, 537), true).isEmpty());
	}

	@Test
	public void unmatchedChangesDoNotCarryForwardIntoUnrelatedLaterChanges()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37), items(100, 500), true);

		assertTrue(tracker.detect(items(), items(100, 500), true).isEmpty());
		assertTrue(tracker.detect(items(), items(100, 537), true).isEmpty());
	}

	@Test
	public void copiesCallerOwnedMapsInsteadOfKeepingLiveViews()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		Map<Integer, Integer> bag = items(100, 37);
		Map<Integer, Integer> bank = items(100, 500);
		tracker.prime(bag, bank, true);

		bag.clear();
		bank.put(100, 537);
		assertEquals(ids(100), tracker.detect(bag, bank, true));
	}

	@Test
	public void ignoresEmptySlotsAndInvalidEntries()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		Map<Integer, Integer> bag = items(-1, 1, 100, 37, 200, 0, 300, -1);
		bag.put(null, 12);
		bag.put(400, null);
		tracker.prime(bag, items(100, 500), true);

		assertEquals(ids(100), tracker.detect(items(), items(100, 537), true));
	}

	@Test(expected = UnsupportedOperationException.class)
	public void returnedItemTypesCannotBeMutated()
	{
		BagDepositTracker tracker = new BagDepositTracker();
		tracker.prime(items(100, 37), items(), true);
		tracker.detect(items(), items(100, 37), true).add(200);
	}

	private static Map<Integer, Integer> items(int... pairs)
	{
		Map<Integer, Integer> items = new HashMap<>();
		for (int index = 0; index < pairs.length; index += 2)
		{
			items.put(pairs[index], pairs[index + 1]);
		}
		return items;
	}

	private static Set<Integer> ids(Integer... values)
	{
		return new HashSet<>(Arrays.asList(values));
	}
}
