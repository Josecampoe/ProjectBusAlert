package com.busalert.shared.di

import com.busalert.shared.data.MockTransitRepositoryImpl
import com.busalert.shared.domain.TransitRepository

object SharedDependencies {
    val transitRepository: TransitRepository by lazy {
        MockTransitRepositoryImpl()
    }
}
