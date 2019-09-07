package riot;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.stream.javadsl.Sink;
import riot.GPIO.Out.Initialize;

public class GPIO {
	private static final GpioController gpio = GpioFactory.getInstance();

	/**
	 * Models the state of a digital GPIO pin: ON (high), OFF (low), or TOGGLE (will
	 * become ON if it was OFF, and vice-versa).
	 */
	public enum State {
		ON, OFF, TOGGLE
	}

	private static class Shutdown {
	}

	/*
	 * Streaming
	 */
	//TODO: Make independent of Raspberry Pi
	public static final Sink<State, NotUsed> asSink(ActorSystem system, int pin, boolean initialstate) {
		ActorRef actor = system.actorOf(Props.create(Out.class), "GPIO-" + pin);
		return Sink.actorRefWithAck(actor, new Initialize(RaspiPin.getPinByAddress(pin), initialstate), Ack.INSTANCE,
				new Shutdown(), ex -> new RuntimeException(ex));
	}
	
	//TODO: Source

	/*
	 * Actors
	 */
	//TODO: In/Out, Digital/Analog, etc...
	public static class Out extends AbstractActor {
		final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
		GpioPinDigitalOutput output;

		public static class Initialize {
			final Pin pin;
			final boolean initialstate;

			public Initialize(Pin pin, boolean initialstate) {
				this.pin = pin;
				this.initialstate = initialstate;
			}
		}

		public void onInitialize(Initialize msg) {
			this.output = gpio.provisionDigitalOutputPin(msg.pin, msg.pin.getName(),
					msg.initialstate ? PinState.HIGH : PinState.LOW);
			output.setShutdownOptions(true, PinState.LOW);
			output.setPullResistance(PinPullResistance.OFF);

			sender().tell(Ack.INSTANCE, self());
		}

		@Override
		public Receive createReceive() {
			return receiveBuilder() //
					.match(Initialize.class, this::onInitialize) //
					.match(Shutdown.class, this::onStreamCompleted) //
					.match(State.class, this::onGPIOState) //
					.build();
		}

		public void onStreamCompleted(Shutdown msg) {
			// TODO
		}

		public void onGPIOState(State state) {
			switch (state) {
			case ON:
				output.high();
				break;
			case OFF:
				output.low();
				break;
			case TOGGLE:
				output.toggle();
				break;
			}

			sender().tell(Ack.INSTANCE, self());
		}

	}

}
