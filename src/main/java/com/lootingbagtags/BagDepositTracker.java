package com.lootingbagtags;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Matches item losses from a looting bag with item gains in an open bank.
 *
 * <p>Both maps must use canonical, unnoted item IDs. Call {@link #detect} once
 * after all container changes for a game tick have been applied, rather than
 * once for each individual container event. The order in which the bank and
 * bag events arrived then makes no difference.</p>
 *
 * <p>A null map means that the container is unknown, not empty. Unknown data,
 * opening the bank, and resetting the tracker establish a fresh baseline and
 * never report a deposit. No unmatched increases or decreases are carried
 * across observations: an unrelated later bank deposit must not get matched
 * against an old bag loss.</p>
 */
public final class BagDepositTracker
{
	private Map<Integer, Integer> previousBag;
	private Map<Integer, Integer> previousBank;
	private boolean bankWasOpen;

	/**
	 * Seeds the before-action snapshot without reporting any deposited items.
	 * This is useful when the bank first becomes available, before the player
	 * sends a deposit action. It is safe to call with unknown containers.
	 */
	public void prime(Map<Integer, Integer> bag, Map<Integer, Integer> bank, boolean bankOpen)
	{
		previousBag = snapshot(bag);
		previousBank = bankOpen ? snapshot(bank) : null;
		bankWasOpen = bankOpen;
	}

	/**
	 * Returns the item types observed moving from the bag into the bank.
	 * The result is immutable and is empty for an unconfirmed change.
	 */
	public Set<Integer> detect(Map<Integer, Integer> bag, Map<Integer, Integer> bank, boolean bankOpen)
	{
		Map<Integer, Integer> currentBag = snapshot(bag);
		Map<Integer, Integer> currentBank = bankOpen ? snapshot(bank) : null;
		Set<Integer> deposited = new TreeSet<>();

		if (bankOpen && bankWasOpen
			&& previousBag != null && currentBag != null
			&& previousBank != null && currentBank != null)
		{
			for (Map.Entry<Integer, Integer> entry : previousBag.entrySet())
			{
				int itemId = entry.getKey();
				long bagLoss = (long) entry.getValue() - currentBag.getOrDefault(itemId, 0);
				long bankGain = (long) currentBank.getOrDefault(itemId, 0)
					- previousBank.getOrDefault(itemId, 0);

				if (Math.min(bagLoss, bankGain) > 0)
				{
					deposited.add(itemId);
				}
			}
		}

		previousBag = currentBag;
		previousBank = currentBank;
		bankWasOpen = bankOpen;

		return deposited.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(deposited);
	}

	/** Discards snapshots on logout, world hopping, or plugin shutdown. */
	public void reset()
	{
		previousBag = null;
		previousBank = null;
		bankWasOpen = false;
	}

	private static Map<Integer, Integer> snapshot(Map<Integer, Integer> items)
	{
		if (items == null)
		{
			return null;
		}

		Map<Integer, Integer> copy = new HashMap<>();
		for (Map.Entry<Integer, Integer> entry : items.entrySet())
		{
			if (entry.getKey() != null && entry.getKey() >= 0
				&& entry.getValue() != null && entry.getValue() > 0)
			{
				copy.put(entry.getKey(), entry.getValue());
			}
		}
		return copy;
	}
}
