package net.driftingsouls.ds2.server.campaign;

import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.db.DBUtil;
import org.jooq.DSLContext;

import java.sql.SQLException;
import java.util.function.Function;

/**
 * Runs jOOQ work on the current request's connection, and so inside its transaction.
 */
public final class CampaignDb
{
	private CampaignDb()
	{
		// static only
	}

	public static <T> T withDb(Function<DSLContext, T> work)
	{
		try (var conn = DBUtil.getConnection(ContextMap.getContext().getEM()))
		{
			return work.apply(DBUtil.getDSLContext(conn));
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
}
