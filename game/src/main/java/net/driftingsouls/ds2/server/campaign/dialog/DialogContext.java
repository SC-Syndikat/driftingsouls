package net.driftingsouls.ds2.server.campaign.dialog;

import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.campaign.Campaign;
import net.driftingsouls.ds2.server.campaign.CampaignException;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.ships.Ship;

import javax.persistence.EntityManager;
import java.util.function.DoubleSupplier;

/**
 * Everything a dialog factor or action may read or change: the player and the ship they talk from, the
 * NPC they talk to, the object offering the dialog and the campaign.
 */
public class DialogContext
{
	public final EntityManager em;
	public final User user;
	public final Ship ship;
	public final User npc;
	public final ObjectType objectType;
	public final int objectId;
	/** {@code null} outside a campaign, e.g. in a preview while none is open. */
	public final Campaign campaign;
	/** {@code null} in a preview of a dialog without interaction. */
	public final Integer interactionId;
	/** The game master behind the choice, or the player themselves. */
	public final User actor;
	public final TrustService trust;
	public final CampaignVariableService variables;
	/** Uniform in [0, 100). */
	public final DoubleSupplier random;

	public DialogContext(EntityManager em, User user, Ship ship, User npc, ObjectType objectType, int objectId,
						 Campaign campaign, Integer interactionId, User actor, DoubleSupplier random)
	{
		this.em = em;
		this.user = user;
		this.ship = ship;
		this.npc = npc;
		this.objectType = objectType;
		this.objectId = objectId;
		this.campaign = campaign;
		this.interactionId = interactionId;
		this.actor = actor;
		this.trust = new TrustService(em);
		this.variables = new CampaignVariableService();
		this.random = random;
	}

	/**
	 * @return The ship offering the dialog
	 * @throws CampaignException if the dialog is not offered by a ship
	 */
	public Ship requireObjectShip()
	{
		Ship target = objectType == ObjectType.SHIP ? em.find(Ship.class, objectId) : null;
		if (target == null)
		{
			throw new CampaignException("Diese Aktion braucht ein Schiff als Gesprächspartner.");
		}
		return target;
	}

	/**
	 * @return The base offering the dialog, or {@code null} if it is not offered by a base
	 */
	public Base objectBase()
	{
		return objectType == ObjectType.BASE ? em.find(Base.class, objectId) : null;
	}

	public Campaign requireCampaign()
	{
		if (campaign == null)
		{
			throw new CampaignException("Diese Aktion ist nur während einer Kampagne möglich.");
		}
		return campaign;
	}

	public int campaignTicks()
	{
		return campaign == null ? 0 : campaign.getTickCount();
	}
}
