package riot.protocols;

public interface Protocol<I, O> {

    ProtocolDescriptor<I, O> getDescriptor();

}
