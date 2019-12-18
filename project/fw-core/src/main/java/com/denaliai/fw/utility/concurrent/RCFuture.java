package com.denaliai.fw.utility.concurrent;

import io.netty.util.ReferenceCounted;

public interface RCFuture<T> extends ReferenceCounted {
	RCFuture<T> addListener(RCFutureListener<T> listener, ParamBag params);
	RCFuture<T> addListener(RCFutureListener<T> listener);

	T get();
	Throwable cause();
	boolean isSuccess();
	boolean isDone();
	boolean await(long timeoutInMS);
	RCFuture<T> retain();
}
