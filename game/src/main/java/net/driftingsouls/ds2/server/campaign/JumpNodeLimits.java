package net.driftingsouls.ds2.server.campaign;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who and what may pass a campaign jump node. Empty sets and {@code null} limits mean no limit.
 */
public class JumpNodeLimits
{
	public enum Period
	{
		TOTAL("für die ganze Kampagne"),
		PER_TICK("pro Kampagnentick");

		private final String label;

		Period(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	/**
	 * What the point limits count.
	 */
	public enum Metric
	{
		SIZE("Größenpunkte (GP)"),
		BATTLE_VALUE("Schlachtwert (nur Kriegsschiffe)");

		private final String label;

		Metric(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	public static final JumpNodeLimits NONE = new JumpNodeLimits(Set.of(), Set.of(), null, null, Period.TOTAL, Metric.SIZE);

	/** Ship class names; empty: all. */
	public final Set<String> allowedShipClasses;
	/** Ship type ids; empty: all. */
	public final Set<Integer> allowedShipTypes;
	public final Integer maxPointsPerShip;
	public final Integer maxPointsPerPlayer;
	public final Period playerLimitPeriod;
	public final Metric metric;

	public JumpNodeLimits(Set<String> allowedShipClasses, Set<Integer> allowedShipTypes, Integer maxPointsPerShip, Integer maxPointsPerPlayer,
						  Period playerLimitPeriod, Metric metric)
	{
		this.allowedShipClasses = allowedShipClasses;
		this.allowedShipTypes = allowedShipTypes;
		this.maxPointsPerShip = maxPointsPerShip;
		this.maxPointsPerPlayer = maxPointsPerPlayer;
		this.playerLimitPeriod = playerLimitPeriod;
		this.metric = metric;
	}

	public boolean isUnlimited()
	{
		return allowedShipClasses.isEmpty() && allowedShipTypes.isEmpty() && maxPointsPerShip == null && maxPointsPerPlayer == null;
	}

	public JumpNodeLimits withAllowedShipClasses(Set<String> classes)
	{
		return new JumpNodeLimits(classes, allowedShipTypes, maxPointsPerShip, maxPointsPerPlayer, playerLimitPeriod, metric);
	}

	public JumpNodeLimits withAllowedShipTypes(Set<Integer> types)
	{
		return new JumpNodeLimits(allowedShipClasses, types, maxPointsPerShip, maxPointsPerPlayer, playerLimitPeriod, metric);
	}

	public JumpNodeLimits withMaxPointsPerShip(Integer points)
	{
		return new JumpNodeLimits(allowedShipClasses, allowedShipTypes, points, maxPointsPerPlayer, playerLimitPeriod, metric);
	}

	public JumpNodeLimits withMaxPointsPerPlayer(Integer points)
	{
		return new JumpNodeLimits(allowedShipClasses, allowedShipTypes, maxPointsPerShip, points, playerLimitPeriod, metric);
	}

	public JumpNodeLimits withPeriod(Period period)
	{
		return new JumpNodeLimits(allowedShipClasses, allowedShipTypes, maxPointsPerShip, maxPointsPerPlayer, period, metric);
	}

	public JumpNodeLimits withMetric(Metric newMetric)
	{
		return new JumpNodeLimits(allowedShipClasses, allowedShipTypes, maxPointsPerShip, maxPointsPerPlayer, playerLimitPeriod, newMetric);
	}

	/**
	 * @return A short description for editors and messages
	 */
	public String describe()
	{
		if (isUnlimited())
		{
			return "keine";
		}
		StringBuilder text = new StringBuilder();
		if (!allowedShipClasses.isEmpty())
		{
			text.append("Klassen ").append(String.join(", ", allowedShipClasses)).append("; ");
		}
		if (!allowedShipTypes.isEmpty())
		{
			text.append("Typen ").append(allowedShipTypes.stream().map(String::valueOf).collect(Collectors.joining(", "))).append("; ");
		}
		if (maxPointsPerShip != null)
		{
			text.append("max. ").append(maxPointsPerShip).append(" je Schiff; ");
		}
		if (maxPointsPerPlayer != null)
		{
			text.append("max. ").append(maxPointsPerPlayer).append(" je Spieler ").append(playerLimitPeriod.getLabel()).append("; ");
		}
		text.append("gezählt: ").append(metric.getLabel());
		return text.toString();
	}

	static Set<String> parseNames(String csv)
	{
		return Arrays.stream(csv == null ? new String[0] : csv.split("[,;\\s]+"))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	static Set<Integer> parseIds(String csv)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (String part : parseNames(csv))
		{
			try
			{
				ids.add(Integer.parseInt(part));
			}
			catch (NumberFormatException e)
			{
				throw new CampaignException("Ungültige Schiffstyp-ID: " + part);
			}
		}
		return ids;
	}
}
