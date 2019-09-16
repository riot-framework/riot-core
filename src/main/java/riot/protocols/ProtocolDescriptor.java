package riot.protocols;

public class ProtocolDescriptor<I, O> {
	private final Class<I> inputMessageType;
	private final Class<O> outputMessageType;
	
	public ProtocolDescriptor(Class<I> inputMessageType, Class<O> outputMessageType) {
		this.inputMessageType = inputMessageType;
		this.outputMessageType = outputMessageType;
	}

	public Class<I> getInputMessageType() {
		return inputMessageType;
	}

	public Class<O> getOutputMessageType() {
		return outputMessageType;
	}
	
}
