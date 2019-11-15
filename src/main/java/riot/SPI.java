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
import riot.protocols.SPIProtocol;
import riot.protocols.ProtocolDescriptor;
import riot.protocols.RawSPIProtocol;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * The configuration of a raw SPI device. This can be used directly, or subclassed to encapsulate the behaviour of a
 * specific SPI device.
 *
 * @param <I>
 */
public class SPI<P extends SPIProtocol<I, O>, I, O> {

    /*
     * Settings
     */
    private P proto;
    private ProtocolDescriptor<I, O> protoDescriptor;
    private int channel;
    private SpiMode mode = SpiDevice.DEFAULT_SPI_MODE;
    private int speed = SpiDevice.DEFAULT_SPI_SPEED;

    private SPI(P deviceProtocol) {
        this.proto = deviceProtocol;
        this.protoDescriptor = deviceProtocol.getDescriptor();
    }

    public static SPI<RawSPIProtocol, byte[], byte[]> rawDevice() {
        return new SPI<RawSPIProtocol, byte[], byte[]>(new RawSPIProtocol());
    }

    public static <P extends SPIProtocol<I, O>, I, O> SPI<P, I, O> device(Class<P> deviceProtocol)
            throws IllegalAccessException, InstantiationException {
        return new SPI<P, I, O>(deviceProtocol.newInstance());
    }

    public static <P extends SPIProtocol<I, O>, I, O> SPI<P, I, O> device(P deviceProtocol) {
        return new SPI<P, I, O>(deviceProtocol);
    }

    public P getProtocol() {
        return proto;
    }

    public ProtocolDescriptor<I, O> getProtocolDescriptor() {
        return protoDescriptor;
    }

    public SPI<P, I, O> onChannel(int channelNumber) {
        this.channel = channelNumber;
        return this;
    }

    public int getChannel() {
        return channel;
    }

    public SPI<P, I, O> withMode0() {
        this.mode = SpiMode.MODE_0;
        return this;
    }

    public SPI<P, I, O> withmode1() {
        this.mode = SpiMode.MODE_1;
        return this;
    }

    public SPI<P, I, O> withmode2() {
        this.mode = SpiMode.MODE_2;
        return this;
    }

    public SPI<P, I, O> withmode3() {
        this.mode = SpiMode.MODE_3;
        return this;
    }

    public SpiMode getMode() {
        return this.mode;
    }

    /**
     * Sets the SPI bus speed, in kHz. Range is 500kHz - 32MHz, default is 1MHz
     *
     * @param speed a bus speed value between 500 and 32000, in kHz
     * @return this configuration object for chaining.
     */
    public SPI<P, I, O> withSpeed(int speed) {
        if (speed < 500) speed = 500;
        if (speed > 32000) speed = 32000;
        this.speed = speed * 1000;
        return this;
    }

    public int getSpeed() {
        return this.speed;
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



