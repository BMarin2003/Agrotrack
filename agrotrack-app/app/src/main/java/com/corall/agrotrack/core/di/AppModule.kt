package com.corall.agrotrack.core.di

import com.corall.agrotrack.data.remote.api.AuthApiService
import com.corall.agrotrack.data.remote.api.HelpDeskApiService
import com.corall.agrotrack.data.remote.api.SensorsApiService
import com.corall.agrotrack.data.remote.api.TelemetryApiService
import com.corall.agrotrack.data.remote.api.UsersApiService
import com.corall.agrotrack.data.repository.AuthRepositoryImpl
import com.corall.agrotrack.data.repository.GatewayRepositoryImpl
import com.corall.agrotrack.data.repository.TelemetryRepositoryImpl
import com.corall.agrotrack.domain.repository.AuthRepository
import com.corall.agrotrack.domain.repository.GatewayRepository
import com.corall.agrotrack.domain.repository.TelemetryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl,
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindTelemetryRepository(
        impl: TelemetryRepositoryImpl,
    ): TelemetryRepository

    @Binds
    @Singleton
    abstract fun bindGatewayRepository(
        impl: GatewayRepositoryImpl,
    ): GatewayRepository

    companion object {
        @Provides
        @Singleton
        fun provideAuthApi(
            retrofit: Retrofit,
        ): AuthApiService = retrofit.create(AuthApiService::class.java)

        @Provides
        @Singleton
        fun provideTelemetryApi(
            retrofit: Retrofit,
        ): TelemetryApiService = retrofit.create(TelemetryApiService::class.java)

        @Provides
        @Singleton
        fun provideSensorsApi(
            retrofit: Retrofit,
        ): SensorsApiService = retrofit.create(SensorsApiService::class.java)

        @Provides
        @Singleton
        fun provideUsersApi(
            retrofit: Retrofit,
        ): UsersApiService = retrofit.create(UsersApiService::class.java)

        @Provides
        @Singleton
        fun provideHelpDeskApi(
            retrofit: Retrofit,
        ): HelpDeskApiService = retrofit.create(HelpDeskApiService::class.java)
    }
}