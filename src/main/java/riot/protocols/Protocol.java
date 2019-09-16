package riot.protocols;

public interface Protocol<I, O> {

	public Class<I> getInputMessageClass();

	public Class<O> getOutputMessageClass();

}
