package riot.actors;

import java.util.Collections;
import java.util.Set;

import akka.japi.pf.ReceiveBuilder;
import com.pi4j.io.gpio.*;
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
    private GpioPinDigitalMultipurpose inputMultipurpose;
    private GpioPinDigitalInput inputDigital;
    private GpioPinAnalogInput inputAnalog;

    private Set<ActorRef> listeners;

    protected GPIOInActor(GPIO.In conf) {
        this.conf = conf;
    }

    @Override
    public Receive createReceive() {
        ReceiveBuilder receive = super.receiveBuilder()
                .match(GPIO.Get.class, this::onGPIOGet);

        if (conf.isBidirectional()) {
            receive = receive
                    .match(GPIO.State.class, this::onGPIOState)
                    .match(GPIO.Pulse.class, this::onGPIOPulse);
        }

        return receive.build();
    }

    @Override
    public void preStart() {
        if (input == null) {
            switch (conf.getPinMode()) {
                case DIGITAL_INPUT:
                    if (conf.isBidirectional()) {
                        inputMultipurpose = gpio.provisionDigitalMultipurposePin(conf.getPin(), conf.getName(), PinMode.DIGITAL_INPUT);
                        inputDigital = inputMultipurpose;
                    } else {
                        inputDigital = gpio.provisionDigitalInputPin(conf.getPin(), conf.getName());
                    }
                    input = inputDigital;
                    break;
                case ANALOG_INPUT:
                    inputAnalog = gpio.provisionAnalogInputPin(conf.getPin(), conf.getName());
                    input = inputAnalog;
                    break;
                default:
                    throw new IllegalArgumentException("GPIOInActor cannot be initialized for " + conf.getPinMode());
            }

            if (conf.getPullResistance() != null) {
                input.setPullResistance(conf.getPullResistance());
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

    public void onGPIOState(GPIO.State state) {
        inputMultipurpose.setMode(PinMode.DIGITAL_OUTPUT);
        switch (state) {
            case HIGH:
                inputMultipurpose.high();
                break;
            case LOW:
                inputMultipurpose.low();
                break;
            case TOGGLE:
                inputMultipurpose.toggle();
                break;
        }
        inputMultipurpose.setMode(PinMode.DIGITAL_INPUT);
    }

    public void onGPIOPulse(GPIO.Pulse pulse) {
        inputMultipurpose.setMode(PinMode.DIGITAL_OUTPUT);
        for (int i = 0; i < pulse.getPulses().length; i++) {
            //Even pulses, starting with 0, are high, odds are low
            final PinState pulseState = i % 2 == 0 ? PinState.HIGH : PinState.LOW;
            final long pulseLength = pulse.getPulses()[i];
            if (pulseLength>0)
                inputMultipurpose.pulse(pulseLength, pulseState, pulseLength > 1);
        }
        inputMultipurpose.setMode(PinMode.DIGITAL_INPUT);
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
            actorRef.tell(msg, self());
        }
    }
}
