package riot.actors;

import java.util.Collections;
import java.util.Set;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogInput;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinInput;
import com.pi4j.io.gpio.event.GpioPinAnalogValueChangeEvent;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerAnalog;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import riot.GPIO;
import riot.GPIO.State;

public class GPIOInActor extends AbstractActor implements GpioPinListenerAnalog, GpioPinListenerDigital {
	final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

	private static final GpioController gpio = GpioFactory.getInstance();

	private final GPIO.In conf;

	private GpioPinInput input;
	private GpioPinDigitalInput inputDigital;
	private GpioPinAnalogInput inputAnalog;

	private Set<ActorRef> listeners;

	protected GPIOInActor(GPIO.In conf) {
		this.conf = conf;
	}

	@Override
	public Receive createReceive() {
		return super.receiveBuilder() //
				.match(GPIO.Get.class, this::onGPIOGet).build();
	}

	@Override
	public void preStart() {
		if (input == null) {
			switch (conf.getPinMode()) {
			case DIGITAL_OUTPUT:
				inputDigital = gpio.provisionDigitalInputPin(conf.getPin(), conf.getName());
				input = inputDigital;
				break;
			case ANALOG_OUTPUT:
				inputAnalog = gpio.provisionAnalogInputPin(conf.getPin(), conf.getName());
				input = inputAnalog;
				break;
			default:
				throw new IllegalArgumentException("GPIOOutActor cannot be initialized for " + conf.getPinMode());
			}

			if (conf.hasListener()) {
				this.listeners = Collections.unmodifiableSet(conf.getListeners());
				input.addListener(this);
			}
		}
	}

	@Override
	public void postStop() {
		if (input != null) {
			input.removeAllListeners();
			input.unexport();
		}
	}

	public void onGPIOGet(GPIO.Get state) {
		if (input == null) {
			switch (conf.getPinMode()) {
			case DIGITAL_INPUT:
				if (inputDigital.isHigh()) {
					sender().tell(GPIO.State.HIGH, self());
				}
				if (inputDigital.isLow()) {
					sender().tell(GPIO.State.LOW, self());
				}
				break;
			case ANALOG_INPUT:
				sender().tell(inputAnalog.getValue(), self());
				break;
			default:
				log.error("GPIOInActor cannot read value of " + conf.getPinMode());
			}
		}
	}

	@Override
	public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
		switch (event.getState()) {
		case HIGH:
			notifyListeners(State.HIGH);
			break;
		case LOW:
			notifyListeners(State.LOW);
			break;
		}
	}

	@Override
	public void handleGpioPinAnalogValueChangeEvent(GpioPinAnalogValueChangeEvent event) {
		notifyListeners(event.getValue());
	}

	private void notifyListeners(Object msg) {
		for (ActorRef actorRef : listeners) {
			actorRef.tell(inputAnalog.getValue(), self());
		}
	}
}
