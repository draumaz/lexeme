package io.github.draumaz.lexeme

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.draumaz.lexeme.data.SettingsStore
import io.github.draumaz.lexeme.data.WiktionaryApi

class LexemeApp : Application(), SingletonImageLoader.Factory {
    val settings by lazy { SettingsStore(this) }

    /**
     * Images share the API's OkHttp client so they carry the same User-Agent (Wikimedia's
     * media servers refuse anonymous clients), and animated GIFs decode as animations:
     * stroke-order diagrams are useless as a first frame.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = WiktionaryApi.defaultClient()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
                add(AnimatedImageDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
