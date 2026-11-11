package com.nuvio.tv.core.sync

import android.content.Context
import com.nuvio.tv.data.local.ProfileDataStore
import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import com.nuvio.tv.core.tmdb.TmdbApiKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Storage of the account's TMDB key, so the logic can be tested without Android's keystore. */
internal interface AccountTmdbKeyPersistence {
    fun read(profileId: Int): String?
    fun write(profileId: Int, value: String?)
    fun clear()
}

/**
 * NuvioArcKai: the TMDB key the user keeps in their Nuvio account (pulled read-only from provider credentials),
 * encrypted per profile with the Android keystore. It is never pushed back: the TV only reads it.
 */
@Singleton
class AccountTmdbKeyStore internal constructor(
    private val persistence: AccountTmdbKeyPersistence,
    private val activeProfileId: () -> Int
) : ProfileScopedCredentialStore {

    // ProfileDataStore, not ProfileManager: ProfileManager depends on every ProfileScopedCredentialStore.
    @Inject
    constructor(@ApplicationContext context: Context, profileDataStore: ProfileDataStore) : this(
        persistence = object : AccountTmdbKeyPersistence {
            private val preferences = ProtectedProfilePreferences(context, "kai_account_tmdb", "com.nuvio.tv.kai.tmdb.v1")
            override fun read(profileId: Int) = preferences.read(profileId)
            override fun write(profileId: Int, value: String?) = preferences.write(profileId, value)
            override fun clear() = preferences.clear()
        },
        activeProfileId = { ActiveProfileHolder.current }
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            profileDataStore.activeProfileId.collect { profileId ->
                ActiveProfileHolder.current = profileId
                selectProfile(profileId)
            }
        }
    }

    private object ActiveProfileHolder {
        @Volatile var current: Int = 1
    }

    /** Loads [profileId]'s key into [TmdbApiKey] (or clears it when the profile has none). */
    internal fun selectProfile(profileId: Int) {
        TmdbApiKey.setAccountKey(runCatching { persistence.read(profileId) }.getOrNull())
    }

    /** Saves what the account holds for [profileId]: a key, or null when the account has none. */
    internal fun applyFromAccount(profileId: Int, value: String?) {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() }
        if (runCatching { persistence.read(profileId) }.getOrNull() != clean) persistence.write(profileId, clean)
        if (profileId == activeProfileId()) TmdbApiKey.setAccountKey(clean)
    }

    override fun removeProfile(profileId: Int) {
        persistence.write(profileId, null)
        if (profileId == activeProfileId()) TmdbApiKey.setAccountKey(null)
    }

    override fun clearAllProfiles() {
        persistence.clear()
        TmdbApiKey.setAccountKey(null)
    }

    override fun toString(): String = "AccountTmdbKeyStore(<redacted>)"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountTmdbKeyModule {
    @Binds @IntoSet
    abstract fun credentialStore(store: AccountTmdbKeyStore): ProfileScopedCredentialStore
}
