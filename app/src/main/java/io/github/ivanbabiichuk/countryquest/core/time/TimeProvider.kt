package io.github.ivanbabiichuk.countryquest.core.time

interface TimeProvider {
    fun currentTimeMillis(): Long
}