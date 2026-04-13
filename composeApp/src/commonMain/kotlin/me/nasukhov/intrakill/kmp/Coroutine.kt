package me.nasukhov.intrakill.kmp

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

fun InstanceKeeper.coroutineScope(): CoroutineScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    getOrCreate {
        object : InstanceKeeper.Instance {
            override fun onDestroy() {
                scope.cancel()
            }
        }
    }
    return scope
}
