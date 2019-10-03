package riot.actors;

import com.pi4j.io.gpio.*;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import riot.GPIO;

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
        switch (conf.getPinMode()) {
            case DIGITAL_OUTPUT:
                return super.receiveBuilder()
                        .match(GPIO.State.class, this::onGPIOState)
                        .match(GPIO.Pulse.class, this::onGPIOPulse)
                        .build();
            case ANALOG_OUTPUT:
                return super.receiveBuilder()
                        .match(Double.class, this::onValue).build();
            case PWM_OUTPUT:
                return super.receiveBuilder()
                        .match(Double.class, this::onValue)
                        .match(Integer.class, this::onValue)
                        .build();
            default:
                throw new IllegalArgumentException("GPIOOutActor cannot be created for " + conf.getPinMode());
        }
    }

    @Override
    public void preStart() {
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
            if (conf.getPullResistance() != null) {
                output.setPullResistance(conf.getPullResistance());
            }

        }
    }

    @Override
    public void postStop() {
        if (output != null) {
            output.unexport();
        }
    }

    public void onGPIOState(GPIO.State state) {
        switch (state) {
            case HIGH:
                outputDigital.high();
                sender().tell(state, self());
                break;
            case LOW:
                outputDigital.low();
                sender().tell(state, self());
                break;
            case TOGGLE:
                outputDigital.toggle();
                if (outputDigital.isHigh()) {
                    sender().tell(GPIO.State.HIGH, self());
                }
                if (outputDigital.isLow()) {
                    sender().tell(GPIO.State.LOW, self());
                }
                break;
        }
    }

    public void onGPIOPulse(GPIO.Pulse pulse) {
        for (int i = 0; i < pulse.getPulses().length; i++) {
            //Even pulses, starting with 0, are high, odds are low
            final PinState pulseState = i % 2 == 0 ? PinState.HIGH : PinState.LOW;
            final long pulseLength = pulse.getPulses()[i];
            if (pulseLength > 0) {
                outputDigital.pulse(pulseLength, pulseState, pulseLength > 1);
            }
            sender().tell(pulse, self());
        }
    }

    public void onValue(Double value) {
        if (outputAnalog != null) {
            outputAnalog.setValue(value);
            sender().tell(outputAnalog.getValue(), self());
        } else if (outputPwm != null) {
            outputPwm.setPwm(toPwmSteps(value));
            sender().tell(outputPwm.getPwm(), self());
        }
    }

    public void onValue(Integer value) {
        if (outputPwm != null) {
            outputPwm.setPwm(value);
            sender().tell(outputPwm.getPwm(), self());
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
}
