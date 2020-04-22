package com.denaliai.fw.utility.concurrent;


import com.denaliai.fw.Application;
import io.netty.channel.EventLoopGroup;

import java.util.concurrent.atomic.AtomicLong;

public abstract class PerpetualWork implements Runnable
{
	private final AtomicLong m_currentWorkTick = new AtomicLong();
	private final EventLoopGroup m_eventGroup;

	public PerpetualWork() {
		m_eventGroup = Application.getTaskPool();
	}

	public PerpetualWork(EventLoopGroup eventGroup) {
		m_eventGroup = eventGroup;
	}

	/*
	 *
	 * Support multiple threads calling this method
	 *
	 */
	public void requestMoreWork()
	{
		final long currentRequestTick = m_currentWorkTick.get();

		// The trick here is that m_currentWorkTick only needs to change, it does not
		// indicate a number of items to do or a count of things to process.  This is just
		// a housekeeping number which must change to keep the worker running
		if (m_currentWorkTick.compareAndSet(currentRequestTick, currentRequestTick+1))
		{
			if (currentRequestTick == 0)
			{
				// work is not scheduled to run
				Application.getTaskPool().submit(this);
			}
			else
			{
				// Updated successfully, currently running work will pick this up
			}
		}
		else if (m_currentWorkTick.compareAndSet(0, currentRequestTick+1))
		{
			// work is not scheduled to run
			Application.getTaskPool().submit(this);
		}
		else
		{
			// This conditions happens when there are multiple threads calling requestMoreWork
			// Only one can win to be considered the "owner" of submitting this work item
			// The one that loses, can silently just return it has nothing to do
		}
	}

	abstract protected void _doWork();

	@Override
	public final void run()
	{
		final long lastRequestTick = m_currentWorkTick.get();
		try {
			_doWork();
		} finally {
			// Set tick to 0 only if it matches the starting tick
			if (m_currentWorkTick.compareAndSet(lastRequestTick, 0) == false)
			{
				// Starting tick didn't match, so we got another request for work while running
				Application.getTaskPool().submit(this);
			}
			else
			{
				// No work requested, stopping work
			}
		}
	}

}