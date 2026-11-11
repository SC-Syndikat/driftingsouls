package net.driftingsouls.ds2.server.campaign.interaction;

import net.driftingsouls.ds2.server.campaign.dialog.ObjectType;

import java.util.Set;

/**
 * An object offering a dialog to players: object + dialog + who may use it how often.
 */
public class Interaction
{
	public enum Audience
	{
		ALL("alle Teilnehmer"),
		RESTRICTED("nur bestimmte Spieler / Allianzen");

		private final String label;

		Audience(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	public final int id;
	/** What the player clicks, e.g. "Mit dem Admiral sprechen". */
	public final String label;
	public final ObjectType objectType;
	public final int objectId;
	public final int startNodeId;
	/** The NPC speaking. */
	public final int npcUserId;
	public final boolean active;
	/** {@code null}: unlimited. */
	public final Integer maxUsesPerUser;
	/** {@code null}: none. */
	public final Integer cooldownTicks;
	public final Audience audience;
	public final Set<Integer> audienceUserIds;
	public final Set<Integer> audienceAllianceIds;

	public Interaction(int id, String label, ObjectType objectType, int objectId, int startNodeId, int npcUserId, boolean active,
					   Integer maxUsesPerUser, Integer cooldownTicks, Audience audience, Set<Integer> audienceUserIds, Set<Integer> audienceAllianceIds)
	{
		this.id = id;
		this.label = label;
		this.objectType = objectType;
		this.objectId = objectId;
		this.startNodeId = startNodeId;
		this.npcUserId = npcUserId;
		this.active = active;
		this.maxUsesPerUser = maxUsesPerUser;
		this.cooldownTicks = cooldownTicks;
		this.audience = audience;
		this.audienceUserIds = audienceUserIds;
		this.audienceAllianceIds = audienceAllianceIds;
	}

	/**
	 * @param allianceId The user's alliance, or {@code null}
	 */
	public boolean isForAudience(int userId, Integer allianceId)
	{
		return audience == Audience.ALL
				|| audienceUserIds.contains(userId)
				|| (allianceId != null && audienceAllianceIds.contains(allianceId));
	}
}
