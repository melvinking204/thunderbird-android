package com.fsck.k9.led

import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.bliss.IGpioService
import java.lang.reflect.Method

class GpioService {
    private var mService: IGpioService? = null

    init {
        connect()
    }

    private fun connect() {
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, SERVICE_NAME) as IBinder?
            if (binder != null) {
                mService = IGpioService.Stub.asInterface(binder)
                Log.i(TAG, "Connected to gpioservice")
            } else {
                Log.e(TAG, "gpioservice binder is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to gpioservice", e)
        }
    }

    val isConnected: Boolean
        get() = mService != null

    fun startBlinking(gpioPin: Int, blinkRateHz: Int): Boolean {
        Log.d(TAG, "startBlinking called: pin=$gpioPin, rate=$blinkRateHz")
        return controlGpioLedInternal(
            gpioPin,
            startBlink = true,
            blinkRate = blinkRateHz,
            stopBlink = false
        )
    }

    fun stopBlinking(gpioPin: Int): Boolean {
        Log.d(TAG, "stopBlinking called: pin=$gpioPin")
        return controlGpioLedInternal(
            gpioPin,
            startBlink = false,
            blinkRate = 0, // ignored
            stopBlink = true
        )
    }

    private fun controlGpioLedInternal(
        gpioPinNumber: Int,
        startBlink: Boolean,
        blinkRate: Int,
        stopBlink: Boolean
    ): Boolean {
        if (mService == null) {
            Log.e(TAG, "gpioservice not connected, attempting to reconnect")
            reconnect()
            if (mService == null) {
                Log.e(TAG, "gpioservice still not connected")
                return false
            }
        }
        return try {
            mService!!.controlGpioLed(
                gpioPinNumber,
                startBlink,
                blinkRate,
                stopBlink
            )
            Log.d(TAG, "controlGpioLed success: pin=$gpioPinNumber, start=$startBlink, stop=$stopBlink")
            true
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException calling controlGpioLed", e)
            false
        }
    }

    fun reconnect() {
        Log.d(TAG, "reconnect called")
        mService = null
        connect()
    }

    companion object {
        private const val TAG = "GpioService"
        private const val SERVICE_NAME = "gpioservice"
    }
}
