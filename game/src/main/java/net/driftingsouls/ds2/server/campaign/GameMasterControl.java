package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.framework.BasicUser;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.authentication.JavaSession;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lets the game master steer the campaign's NPCs without logging in as them: one click switches the
 * session to a participating NPC, the game master stays attached behind it, and another click switches back.
 * While he steers an NPC this way, its ships and bases act only in campaign star systems.
 */
public final class GameMasterControl
{
	private GameMasterControl()
	{
		// static only
	}

	/**
	 * Remembers in the session that the NPC is steered through this switch, not through a master login.
	 */
	public static class State implements Serializable
	{
		private static final long serialVersionUID = 1L;

		boolean active;

		public State()
		{
			// for the session
		}
	}

	/**
	 * @return The person behind the session: the game master while he steers an NPC, otherwise the active user
	 */
	public static BasicUser realUser()
	{
		BasicUser active = ContextMap.getContext().getActiveUser();
		if (active == null)
		{
			return null;
		}
		return active.getAttachedUser() != null ? active.getAttachedUser() : active;
	}

	/**
	 * @return Whether the user may steer the campaign's NPCs: a game master or an admin
	 */
	public static boolean mayControl(BasicUser user)
	{
		return user != null && (GameMasterPermissionResolver.isGameMaster(user) || user.getAccessLevel() >= 30);
	}

	/**
	 * @return The NPCs taking part in the running campaign, which the game master may steer
	 */
	public static List<User> controllableNpcs()
	{
		Context context = ContextMap.getContext();
		Optional<Campaign> campaign = new CampaignRepository().findOpen().filter(Campaign::isRunning);
		List<User> npcs = new ArrayList<>();
		if (campaign.isEmpty())
		{
			return npcs;
		}
		Set<Integer> ids = new TreeSet<>(new CampaignRepository().findParticipantIds(campaign.get().getId()));
		for (int id : ids)
		{
			User user = id < 0 ? context.getEM().find(User.class, id) : null;
			if (user != null)
			{
				npcs.add(user);
			}
		}
		return npcs;
	}

	/**
	 * @return Whether the game master steers an NPC through this switch right now
	 */
	public static boolean isControlling()
	{
		Context context = ContextMap.getContext();
		BasicUser active = context.getActiveUser();
		if (active == null || active.getAttachedUser() == null || active.getId() >= 0)
		{
			return false;
		}
		State state = context.getRequest().getFromSession(State.class);
		return state != null && state.active;
	}

	/**
	 * Steers the NPC, or goes back to the game master's own account when the id is his.
	 */
	public static void switchTo(int userId)
	{
		Context context = ContextMap.getContext();
		BasicUser real = realUser();
		if (!mayControl(real))
		{
			throw new CampaignException("Nur der GameMaster kann NPCs steuern.");
		}
		JavaSession session = context.get(JavaSession.class);
		State state = context.getRequest().getFromSession(State.class);
		if (userId == real.getId())
		{
			session.setUser(real);
			session.setAttach(null);
			if (state != null)
			{
				state.active = false;
			}
			return;
		}
		User npc = controllableNpcs().stream().filter(u -> u.getId() == userId).findFirst()
				.orElseThrow(() -> new CampaignException("Steuern kannst du nur NPCs, die an der laufenden Kampagne teilnehmen."));
		session.setUser(npc);
		session.setAttach(real);
		if (state != null)
		{
			state.active = true;
		}
	}
}
