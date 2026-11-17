package net.driftingsouls.ds2.server.framework.authentication;

import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Remembers when each user last made an authenticated request, to show who is online.
 *
 * <p>Held in memory only: there is no database write per request. After a server restart every user
 * counts as offline until their next request.
 */
public final class UserActivityTracker
{
	private static final UserActivityTracker INSTANCE = new UserActivityTracker(System::currentTimeMillis);

	private final Map<Integer, Long> lastSeenMillis = new ConcurrentHashMap<>();
	private final LongSupplier clock;

	UserActivityTracker(LongSupplier clock)
	{
		this.clock = clock;
	}

	public static UserActivityTracker getInstance()
	{
		return INSTANCE;
	}

	public void recordActivity(int userId)
	{
		lastSeenMillis.put(userId, clock.getAsLong());
	}

	/**
	 * @return Seconds since the user's last request, or empty if none was seen since the server started
	 */
	public OptionalLong secondsSinceLastActivity(int userId)
	{
		Long lastSeen = lastSeenMillis.get(userId);
		if (lastSeen == null)
		{
			return OptionalLong.empty();
		}
		return OptionalLong.of(Math.max(0, (clock.getAsLong() - lastSeen) / 1000));
	}

	public boolean isOnline(int userId, int thresholdSeconds)
	{
		OptionalLong seconds = secondsSinceLastActivity(userId);
		return seconds.isPresent() && seconds.getAsLong() <= thresholdSeconds;
	}
}
