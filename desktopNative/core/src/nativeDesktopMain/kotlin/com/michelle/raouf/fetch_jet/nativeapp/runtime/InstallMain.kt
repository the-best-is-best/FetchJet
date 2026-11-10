package com.michelle.raouf.fetch_jet.nativeapp.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
fun installMainDispatcher(dispatcher: MainCoroutineDispatcher) {
    Dispatchers.injectMain(dispatcher)
}
