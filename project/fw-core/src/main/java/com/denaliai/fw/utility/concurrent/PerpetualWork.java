package com.denaliai.fw.utility.concurrent;


import com.denaliai.fw.Application;
import io.netty.channel.EventLoopGroup;

import java.util.concurrent.atomic.AtomicLong;

public abstract class PerpetualWork
{
	private final AtomicLong m_currentWorkTick = new AtomicLong();
	private final Runner m_runner = new Runner();
	private final EventLoopGroup m_eventGroup;
	private final int m_maxNumWorkPerRun;
	private volatile Thread m_currentWorker;

	public PerpetualWork() {
		this(Application.getTaskPool(), 1);
	}

	public PerpetualWork(int maxNumWorkPerRun) {
		this(Application.getTaskPool(), maxNumWorkPerRun);
	}

	public PerpetualWork(EventLoopGroup eventGroup) {
		this(eventGroup, 1);
	}

	public PerpetualWork(EventLoopGroup eventGroup, int maxNumWorkPerRun) {
		m_maxNumWorkPerRun = maxNumWorkPerRun;
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
				if (m_currentWorker != Thread.currentThread()) {
					// We are not already in the loop
					Application.getTaskPool().execute(m_runner);
				}
			}
			else
			{
				// Updated successfully, currently running work will pick this up
			}
		}
		else if (m_currentWorkTick.compareAndSet(0, currentRequestTick+1))
		{
			// work is not scheduled to run
			if (m_currentWorker != Thread.currentThread()) {
				// We are not already in the loop
				Application.getTaskPool().execute(m_runner);
			}
		}
		else
		{
			// This conditions happens when there are multiple threads calling requestMoreWork
			// Only one can win to be considered the "owner" of submitting this work item
			// The one that loses, can silently just return it has nothing to do
		}
	}

	public void runMoreWork()
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
				if (m_currentWorker != Thread.currentThread()) {
					// We are not already in the loop
					m_runner.run();
				}
			}
			else
			{
				// Updated successfully, currently running work will pick this up
			}
		}
		else if (m_currentWorkTick.compareAndSet(0, currentRequestTick+1))
		{
			// work is not scheduled to run
			if (m_currentWorker != Thread.currentThread()) {
				// We are not already in the loop
				m_runner.run();
			}
		}
		else
		{
			// This conditions happens when there are multiple threads calling requestMoreWork
			// Only one can win to be considered the "owner" of submitting this work item
			// The one that loses, can silently just return it has nothing to do
		}
	}

	abstract protected void _doWork();

	private final class Runner implements Runnable {

		private boolean run0(int numProcessed) {
			Thread saveWorker = m_currentWorker;
			final long lastRequestTick = m_currentWorkTick.get();
			try {
				_doWork();
			} finally {
				m_currentWorker = null;
				// Set tick to 0 only if it matches the starting tick
				if (m_currentWorkTick.compareAndSet(lastRequestTick, 0) == false)
				{
					// Starting tick didn't match, so we got another request for work while running
					if (numProcessed >= m_maxNumWorkPerRun) {
						Application.getTaskPool().execute(this);
						return false;
					} else {
						// We are staying in the loop, restore so we don't have to hit API
						m_currentWorker = saveWorker;
						return true;
					}
				}
				else
				{
					// No work requested, stopping work
					return false;
				}
			}
		}

		@Override
		public void run() {
			m_currentWorker = Thread.currentThread();
			int numProcessed = 1;
			while(true) {
				if (!run0(numProcessed)) {
					break;
				}
				numProcessed++;
			}
		}
	}
}