package riot.actors;

import java.io.IOException;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import riot.I2C;
import riot.protocols.I2CProtocol;

public class I2CActor<P extends I2CProtocol<I, O>, I, O> extends AbstractActor {
	final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

	private I2CDevice dev;
	private I2CProtocol<I, O> proto;

	private final I2C<P, I, O> conf;

	protected I2CActor(I2C<P, I, O> conf) {
		this.conf = conf;
	}

	@Override
	public Receive createReceive() {
		return super.receiveBuilder() //
				.match(conf.getProtocolDescriptor().getInputMessageType(), this::onMessage).build();
	}

	@Override
	public void preStart() throws UnsupportedBusNumberException, IOException {
		final I2CBus bus = I2CFactory.getInstance(conf.getBusNumber());
		dev = bus.getDevice(conf.getAddress());
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
