package net.driftingsouls.ds2.server.campaign;

/**
 * A campaign run by a game master. Times are in seconds, as returned by {@code Common.time()}.
 */
public class Campaign
{
	private final int id;
	private final String name;
	private final Integer gamemasterId;
	private final CampaignStatus status;
	private final long createdAt;
	private final Long startedAt;
	private final Long endedAt;
	private final int tickCount;

	public Campaign(int id, String name, Integer gamemasterId, CampaignStatus status, long createdAt, Long startedAt, Long endedAt, int tickCount)
	{
		this.id = id;
		this.name = name;
		this.gamemasterId = gamemasterId;
		this.status = status;
		this.createdAt = createdAt;
		this.startedAt = startedAt;
		this.endedAt = endedAt;
		this.tickCount = tickCount;
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	/**
	 * @return The game master's user id, or {@code null} if that account has been deleted
	 */
	public Integer getGamemasterId()
	{
		return gamemasterId;
	}

	public CampaignStatus getStatus()
	{
		return status;
	}

	public long getCreatedAt()
	{
		return createdAt;
	}

	public Long getStartedAt()
	{
		return startedAt;
	}

	public Long getEndedAt()
	{
		return endedAt;
	}

	public int getTickCount()
	{
		return tickCount;
	}

	public boolean isPreparing()
	{
		return status == CampaignStatus.PREPARING;
	}

	public boolean isRunning()
	{
		return status == CampaignStatus.RUNNING;
	}
}
