package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.UserFlag;
import net.driftingsouls.ds2.server.framework.BasicUser;
import net.driftingsouls.ds2.server.framework.PermissionDescriptor;
import net.driftingsouls.ds2.server.framework.PermissionResolver;

import java.util.EnumSet;
import java.util.Set;

/**
 * Grants a game master the admin permissions the campaign needs, on top of whatever the inner resolver
 * grants.
 *
 * <p>Only permissions whose plugin restricts non-admins to campaign objects (campaign star systems,
 * participating NPC accounts) belong here. Anything without such a restriction would give the game
 * master more than the campaign needs.
 */
public class GameMasterPermissionResolver implements PermissionResolver
{
	static final Set<WellKnownAdminPermission> GRANTED = EnumSet.of(
			WellKnownAdminPermission.SICHTBAR,
			WellKnownAdminPermission.CAMPAIGN_PARTICIPANTS,
			WellKnownAdminPermission.CAMPAIGN_SYSTEMS,
			WellKnownAdminPermission.CAMPAIGN_JUMPNODES
	);

	private final PermissionResolver inner;
	private final boolean gameMaster;

	public GameMasterPermissionResolver(PermissionResolver inner, boolean gameMaster)
	{
		this.inner = inner;
		this.gameMaster = gameMaster;
	}

	/**
	 * @param user The user, or {@code null}
	 * @return Whether the user carries the {@link UserFlag#GAMEMASTER} flag
	 */
	public static boolean isGameMaster(BasicUser user)
	{
		return user instanceof User && ((User) user).hasFlag(UserFlag.GAMEMASTER);
	}

	@Override
	public boolean hasPermission(PermissionDescriptor permission)
	{
		if (gameMaster && permission instanceof WellKnownAdminPermission && GRANTED.contains(permission))
		{
			return true;
		}
		return inner.hasPermission(permission);
	}
}
