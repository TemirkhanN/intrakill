package me.nasukhov.intrakill.scene

import androidx.compose.ui.graphics.ImageBitmap
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Enumeration

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

fun <T : Any> Value<T>.asFlow(): Flow<T> = callbackFlow {
    // Subscribe for changes in Value. Subscriber sends that value into the flow.
    // It's, technically, intended to be used as a pipeline.
    val cancellation = subscribe { value ->
        trySend(value)
    }

    // 2. When the Flow is closed/cancelled, we call cancel() on the token
    awaitClose {
        cancellation.cancel()
    }
}

fun <T> Sequence<T>.asEnumeration(): Enumeration<T> = object : Enumeration<T> {
    val iterator = this@asEnumeration.iterator()
    override fun hasMoreElements(): Boolean = iterator.hasNext()
    override fun nextElement(): T = iterator.next()
}