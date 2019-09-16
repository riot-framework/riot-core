package riot.protocols;

import java.util.concurrent.TimeUnit;

import akka.util.Timeout;

public class ProtocolDescriptor<I, O> {
	private final Class<I> inputMessageType;
	private final Class<O> outputMessageType;
	private final Timeout timeout;

	public ProtocolDescriptor(Class<I> inputMessageType, Class<O> outputMessageType) {
		this.inputMessageType = inputMessageType;
		this.outputMessageType = outputMessageType;
		this.timeout = Timeout.apply(1, TimeUnit.SECONDS);
	}

	public ProtocolDescriptor(Class<I> inputMessageType, Class<O> outputMessageType, Timeout timeout) {
		this.inputMessageType = inputMessageType;
		this.outputMessageType = outputMessageType;
		this.timeout = timeout;
	}

	public Class<I> getInputMessageType() {
		return inputMessageType;
	}

	public Class<O> getOutputMessageType() {
		return outputMessageType;
	}

	public Timeout getTimeout() {
		return timeout;
	}
}
