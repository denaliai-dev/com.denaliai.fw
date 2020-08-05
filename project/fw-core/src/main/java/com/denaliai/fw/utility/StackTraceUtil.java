package com.denaliai.fw.utility;

import java.io.StringWriter;
import java.util.Arrays;

/*
java.io.FileNotFoundException: /logs/694445a17176-test/geo-v1-1499535983811 (No such file or directory)
	at java.io.FileOutputStream.open0(Native Method) ~[?:1.8.0_131]
	at java.io.FileOutputStream.open(FileOutputStream.java:270) ~[?:1.8.0_131]
	at java.io.FileOutputStream.<init>(FileOutputStream.java:213) ~[?:1.8.0_131]
	at java.io.FileOutputStream.<init>(FileOutputStream.java:101) ~[?:1.8.0_131]
	at java.io.PrintWriter.<init>(PrintWriter.java:184) ~[?:1.8.0_131]
	at com.applelg.service.core.Operation$RootContext.onFinalClose(Operation.java:291) [classes!/:0.1.0]
	at com.applelg.service.core.Operation$BaseContext.close(Operation.java:219) [classes!/:0.1.0]
	at com.applelg.service.core.Operation$SubContext.onFinalClose(Operation.java:326) [classes!/:0.1.0]
	at com.applelg.service.core.Operation$BaseContext.close(Operation.java:219) [classes!/:0.1.0]
	at com.applelg.service.core.Operation$SubContext.close(Operation.java:301) [classes!/:0.1.0]
	at com.applelg.service.core.Operation$SubContext.onFinalClose(Operation.java:326) [classes!/:0.1.0]
	at com.applelg.service.core.Operation$BaseContext.close(Operation.java:219) [classes!/:0.1.0]
	at com.applelg.service.core.Operation$SubContext.close(Operation.java:301) [classes!/:0.1.0]
	at com.applelg.service.core.Operation$Service.exitCurrentOperation(Operation.java:103) [classes!/:0.1.0]
	at com.applelg.service.core.Operation$Closeable.close(Operation.java:117) [classes!/:0.1.0]
	at com.applelg.service.core.OperationRunnable.run(OperationRunnable.java:14) [classes!/:0.1.0]
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142) [?:1.8.0_131]
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617) [?:1.8.0_131]
	at java.lang.Thread.run(Thread.java:748) [?:1.8.0_131]

 */
public class StackTraceUtil {
	public static String stackTraceToString(Throwable t) {
		final StringBuilder writer = new StringBuilder();
		_stackTraceToString(writer, t);
		return writer.toString();
	}
	private static void _stackTraceToString(StringBuilder writer, Throwable t) {

		writer.append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");

		StackTraceElement[] stack = t.getStackTrace();
		for(int i=0; i<stack.length; i++) {
			StackTraceElement el = stack[i];
			writer.append("\tat ").append(el.toString()).append("\n");
		}
		if (t.getCause() != null) {
			writer.append("Caused by ");
			_stackTraceToString(writer, t.getCause());
		}
	}

	public static String stackTraceToString(StackTraceElement[] stack) {
		final StringBuilder writer = new StringBuilder();
		for(int i=0; i<stack.length; i++) {
			StackTraceElement el = stack[i];
			writer.append("\t").append(el.toString()).append("\n");
		}
		return writer.toString();
	}

	public static String stackTraceToString(StackTraceElement[] stack, int skip) {
		final StringBuilder writer = new StringBuilder();
		for(int i=skip; i<stack.length; i++) {
			StackTraceElement el = stack[i];
			writer.append("\t").append(el.toString()).append("\n");
		}
		return writer.toString();
	}

	public static String stackTraceForDebugging(int skip) {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		final StringWriter writer = new StringWriter();

		for(int i=skip+2; i<stack.length; i++) {
			StackTraceElement el = stack[i];
			writer.append("\t").append(el.toString()).append("\n");
			if (!el.getClassName().startsWith("com.applelg") && !el.getClassName().startsWith("com.appleleisuregroup")) {
				break;
			}
		}
		return writer.toString();
	}

	public static void trimStackTrace(final Throwable t, final String appPackageName) {
		final StackTraceElement[] stack = t.getStackTrace();
		if (stack == null) {
			return;
		}
		int lastElement = -1;
		for(int i=0; i<stack.length; i++) {
			StackTraceElement el = stack[i];
			if (el.getClassName().startsWith(appPackageName)) {
				lastElement = -1;
			} else if (lastElement != -1) {
				lastElement = i;
			}
		}
		if (lastElement > 0 && lastElement < stack.length-1) {
			final StackTraceElement[] newStack = Arrays.copyOfRange(stack, 0, lastElement+1);
			newStack[newStack.length-1] = new StackTraceElement("trimmed.", ".", null, 0);
			t.setStackTrace(newStack);
		}
	}

	public static void writeSkipStackTrace(final StackTraceElement[] stack, final StringBuilder b, final String skipStartPackageName, final String lookForStartAfterName) {
		int startIndex = 1; // Start at 1, since 0 is Thread.getStackTrace()
		if (skipStartPackageName != null) {
			for(int i=1; i<stack.length; i++) { // Start at 1, since 0 is Thread.getStackTrace()
				StackTraceElement el = stack[i];
				if (!el.getClassName().startsWith(skipStartPackageName)) {
					startIndex = i;
					break;
				}
			}
		} else if (lookForStartAfterName != null) {
			for(int i=1; i<stack.length; i++) { // Start at 1, since 0 is Thread.getStackTrace()
				StackTraceElement el = stack[i];
				if (el.toString().startsWith(lookForStartAfterName)) {
					startIndex = i;
					break;
				}
			}
		}
		for(int i=startIndex; i<stack.length; i++) {
			StackTraceElement el = stack[i];
			b.append('\t').append(el.toString()).append("\n");
		}
	}
}
