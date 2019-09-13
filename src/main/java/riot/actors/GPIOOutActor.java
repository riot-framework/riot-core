package riot.actors;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogOutput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.GpioPinOutput;
import com.pi4j.io.gpio.GpioPinPwmOutput;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import riot.GPIO;
import riot.messages.Ack;
import riot.messages.Init;
import riot.messages.Shutdown;

public class GPIOOutActor extends AbstractActor {
	private static final int PWM_RANGE = 1024;
	
	final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

	private static final GpioController gpio = GpioFactory.getInstance();
	
	private final GPIO.Out conf;
	
	private GpioPinOutput output;
	private GpioPinDigitalOutput outputDigital;
	private GpioPinAnalogOutput outputAnalog;
	private GpioPinPwmOutput outputPwm;

	protected GPIOOutActor(GPIO.Out conf) {
		this.conf = conf;
	}

	@Override
	public Receive createReceive() {
		ReceiveBuilder builder = super.receiveBuilder() //
				.match(Init.class, this::onInitialize) //
				.match(Shutdown.class, this::onShutdown) //
				.match(Ack.class, this::onAck);

		switch (conf.getPinMode()) {
		case DIGITAL_OUTPUT:
			return builder.match(GPIO.State.class, this::onGPIOState) //
					.build();
		case ANALOG_OUTPUT:
		case PWM_OUTPUT:
			return builder.match(Double.class, this::onValue) //
					.build();
		default:
			throw new IllegalArgumentException("GPIOOutActor cannot be created for " + conf.getPinMode());
		}
	}

	public void onInitialize(Init msg) {
		ensurePinProvisioned();
		sender().tell(Ack.INSTANCE, self());
	}

	private void ensurePinProvisioned() {
		if (output == null) {
			switch (conf.getPinMode()) {
			case DIGITAL_OUTPUT:
				outputDigital = gpio.provisionDigitalOutputPin(conf.getPin(), conf.getName());
				if (conf.getInitialState() != null) {
					outputDigital.setState(conf.getInitialState());
				}
				output = outputDigital;
				break;
			case ANALOG_OUTPUT:
				outputAnalog = gpio.provisionAnalogOutputPin(conf.getPin(), conf.getName());
				if (conf.getInitialValue() != null) {
					outputAnalog.setValue(conf.getInitialValue());
				}
				output = outputAnalog;
				break;
			case PWM_OUTPUT:
				outputPwm = gpio.provisionPwmOutputPin(conf.getPin(), conf.getName());
				outputPwm.setPwmRange(PWM_RANGE);
				if (conf.getInitialValue() != null) {
					outputPwm.setPwm(toPwmSteps(conf.getInitialValue()));
				}
				output = outputPwm;
				break;
			default:
				throw new IllegalArgumentException("GPIOOutActor cannot be initialized for " + conf.getPinMode());
			}

			if (conf.getShutdownState() != null) {
				output.setShutdownOptions(true, conf.getShutdownState());
			}
		}
	}

	private static final int toPwmSteps(double value) {
		final long steps = Math.round(value * PWM_RANGE);
		if (steps > PWM_RANGE) {
			return PWM_RANGE;
		}
		if (steps < 0) {
			return 0;
		}
		return (int) steps;
	}

	public void onShutdown(Shutdown msg) {
		if (output != null) {
			output.unexport();
		}
		sender().tell(Ack.INSTANCE, self());
	}

	public void onAck(Ack msg) {
		log.debug("Received Ack");
	}

	public void onGPIOState(GPIO.State state) {
		switch (state) {
		case ON:
			outputDigital.high();
			break;
		case OFF:
			outputDigital.low();
			break;
		case TOGGLE:
			outputDigital.toggle();
			break;
		}

		sender().tell(Ack.INSTANCE, self());
	}

	public void onValue(Double value) {
		if (output == null) {

		}
		if (outputAnalog != null) {
			outputAnalog.setValue(value);
		} else if (outputPwm != null) {
			outputPwm.setPwm(toPwmSteps(value));
		}
	}
}
