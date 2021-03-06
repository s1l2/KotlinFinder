package com.kotlinconf.library.domain.repository

import com.github.aakira.napier.Napier
import dev.bluefalcon.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.kotlinconf.library.domain.UI
import com.kotlinconf.library.domain.entity.BeaconInfo


class SpotSearchRepository(
    context: ApplicationContext,
    private val gameDataRepository: GameDataRepository
) : BlueFalconDelegate {
    private val bf: BlueFalcon = BlueFalcon(context, null)

    private val lastBeaconRssi = mutableMapOf<String, Int>()

    init {
        this.bf.delegates.add(this)
    }

    fun startScanning() {
        if (this.bf.isScanning) {
            return
        }

        Napier.d(message = "starting search...")

        this.doScanning()
    }

    fun stopScanning() {
        this.bf.stopScanning()
    }

    fun isScanning(): Boolean {
        return this.bf.isScanning
    }

    fun restartScanning() {
        Napier.d(">>> SCANNING RESTARTED")
        this.doScanning()
    }

    private fun doScanning() {
        GlobalScope.launch(Dispatchers.UI) {
            while (isActive) {
                if (tryStartScan())
                    break

                delay(500)
            }
        }
    }

    private fun tryStartScan(): Boolean {
        try {
            this.bf.scan()
            return true
        } catch (error: Throwable) {
            return when (error) {
                is BluetoothUnsupportedException,
                is BluetoothNotEnabledException,
                is BluetoothResettingException,
                is BluetoothUnknownException -> {
                    Napier.e(message = "known BT exception, try again later", throwable = error)
                    false
                }
                else -> {
                    Napier.e(message = "fail scan", throwable = error)
                    true
                }
            }
        }
    }

    private fun sendBeaconInfo(bluetoothPeripheral: BluetoothPeripheral) {
        val name: String = bluetoothPeripheral.name ?: return
        val rssi: Int = bluetoothPeripheral.rssi?.toInt() ?: return

        val processedRssi = if (rssi == 127) {
            lastBeaconRssi[name] ?: return
        } else {
            lastBeaconRssi[name] = rssi
            rssi
        }

        val beaconInfo = BeaconInfo(name = name, rssi = processedRssi)

        GlobalScope.launch(Dispatchers.UI) {
            Napier.d("beaconInfo: $beaconInfo")
            gameDataRepository.beaconsChannel.send(beaconInfo)
        }
    }

    override fun didDiscoverDevice(bluetoothPeripheral: BluetoothPeripheral) {
        this.sendBeaconInfo(bluetoothPeripheral)
    }

    override fun didConnect(bluetoothPeripheral: BluetoothPeripheral) {}
    override fun didDisconnect(bluetoothPeripheral: BluetoothPeripheral) {}
    override fun didDiscoverServices(bluetoothPeripheral: BluetoothPeripheral) {}
    override fun didDiscoverCharacteristics(bluetoothPeripheral: BluetoothPeripheral) {}
    override fun didCharacteristcValueChanged(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic
    ) {
    }

    override fun didUpdateMTU(bluetoothPeripheral: BluetoothPeripheral) {}

    override fun didRssiUpdate(bluetoothPeripheral: BluetoothPeripheral) {
        this.sendBeaconInfo(bluetoothPeripheral)
    }

    override fun scanDidFailed(error: Throwable) {
        Napier.e("scan failed: $error")
        doScanning()
    }
}