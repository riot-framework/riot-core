package riot;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.Timeout;
import com.pi4j.io.spi.SpiDevice;
import com.pi4j.io.spi.SpiMode;
import riot.actors.SPIActor;
import riot.protocols.OneWireProtocol;
import riot.protocols.ProtocolDescriptor;
import riot.protocols.RawOneWireProtocol;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The configuration of a raw SPI device. This can be used directly, or subclassed to encapsulate the behaviour of a
 * specific SPI device.
 *
 * @param <I>
 */
public class OneWire<P extends OneWireProtocol<I, O>, I, O> {

    /*
     * Settings
     */
    private P proto;
    private ProtocolDescriptor<I, O> protoDescriptor;
    private int deviceFamily;

    private OneWire(P deviceProtocol) {
        this.proto = deviceProtocol;
        this.protoDescriptor = deviceProtocol.getDescriptor();
    }

    public static OneWire<OneWireProtocol<RawOneWireProtocol.Command, Map>, RawOneWireProtocol.Command, Map> rawDevice() {
        return new OneWire<OneWireProtocol<RawOneWireProtocol.Command, Map>, RawOneWireProtocol.Command, Map>(new RawOneWireProtocol());
    }

    public static <P extends OneWireProtocol<I, O>, I, O> OneWire<P, I, O> device(Class<P> deviceProtocol)
            throws IllegalAccessException, InstantiationException {
        return new OneWire<P, I, O>(deviceProtocol.newInstance());
    }

    public static <P extends OneWireProtocol<I, O>, I, O> OneWire<P, I, O> device(P deviceProtocol) {
        return new OneWire<P, I, O>(deviceProtocol);
    }

    public P getProtocol() {
        return proto;
    }

    public ProtocolDescriptor<I, O> getProtocolDescriptor() {
        return protoDescriptor;
    }

    public OneWire<P, I, O> onChannel(int deviceFamily) {
        this.deviceFamily = deviceFamily;
        return this;
    }

    public int getDeviceFamily() {
        return deviceFamily;
    }

    /*
     * Streams and actors
     */

    public Sink<I, NotUsed> asSink(ActorSystem system) {
        return Flow.of(protoDescriptor.getInputMessageType()).ask(system.actorOf(asProps()),
                protoDescriptor.getOutputMessageType(), Timeout.apply(1, TimeUnit.SECONDS)).to(Sink.ignore());
    }

    public Flow<I, O, NotUsed> asFlow(ActorSystem system) {
        return Flow.of(protoDescriptor.getInputMessageType()).ask(system.actorOf(asProps()),
                protoDescriptor.getOutputMessageType(), protoDescriptor.getTimeout());
    }

    public Source<O, Cancellable> asSource(ActorSystem system, I command, Duration d) {
        Source<I, Cancellable> timerSource = Source.tick(d, d, command);
        return timerSource.via(asFlow(system));
    }

    public Props asProps() {
        return Props.create(SPIActor.class, this);
    }

}



