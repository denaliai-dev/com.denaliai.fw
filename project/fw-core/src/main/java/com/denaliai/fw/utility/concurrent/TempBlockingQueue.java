package com.denaliai.fw.utility.concurrent;

import io.netty.util.internal.PlatformDependent;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * This is a temporary cheap replacement for MpscBlockingConsumerArrayQueue which is not released yet
 *
 *
 */
public class TempBlockingQueue<T> implements BlockingQueue<T> {
	private final Queue<T> m_queue;
	private final Semaphore m_itemsSemaphore = new Semaphore(0, false);

	public TempBlockingQueue(int capacity) {
		m_queue = PlatformDependent.newFixedMpscQueue(4096);
	}

	@Override
	public boolean add(T t) {
		if (!m_queue.add(t)) {
			return false;
		}
		m_itemsSemaphore.release();
		return true;
	}

	@Override
	public boolean offer(T t) {
		if (!m_queue.offer(t)) {
			return false;
		}
		m_itemsSemaphore.release();
		return true;
	}

	@Override
	public T remove() {
		if (!m_itemsSemaphore.tryAcquire()) {
			throw new NoSuchElementException();
		}
		return m_queue.remove();
	}

	@Override
	public T poll() {
		if (!m_itemsSemaphore.tryAcquire()) {
			return null;
		}
		return m_queue.poll();
	}

	@Override
	public T element() {
		return m_queue.element();
	}

	@Override
	public T peek() {
		return m_queue.peek();
	}

	@Override
	public void put(T t) throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public T take() throws InterruptedException {
		m_itemsSemaphore.acquire();
		return m_queue.poll();
	}

	@Override
	public T poll(long timeout, TimeUnit unit) throws InterruptedException {
		if (!m_itemsSemaphore.tryAcquire(timeout, unit)) {
			return null;
		}
		return m_queue.poll();
	}

	@Override
	public int remainingCapacity() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		return m_queue.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		m_queue.clear();
	}

	@Override
	public int size() {
		return m_queue.size();
	}

	@Override
	public boolean isEmpty() {
		return m_queue.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<T> iterator() {
		return m_queue.iterator();
	}

	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T1> T1[] toArray(T1[] a) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int drainTo(Collection<? super T> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int drainTo(Collection<? super T> c, int maxElements) {
		throw new UnsupportedOperationException();
	}
}
