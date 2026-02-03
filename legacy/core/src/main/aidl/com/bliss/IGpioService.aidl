package com.bliss;

/**
 * AIDL interface for controlling GPIO LEDs.
 */
interface IGpioService {
    /**
     * Controls blinking behavior for a GPIO pin.
     *
     * @param gpioPinNumber The GPIO pin number
     * @param startBlink Whether to start blinking
     * @param blinkRate Blink rate in hertz
     * @param stopBlink Whether to stop blinking
     */
    void controlGpioLed(
        int gpioPinNumber,
        boolean startBlink,
        int blinkRate,
        boolean stopBlink
    );
}
