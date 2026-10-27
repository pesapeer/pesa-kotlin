package co.pesa.checkout

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.certificates.CertificatePinner

/**
 * Darwin engine with Ktor's own SPKI pinner (sha256/… pins on the leaf or
 * intermediate public key), which hooks the URLSession authentication
 * challenge. Without pins the system trust store decides.
 */
internal actual fun httpClientEngine(pins: List<String>): HttpClientEngine = Darwin.create {
    if (pins.isNotEmpty()) {
        val b = CertificatePinner.Builder()
        for (p in pins) b.add("checkout.pesa.co", p).add("*.pesa.co", p)
        handleChallenge(b.build())
    }
}
