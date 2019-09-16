package riot;

import java.util.concurrent.TimeUnit;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Sink;
import akka.util.Timeout;
import riot.actors.I2CActor;
import riot.protocols.Protocol;
import riot.protocols.Protocol.Command;
import riot.protocols.Protocol.Responce;
import riot.protocols.Raw;

/**
 * The configuration of a raw I2C device. This can be used directly, or
 * subclassed to encapsulate the behaviour of a specific I2C device.
 * 
 * @param <I>
 * 
 */
public class I2C<P extends Protocol<P>> {

	/*
	 * Settings
	 */
	private P deviceProtocol;
	private int busNumber;
	private int address;

	private I2C(P deviceProtocol) {
		this.deviceProtocol = deviceProtocol;
	}

	public static I2C<Raw> rawDevice() {
		return new I2C<Raw>(new Raw());
	}

	public static <P extends Protocol<P>> I2C<P> device(Class<P> deviceProtocol) throws IllegalAccessException, InstantiationException {
		return new I2C<P>(deviceProtocol.newInstance());
	}

	public static <P extends Protocol<P>> I2C<P> device(P deviceProtocol) {
		return new I2C<P>(deviceProtocol);
	}

	public I2C<P> onBus(int busNumber) {
		this.busNumber = busNumber;
		return this;
	}

	public int getBusNumber() {
		return busNumber;
	}

	public I2C<P> at(int address) {
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

	public Sink<? extends Command<P>, NotUsed> asSink(ActorSystem system) {
		return Flow.of(deviceProtocol.getCommandClass())
				.ask(system.actorOf(asProps()), Responce.class, Timeout.apply(1, TimeUnit.SECONDS))
				.to(Sink.ignore());
	}

	public Flow<Command<P>, Responce<Command<P>, P>, NotUsed> asFlow(ActorSystem system) {
		return Flow.fromGraph(GraphDSL.create(b -> {
			return b.add(
					Flow.of(deviceProtocol.getCommandClass()).ask(system.actorOf(asProps()), Message.Result.class, Timeout.getTimeout()));
		}));
	}

	public Props asProps() {
		return Props.create(I2CActor.class, this);
	}
}
