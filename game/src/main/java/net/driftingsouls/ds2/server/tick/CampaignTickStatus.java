package net.driftingsouls.ds2.server.tick;

import net.driftingsouls.ds2.server.framework.Common;

/**
 * How far the last campaign tick has got, for the game master's menu. Held in memory only: the tick runs
 * in this JVM, and after a restart there is no tick in progress to report.
 */
public final class CampaignTickStatus
{
	public enum State
	{
		NONE,
		STARTED,
		RUNNING,
		FINISHED
	}

	private static volatile CampaignTickStatus current = new CampaignTickStatus(State.NONE, null, 0, 0);

	private final State state;
	private final String step;
	private final long startedAt;
	private final long finishedAt;

	private CampaignTickStatus(State state, String step, long startedAt, long finishedAt)
	{
		this.state = state;
		this.step = step;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
	}

	/**
	 * The game master has triggered the tick; it waits for the scheduler.
	 */
	public static void started()
	{
		current = new CampaignTickStatus(State.STARTED, null, Common.time(), 0);
	}

	/**
	 * The tick has begun, or moved on to the given step.
	 * @param step The step being computed, or {@code null} while there is none yet
	 */
	public static void running(String step)
	{
		CampaignTickStatus status = current;
		long startedAt = status.state == State.STARTED || status.state == State.RUNNING ? status.startedAt : Common.time();
		current = new CampaignTickStatus(State.RUNNING, step, startedAt, 0);
	}

	public static void finished()
	{
		current = new CampaignTickStatus(State.FINISHED, null, current.startedAt, Common.time());
	}

	/**
	 * Forgets a start the scheduler refused, so it is not reported as pending.
	 */
	public static void reset()
	{
		current = new CampaignTickStatus(State.NONE, null, 0, 0);
	}

	public static CampaignTickStatus get()
	{
		return current;
	}

	public State getState()
	{
		return state;
	}

	/**
	 * @return The step being computed while the tick runs, otherwise {@code null}
	 */
	public String getStep()
	{
		return step;
	}

	public long getStartedAt()
	{
		return startedAt;
	}

	public long getFinishedAt()
	{
		return finishedAt;
	}

}
