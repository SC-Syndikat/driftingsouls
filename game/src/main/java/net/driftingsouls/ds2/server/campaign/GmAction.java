package net.driftingsouls.ds2.server.campaign;

/**
 * What a game master did, as kept in the {@link GmActionLog}.
 */
public enum GmAction
{
	CAMPAIGN_START("Kampagne gestartet"),
	CAMPAIGN_TICK("Kampagnentick"),
	CAMPAIGN_END("Kampagne beendet"),
	SPAWN("Schiffe gespawnt"),
	ADD_SHIPS("Schiffe hinzugefügt"),
	HAND_OVER("Schiff übergeben"),
	REQUEST_ACTION("Aktion aus Anfrage"),
	REQUEST_TAKEOVER("Anfrage übernommen"),
	BATTLE_END("Schlacht beendet"),
	CAPITULATION("Kapitulation"),
	CONTROL("Steuermodus"),
	MASTER_LOGIN("Masterlogin"),
	TRUST_CORRECTION("Vertrauen korrigiert");

	private final String label;

	GmAction(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}
}
