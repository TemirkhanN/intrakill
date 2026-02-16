package me.nasukhov.intrakill.scene

import androidx.compose.ui.graphics.ImageBitmap
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

expect fun ByteArray.asImageBitmap(): ImageBitmap

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

data class OptionalValue<T: Any>(var value: T? = null) {
    fun get(): T = value!!
    fun set(value: T) {
        this.value = value
    }

    fun getOrNull(): T? = value
}