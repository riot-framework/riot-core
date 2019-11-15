package riot.actors;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.pi4j.io.spi.SpiChannel;
import com.pi4j.io.spi.SpiDevice;
import com.pi4j.io.spi.SpiFactory;
import riot.SPI;
import riot.protocols.SPIProtocol;

import java.io.IOException;

public class SPIActor<P extends SPIProtocol<I, O>, I, O> extends AbstractActor {
    final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private SpiDevice dev;
    private SPIProtocol<I, O> proto;

    private final SPI<P, I, O> conf;

    protected SPIActor(SPI<P, I, O> conf) {
        this.conf = conf;
    }

    @Override
    public Receive createReceive() {
        return super.receiveBuilder() //
                .match(conf.getProtocolDescriptor().getInputMessageType(), this::onMessage).build();
    }

    @Override
    public void preStart() throws IOException {
        final SpiChannel chan = SpiChannel.getByNumber(conf.getChannel());
        dev = SpiFactory.getInstance(chan, conf.getSpeed(), conf.getMode());
        proto = conf.getProtocol();
        proto.init(dev);
    }

    @Override
    public void postStop() throws IOException {
        proto.shutdown(dev);
    }

    public void onMessage(I message) throws IOException {
        sender().tell(proto.exec(dev, message), self());
    }

}
