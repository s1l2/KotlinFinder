package org.example.library.domain.repository

import com.github.aakira.napier.Napier
import dev.icerock.moko.mvvm.livedata.LiveData
import dev.icerock.moko.mvvm.livedata.MutableLiveData
import dev.icerock.moko.mvvm.livedata.readOnly
import dev.icerock.moko.network.generated.apis.GameApi
import dev.icerock.moko.network.generated.models.ConfigResponse
import dev.icerock.moko.network.generated.models.ProximityResponse
import dev.icerock.moko.network.generated.models.RegisterResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.example.library.domain.UI
import org.example.library.domain.entity.*
import org.example.library.domain.entity.toDomain
import org.example.library.domain.storage.KeyValueStorage


@FlowPreview
@UseExperimental(ExperimentalCoroutinesApi::class)
class GameDataRepository internal constructor(
    private val gameApi: GameApi,
    private val collectedSpotsRepository: CollectedSpotsRepository,
    private val storage: KeyValueStorage
) {
    val beaconsChannel: Channel<BeaconInfo> = Channel(Channel.BUFFERED)
    var gameConfig: GameConfig? = null

    private val _currentDiscoveredBeaconId: MutableLiveData<Int?> = MutableLiveData(null)
    val currentDiscoveredBeaconId: LiveData<Int?> = this._currentDiscoveredBeaconId.readOnly()

    private val _isGameEnded: MutableLiveData<Boolean> = MutableLiveData(false)
    val isGameEnded: LiveData<Boolean> = this._isGameEnded.readOnly()

    private val _proximityInfoChannel: Channel<ProximityInfo?> = Channel()
    val proximityInfo: Flow<ProximityInfo?> = channelFlow {
        val job = launch {
            while (isActive) {
                val info: ProximityInfo? = _proximityInfoChannel.receive()
                Napier.d("got strength $info")
                send(info)
            }
        }

        awaitClose {
            job.cancel()
        }
    }

    fun startScanning(didReceiveNoDevicesBlock: (() -> Unit)?) {
        GlobalScope.launch(Dispatchers.UI) {
            while (isActive) {
                val scanResults = mutableListOf<BeaconInfo>()
                var beacon = beaconsChannel.poll()
                while (beacon != null) {
                    scanResults.add(beacon)
                    beacon = beaconsChannel.poll()
                }

                Napier.d("scanResults count: ${scanResults.count()}")

                if (scanResults.isNotEmpty()) {
                    async {
                        if (_isGameEnded.value)
                            return@async

                        val info: ProximityInfo? = sendBeaconsInfo(scanResults)
                        val config: GameConfig = gameConfig ?: return@async

                        _proximityInfoChannel.send(info)

                        val collectedIds: List<Int> = collectedSpotsRepository.collectedSpotIds() ?: emptyList()
                        val discoveredIds: List<Int> = info?.discoveredBeaconsIds ?: emptyList()

                        val newIds: List<Int> = discoveredIds.minus(collectedIds)

                        Napier.d("collected: $collectedIds, discovered: $discoveredIds, new: $newIds")

                        _currentDiscoveredBeaconId.value = newIds.firstOrNull()

                        if (info?.discoveredBeaconsIds != null)
                            collectedSpotsRepository.setCollectedSpotIds(info.discoveredBeaconsIds)

                        _isGameEnded.value = (discoveredIds.count() == config.active)
                    }
                } else {
                    didReceiveNoDevicesBlock?.invoke()
                }

                delay(500)
            }
        }
    }

    fun isUserRegistered(): Boolean {
        return this.storage.isUserRegistered
    }

    fun taskForSpotId(id: Int): TaskItem? {
        val items: List<TaskItem> = this.gameConfig?.tasks ?: return null

        return items.firstOrNull { item: TaskItem ->
            item.code == id
        }
    }

    fun resetCookies() {
        this.storage.cookies = null

        Napier.d("COOKIES CLEARED")
    }

    suspend fun loadGameConfig(): GameConfig? {
        val config: ConfigResponse = this.gameApi.finderConfigGet()
        Napier.d(message = "Game config response = $config")

        this.gameConfig = config.toDomain()

        return this.gameConfig
    }

    suspend fun sendWinnerName(name: String): String? {
        val response: RegisterResponse = this.gameApi.finderRegisterGet(name)
        Napier.d(message = "Register response: $response")

        return response.message
    }

    private suspend fun sendBeaconsInfo(beacons: List<BeaconInfo>): ProximityInfo? {
        val onlyLast = beacons
            .filter { it.rssi < 0 }
            .reversed()
            .distinctBy { it.name }

        if (onlyLast.isEmpty()) {
            Napier.d(message = "all filtered = $beacons")
            return null
        }

        val beaconsString: String = onlyLast.joinToString(separator = ",") { "${it.name}:${it.rssi}" }

        Napier.d(message = "proximity = $beaconsString")

        val info: ProximityInfo

        try {
            val response: ProximityResponse = this.gameApi.finderProximityGet(beaconsString)
            Napier.d(message = "received = $response")

            info = response.toDomain()
        } catch (error: Throwable) {
            Napier.e(message = "can't get proximity", throwable = error)
            Napier.e(message = error.toString())
            return null
        }

        return info
    }
}