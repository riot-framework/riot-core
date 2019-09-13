package riot;

import java.util.concurrent.TimeUnit;

import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Sink;
import akka.util.Timeout;
import riot.actors.GPIOOutActor;

/**
 * The configuration of a GPIO pin, with utility methods to create sources,
 * sinks, flows, or just akka actor props.
 *
 * @param <T>
 *            the type of GPIO configuration, IN or OUT.
 */
public abstract class GPIO<T extends GPIO<T>> {
	/**
	 * Models the state of a digital GPIO pin: ON (high), OFF (low), or TOGGLE (will
	 * become ON if it was OFF, and vice-versa).
	 */
	public enum State {
		HIGH, LOW, TOGGLE
	}

	/*
	 * Settings
	 */
	private Pin pin;

	private PinMode pinMode;

	private PinPullResistance pullResistance = PinPullResistance.OFF;

	private String name;

	public Pin getPin() {
		return pin;
	}

	public abstract Props asProps();

	public abstract T analog();

	public abstract T digital();

	// This is only used internally to make chaining work
	protected abstract T getThis();

	public PinMode getPinMode() {
		return pinMode;
	}

	public T withPullupResistor() {
		pullResistance = PinPullResistance.PULL_UP;
		return getThis();
	}

	public T withPulldownResistor() {
		pullResistance = PinPullResistance.PULL_DOWN;
		return getThis();
	}

	public PinPullResistance getPullResistance() {
		return pullResistance;
	}

	public T named(String name) {
		this.name = name;
		return getThis();
	}

	public String getName() {
		return name;
	}

	/*
	 * Streaming
	 */
	public static Out out(int pin) {
		return new Out(RaspiPin.getPinByAddress(pin));
	}

	public static Out out(Pin pin) {
		return new Out(pin);
	}

	public static class Out extends GPIO<Out> {

		private PinState initialState = null;

		private PinState shutdownState = null;

		private Double initialValue = null;

		private Out(Pin pin) {
			super.pin = pin;
			super.pinMode = PinMode.DIGITAL_OUTPUT;
			pin.getName();
		}

		@Override
		public Out analog() {
			super.pinMode = PinMode.ANALOG_OUTPUT;
			return this;
		}

		@Override
		public Out digital() {
			super.pinMode = PinMode.DIGITAL_OUTPUT;
			return this;
		}

		protected Out pwm() {
			super.pinMode = PinMode.PWM_OUTPUT;
			return this;
		}

		protected Out tone() {
			super.pinMode = PinMode.PWM_TONE_OUTPUT;
			return this;
		}

		public Out initiallyHigh() {
			initialState = PinState.HIGH;
			return this;
		}

		public Out initiallyLow() {
			initialState = PinState.LOW;
			return this;
		}

		public PinState getInitialState() {
			return initialState;
		}

		public Out initiallyAt(double value) {
			initialValue = value;
			return this;
		}

		public Double getInitialValue() {
			return initialValue;
		}

		public Out shuttingDownHigh() {
			shutdownState = PinState.HIGH;
			return this;
		}

		public Out shuttingDownLow() {
			shutdownState = PinState.LOW;
			return this;
		}

		public PinState getShutdownState() {
			return shutdownState;
		}

		/**
		 * This is used to allow the superclass to do chaining properly
		 */
		@Override
		protected Out getThis() {
			return this;
		}

		public Sink<State, NotUsed> asSink(ActorSystem system) {
			return Flow.of(State.class).ask(system.actorOf(asProps()), State.class, Timeout.apply(1, TimeUnit.SECONDS))
					.to(Sink.ignore());
		}

		public Flow<State, State, NotUsed> asFlow(ActorSystem system) {
			return Flow.fromGraph(GraphDSL.create(b -> {
				return b.add(Flow.of(State.class).ask(system.actorOf(asProps()), State.class,
						Timeout.apply(1, TimeUnit.SECONDS)));
			}));
		}

		@Override
		public Props asProps() {
			return Props.create(GPIOOutActor.class, this);
		}

	}

	// TODO: Source

}
