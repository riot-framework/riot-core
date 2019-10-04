package riot;

import com.pi4j.io.gpio.BananaPiPin;
import com.pi4j.io.gpio.BpiPin;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.NanoPiPin;
import com.pi4j.io.gpio.OdroidC1Pin;
import com.pi4j.io.gpio.OrangePiPin;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.RCMPin;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.system.SystemInfo;

/**
 * Utility class used internally in this package. Contains helper methods.
 */
class Utils {
    private static boolean shutdownThreadRegistered = false;

    private Utils() {
        // No instantiation necessary.
    }

    /**
     * Maps a pin number to a concrete Pin instance for the current board. This may fail if the board is not known in
     * this version of RIoT.
     *
     * @param address the pin number in the board's own numbering (i.e. NOT in Broadcom's numbering).
     * @return the corresponding Pin object
     * @throws a RuntimeException if mapping fails (pin or board type is unknown, or board type could not be
     *           identified).
     */
    static final Pin asPin(int address) {
        try {
            switch (SystemInfo.getBoardType()) {

                case RaspberryPi_A:
                case RaspberryPi_B_Rev1:
                case RaspberryPi_B_Rev2:
                case RaspberryPi_A_Plus:
                case RaspberryPi_B_Plus:
                case RaspberryPi_2B:
                case RaspberryPi_3B:
                case RaspberryPi_3B_Plus:
                case RaspberryPi_Zero:
                case RaspberryPi_ZeroW:
                case RaspberryPi_Alpha:
                case RaspberryPi_Unknown:
                    return RaspiPin.getPinByAddress(address);

                case RaspberryPi_ComputeModule:
                case RaspberryPi_ComputeModule3:
                    return RCMPin.getPinByAddress(address);

                case BananaPi:
                case BananaPro:
                    return BananaPiPin.getPinByAddress(address);

                case Bpi_M1:
                case Bpi_M1P:
                case Bpi_M2:
                case Bpi_M2P:
                case Bpi_M2P_H2_Plus:
                case Bpi_M2P_H5:
                case Bpi_M2U:
                case Bpi_M2U_V40:
                case Bpi_M2M:
                case Bpi_M3:
                case Bpi_R1:
                case Bpi_M64:
                    return BpiPin.getPinByAddress(address);

                case Odroid:
                    return OdroidC1Pin.getPinByAddress(address);

                case OrangePi:
                    return OrangePiPin.getPinByAddress(address);

                case NanoPi_M1:
                case NanoPi_M1_Plus:
                case NanoPi_M3:
                case NanoPi_NEO:
                case NanoPi_NEO2:
                case NanoPi_NEO2_Plus:
                case NanoPi_NEO_Air:
                case NanoPi_S2:
                case NanoPi_A64:
                case NanoPi_K2:
                    return NanoPiPin.getPinByAddress(address);

                default:
                    throw new RuntimeException("Unknown pin mapping for board " + SystemInfo.getBoardType().toString());
            }

        } catch (Exception e) {
            throw new RuntimeException("Unable to find pin for pin number " + address, e);
        }
    }

    /**
     * Registers a shutdown hook that will shut down the GPIO controller when the VM stops.
     */
    static final synchronized void registerShutdownHook() {
        if (shutdownThreadRegistered) {
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                GpioFactory.getInstance().shutdown();
            }
        });

        shutdownThreadRegistered = true;
    }
}
