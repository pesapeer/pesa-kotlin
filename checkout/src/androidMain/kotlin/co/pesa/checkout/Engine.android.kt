package co.pesa.checkout

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CertificatePinner

internal actual fun httpClientEngine(pins: List<String>): HttpClientEngine = OkHttp.create {
    config {
        if (pins.isNotEmpty()) {
            val b = CertificatePinner.Builder()
            for (p in pins) b.add("checkout.pesa.co", p).add("*.pesa.co", p)
            certificatePinner(b.build())
        }
    }
}
