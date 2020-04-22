package com.denaliai.fw.mongo;

import com.denaliai.fw.Application;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public final class DenaliMongoPromiseSubscriber<T> implements Subscriber<T> {
	private Promise<T> m_completed = Application.newPromise();

	@Override
	public void onSubscribe(Subscription s) {
		s.request(1);
	}

	@Override
	public void onNext(T result) {
		m_completed.setSuccess(result);
	}

	@Override
	public void onError(Throwable t) {
		m_completed.tryFailure(t);
	}

	@Override
	public void onComplete() {
		m_completed.trySuccess(null);
	}

	public Future<T> completedFuture() {
		return m_completed;
	}
}
