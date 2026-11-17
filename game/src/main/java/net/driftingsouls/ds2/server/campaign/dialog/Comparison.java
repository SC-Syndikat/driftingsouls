package net.driftingsouls.ds2.server.campaign.dialog;

/**
 * How a condition compares a factor's value with its threshold.
 */
public enum Comparison
{
	GE(">=", "mindestens"),
	LE("<=", "höchstens"),
	GT(">", "mehr als"),
	LT("<", "weniger als"),
	EQ("==", "genau"),
	NE("!=", "nicht");

	private final String symbol;
	private final String label;

	Comparison(String symbol, String label)
	{
		this.symbol = symbol;
		this.label = label;
	}

	public String getSymbol()
	{
		return symbol;
	}

	public String getLabel()
	{
		return label;
	}

	public boolean test(double value, double threshold)
	{
		switch (this)
		{
			case GE:
				return value >= threshold;
			case LE:
				return value <= threshold;
			case GT:
				return value > threshold;
			case LT:
				return value < threshold;
			case EQ:
				return Math.abs(value - threshold) < 1e-9;
			default:
				return Math.abs(value - threshold) >= 1e-9;
		}
	}

	public static Comparison fromSymbol(String symbol)
	{
		for (Comparison comparison : values())
		{
			if (comparison.symbol.equals(symbol))
			{
				return comparison;
			}
		}
		throw new IllegalArgumentException("Unbekannter Vergleich: " + symbol);
	}
}
