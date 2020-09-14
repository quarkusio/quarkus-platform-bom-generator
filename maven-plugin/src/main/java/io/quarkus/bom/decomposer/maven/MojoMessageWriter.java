package io.quarkus.bom.decomposer.maven;

import java.util.Objects;

import org.apache.maven.plugin.logging.Log;

import io.quarkus.bom.decomposer.MessageWriter;

public class MojoMessageWriter implements MessageWriter {

	private final Log log;
	
	public MojoMessageWriter(Log log) {
		this.log = Objects.requireNonNull(log);
	}
	
	@Override
	public void info(Object msg) {
		log.info(toStr(msg));
	}

	@Override
	public void error(Object msg) {
		log.error(toStr(msg));
	}

	@Override
	public boolean debugEnabled() {
		return log.isDebugEnabled();
	}

	@Override
	public void debug(Object msg) {
		log.debug(toStr(msg));
	}

	@Override
	public void warn(Object msg) {
		log.warn(toStr(msg));
	}

	private CharSequence toStr(Object msg) {
		return msg == null ? "null" : msg.toString();
	}
}
