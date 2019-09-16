package riot;

import java.util.concurrent.TimeUnit;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.util.Timeout;
import riot.actors.I2CActor;
import riot.protocols.Protocol;
import riot.protocols.ProtocolDescriptor;
import riot.protocols.Raw;

/**
 * The configuration of a raw I2C device. This can be used directly, or
 * subclassed to encapsulate the behaviour of a specific I2C device.
 * 
 * @param <I>
 * 
 */
public class I2C<P extends Protocol<I, O>, I, O> {

	/*
	 * Settings
	 */
	private P proto;
	private ProtocolDescriptor<I, O> protoDescriptor;
	private int busNumber;
	private int address;

	private I2C(P deviceProtocol) {
		this.proto = deviceProtocol;
		this.protoDescriptor = deviceProtocol.getDescriptor();
	}
	
	public static I2C<Raw, Raw.Command, Raw.Result> rawDevice() {
		return new I2C<Raw, Raw.Command, Raw.Result>(new Raw());
	}

	public static <P extends Protocol<I, O>, I, O> I2C<P, I, O> device(Class<P> deviceProtocol)
			throws IllegalAccessException, InstantiationException {
		return new I2C<P, I, O>(deviceProtocol.newInstance());
	}

	public static <P extends Protocol<I, O>, I, O> I2C<P, I, O> device(P deviceProtocol) {
		return new I2C<P, I, O>(deviceProtocol);
	}

	public P getProtocol() {
		return proto;
	}

	public ProtocolDescriptor<I, O> getProtocolDescriptor() {
		return protoDescriptor;
	}

	public I2C<P, I, O> onBus(int busNumber) {
		this.busNumber = busNumber;
		return this;
	}

	public int getBusNumber() {
		return busNumber;
	}

	public I2C<P, I, O> at(int address) {
		this.address = address;
		return this;
	}

	public int getAddress() {
		return address;
	}

	/*
	 * Streams and actors
	 */

	protected Timeout getTimeout() {
		return Timeout.apply(1, TimeUnit.SECONDS);
	}

	public Sink<I, NotUsed> asSink(ActorSystem system) {
		return Flow.of(protoDescriptor.getInputMessageType()).ask(system.actorOf(asProps()),
				protoDescriptor.getOutputMessageType(), Timeout.apply(1, TimeUnit.SECONDS)).to(Sink.ignore());
	}

	public Flow<I, O, NotUsed> asFlow(ActorSystem system) {
		return Flow.of(protoDescriptor.getInputMessageType()).ask(system.actorOf(asProps()),
				protoDescriptor.getOutputMessageType(), getTimeout());
	}

	public Props asProps() {
		return Props.create(I2CActor.class, this);
	}
}
