package net.driftingsouls.ds2.server.campaign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fits cargo or units of a ship group template entry into the room a ship has: whatever does not fit
 * is taken from the last entry, then the one before, and so on.
 */
public final class ShipGroupLimits
{
	private ShipGroupLimits()
	{
		// static only
	}

	/**
	 * Something loaded on the ship: how many, and how much room one takes.
	 */
	public static class Load<K>
	{
		public final K key;
		public final String label;
		public final long count;
		public final long size;

		public Load(K key, String label, long count, long size)
		{
			this.key = key;
			this.label = label;
			this.count = count;
			this.size = size;
		}
	}

	/**
	 * What remains after trimming, in the original order, and what was taken away.
	 */
	public static class Result<K>
	{
		public final List<Load<K>> loads;
		/** One sentence per entry reduced or removed. */
		public final List<String> warnings;

		Result(List<Load<K>> loads, List<String> warnings)
		{
			this.loads = loads;
			this.warnings = warnings;
		}

		public boolean isChanged()
		{
			return !warnings.isEmpty();
		}
	}

	/**
	 * @param loads What is loaded, in the order entered
	 * @param capacity The room there is
	 * @param what What is loaded, for the warnings, e.g. "Cargo"
	 */
	public static <K> Result<K> trim(List<Load<K>> loads, long capacity, String what)
	{
		long total = 0;
		for (Load<K> load : loads)
		{
			total += load.count * load.size;
		}

		List<Load<K>> kept = new ArrayList<>(loads);
		List<String> warnings = new ArrayList<>();
		for (int i = kept.size() - 1; i >= 0 && total > Math.max(0, capacity); i--)
		{
			Load<K> load = kept.get(i);
			if (load.size <= 0 || load.count <= 0)
			{
				continue;
			}
			long excess = total - Math.max(0, capacity);
			long reduceBy = Math.min(load.count, (excess + load.size - 1) / load.size);
			long newCount = load.count - reduceBy;
			total -= reduceBy * load.size;
			if (newCount == 0)
			{
				kept.remove(i);
				warnings.add(what + ": " + load.label + " (" + load.count + ") entfernt.");
			}
			else
			{
				kept.set(i, new Load<>(load.key, load.label, newCount, load.size));
				warnings.add(what + ": " + load.label + " von " + load.count + " auf " + newCount + " reduziert.");
			}
		}
		return new Result<>(Collections.unmodifiableList(kept), warnings);
	}
}
