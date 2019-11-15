package riot.actors;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.pi4j.io.w1.W1Device;
import com.pi4j.io.w1.W1Master;
import riot.OneWire;
import riot.protocols.OneWireProtocol;

import java.io.IOException;
import java.util.List;

public class OneWireActor<P extends OneWireProtocol<I, O>, I, O> extends AbstractActor {
    final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private List<W1Device> dev;
    private OneWireProtocol<I, O> proto;

    private final OneWire<P, I, O> conf;

    protected OneWireActor(OneWire<P, I, O> conf) {
        this.conf = conf;
    }

    @Override
    public Receive createReceive() {
        return super.receiveBuilder() //
                .match(conf.getProtocolDescriptor().getInputMessageType(), this::onMessage).build();
    }

    @Override
    public void preStart() throws IOException {
        final W1Master w1Master = new W1Master();
        dev = w1Master.getDevices(conf.getDeviceFamily());
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
