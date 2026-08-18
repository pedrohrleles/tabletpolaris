package com.polarisrh.tabletpolaris

import android.content.Context
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.repository.DeviceAuthRepository
import com.polarisrh.tabletpolaris.data.repository.FakeDeviceAuthRepository
import com.polarisrh.tabletpolaris.data.repository.FakePunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchRepository

class AppContainer(context: Context) {

    val credentialsStore: DeviceCredentialsStore = DeviceCredentialsStore(context)
    val deviceAuthRepository: DeviceAuthRepository = FakeDeviceAuthRepository(credentialsStore)
    val punchRepository: PunchRepository = FakePunchRepository()
}
