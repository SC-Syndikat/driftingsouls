package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.WellKnownConfigValue;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.framework.authentication.UserActivityTracker;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;

/**
 * A user as the game master sees them when choosing and reviewing campaign participants.
 */
public class CampaignUserView
{
	public final int id;
	public final String plainName;
	/** The name with its BBCode rendered as HTML. */
	public final String formattedName;
	public final String allianceName;
	public final boolean npc;
	public final boolean gamemaster;
	public final boolean eligible;
	public final int inactivity;
	public final boolean tickReady;
	public final boolean online;
	public final String lastSeen;

	private CampaignUserView(User user, UserActivityTracker tracker, int onlineThresholdSeconds)
	{
		this.id = user.getId();
		this.plainName = user.getPlainname();
		this.formattedName = user.getFormatedName();
		this.allianceName = user.getAlly() != null ? user.getAlly().getPlainname() : "";
		this.npc = user.isNPC();
		this.gamemaster = user.hasFlag(UserFlag.GAMEMASTER);
		this.eligible = CampaignService.isEligible(user);
		this.inactivity = user.getInactivity();
		this.tickReady = Boolean.TRUE.equals(user.isTickReady());
		this.online = tracker.isOnline(user.getId(), onlineThresholdSeconds);
		this.lastSeen = describeLastSeen(tracker.secondsSinceLastActivity(user.getId()), online);
	}

	public static List<CampaignUserView> of(Collection<User> users, EntityManager em)
	{
		UserActivityTracker tracker = UserActivityTracker.getInstance();
		int threshold = new ConfigService(em).getValue(WellKnownConfigValue.ONLINE_THRESHOLD_SECONDS);

		List<CampaignUserView> views = new ArrayList<>();
		for (User user : users)
		{
			views.add(new CampaignUserView(user, tracker, threshold));
		}
		return views;
	}

	static String describeLastSeen(OptionalLong secondsSince, boolean online)
	{
		if (online)
		{
			return "online";
		}
		if (secondsSince.isEmpty())
		{
			return "seit dem Serverstart nicht gesehen";
		}
		long minutes = secondsSince.getAsLong() / 60;
		if (minutes < 60)
		{
			return "zuletzt aktiv vor " + minutes + " Min.";
		}
		long hours = minutes / 60;
		if (hours < 48)
		{
			return "zuletzt aktiv vor " + hours + " Std.";
		}
		return "zuletzt aktiv vor " + (hours / 24) + " Tagen";
	}
}
