# riot-core
The core RIoT framework provides Akka classes for the Raspberry Pi and similar small single-board computers. 

## GPIO

RIoT provides wrappers around the [PI4J] library's objects to access input and output GPIO pins. To create one of these wrappers, use the `GPIO` class.

### Creating Akka Streams components

Simply use the `in` method to access a pin as an input, `out` to access it as an output, passing the Wiring / Pi4J [pin number] as a parameter (these differ from the numberring scheme used by Broadcom for the CPU). Then call `asSource`, `asSink` or `asFlow` to create an Akka Streams source, sink, or flow object:

```java
gpio3InputSource = GPIO.in(3).asSource(system);
gpio7OutputSink = GPIO.out(7).asSink(system);
``` 
You can further configure the GPIO pin by calling methods before the final `asSource`, `asSink` or `asFlow` call: 

- Pins default to Digital mode (they have a state that is either High or Low, On or Off). call `analog()` for an Analog pin, that can have a range of values between 0 and 1. Call `pwm()` on an output pin to get a PWM output, as would be used for servos.
- The Broadcom CPU is able to switch resistors between a pin and the ground or positive. Use `withPullupResistor()` to have a resistor between the pin and positive, and `withPullDownResistor()` to have one between the pin and ground. For example, if you have wired a switch between an input pin and the ground, you'll want a resistor between that pin and positive, so that this pin's state is 'pulled' high when the switch is not pressed.
- Output pins can have a value set before the constructred RIoT object has received any message: Use `initiallyHigh()` and `initiallyLow()` to set this initial value with digital pins, and use `initiallyAt(...)` to set the initial value of an analog or PWM pin.
- Similarly, the value they will be reset to when the program terminates can be set using `shuttingDownHigh()` and `ishuttingDownLow()` for digital pins, and `shuttindDownAt(...)` for analog or PWM pins.

### Behaviour of the Akka Streams components

GPIO defines an enum called `State`, which models the states that a digital GPIO port can have.

- A digital GPIO `Source` will emit either `State.HIGH` or `State.LOW` whenever a Pin's state changes. 
- A digital GPIO `Sink`will accept `State.HIGH`, `State.LOW`, or `State.TOGGLE`, setting the state high, low, or the opposite of its current state, respectively.
- A digital GPIO `Flow` will accept the same messages, and emit the current state of the Pin: If `State.TOGGLE` is repeatedly sent to it, it will altenatingly emit `State.HIGH`, then `State.LOW`.

Analog pins can have any value between 0 and 1. The streams components behave similarly as with digital pins, but using `Float` objects:

- An analog GPIO `Source` will emit a `Float` whenever its measured value changes. 
- An analog GPIO `Sink` will accept `Float` messages, and set the value accordingly.
- A digital GPIO `Flow` will accept `Float` messages,  and emit the current value of the Pin.

PWM pins behave similarly, but accept, in addition to the `Float`, also `Integer` messages with a value expressed in number of PWM steps (bewteen 0 and 1024).

### Constructing Akka actors

Regular Akka actors can also be created. Using the GPIO class' `toProps()` methods to create an Akka Props object, then Akka's actorOf method  to get an `ActorRef`. `GPIO.State`, `Float` or `Integer` messages, depending on the GPIO type, can then be sent to it:

```java
Props gpio7Props = GPIO.out(7).asProps();
ActorRef gpio7 = system.actorOf(gpio7Props);
gpio7.tell(GPIO.State.HIGH, self());

```

For the actor to send updates about the pin's state, it will need to know the recipient's `ActorRef`. This is done at construction time using the `notifyActor` method:

```java
Props gpio7Props = GPIO.in(7). notifyActor(myOtherActor).asProps();
system.actorOf(gpio7Props);
```


[sbt]: https://www.scala-sbt.org/1.x/docs/Setup.html
[streams.g8]: https://github.com/riot-framework/streams.g8
[riot-core]: https://github.com/riot-framework/riot-core
[sbt-riot]: https://github.com/riot-framework/sbt-riotctl
[riot]: https://riot.community
[akka streams]: https://doc.akka.io/docs/akka/current/stream/stream-quickstart.html
[giter8]: http://www.foundweekends.org/giter8/
[systemctl]: https://www.digitalocean.com/community/tutorials/how-to-use-systemctl-to-manage-systemd-services-and-units
[pi4j]: https://pi4j.com
[pin numbers]: https://pi4j.com/1.2/pins

[led]: https://www.aliexpress.com/item/32700885768.html
