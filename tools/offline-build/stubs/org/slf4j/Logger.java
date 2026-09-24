package org.slf4j;
public interface Logger {
	void info(String msg);
	void warn(String format, Object arg);
	void warn(String msg, Throwable t);
	void error(String msg, Throwable t);
}
