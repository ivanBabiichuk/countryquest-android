package io.github.ivanbabiichuk.countryquest.core.time

import javax.inject.Inject

class SystemTimeProvider @Inject constructor() : TimeProvider {

    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}