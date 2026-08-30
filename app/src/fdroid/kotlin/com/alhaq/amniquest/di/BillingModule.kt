package com.alhaq.amniquest.di

import com.alhaq.amniquest.billing.BillingManager
import com.alhaq.amniquest.billing.FdroidBillingManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    @Singleton
    abstract fun bindBillingManager(impl: FdroidBillingManager): BillingManager
}
