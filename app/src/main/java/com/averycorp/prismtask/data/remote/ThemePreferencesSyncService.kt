package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.preferences.ThemePreferences
import com.averycorp.prismtask.data.remote.sync.PrismSyncLogger
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs the selected PrismTheme to/from Firestore at
 * /users/{uid}/settings/theme_preferences.
 *
 * Push: observes [ThemePreferences.flowOfThemeChanges], debounces 500 ms,
 * writes { prism_theme, updated_at } with merge semantics.
 *
 * Pull: Firestore snapshot listener applies remote value when remote
 * updated_at is newer than local (last-write-wins).
 */
@Singleton
class ThemePreferencesSyncService @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val authManager: AuthManager,
    private val logger: PrismSyncLogger
) {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var settingsListener: ListenerRegistration? = null

    private fun docRef() = authManager.userId?.let {
        firestore.collection("users").document(it)
            .collection("settings").document("theme_preferences")
    }

    /** Start the reactive push observer. Safe to call unconditionally; no-ops when signed out. */
    fun startPushObserver() {
        scope.launch {
            themePreferences.flowOfThemeChanges
                .debounce(500L)
                .collect {
                    if (authManager.userId == null) return@collect
                    val updatedAt = themePreferences.getThemeUpdatedAt()
                    val lastSynced = themePreferences.getThemeLastSyncedAt()
                    if (updatedAt <= lastSynced) return@collect
                    pushNow()
                }
        }
    }

    /** Called from AuthViewModel after a successful sign-in. */
    fun startAfterSignIn() {
        startPullListener()
        scope.launch { pushNow(force = true) }
    }

    /** Called from AuthViewModel on sign-out. Does NOT clear local DataStore. */
    fun stopAfterSignOut() {
        stopPullListener()
    }

    private fun startPullListener() {
        stopPullListener()
        val ref = docRef() ?: return
        settingsListener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                logger.warn(operation = "[ThemeSync] listener.error", throwable = error)
                return@addSnapshotListener
            }
            val data = snapshot?.data ?: return@addSnapshotListener
            scope.launch { applyRemoteData(data) }
        }
    }

    private fun stopPullListener() {
        settingsListener?.remove()
        settingsListener = null
    }

    private suspend fun applyRemoteData(data: Map<String, Any>) {
        val remoteUpdatedAt = (data["updated_at"] as? Number)?.toLong() ?: return
        val localUpdatedAt = themePreferences.getThemeUpdatedAt()
        if (remoteUpdatedAt <= localUpdatedAt) return

        val themeName = data["prism_theme"] as? String ?: return
        themePreferences.applyRemoteTheme(themeName, remoteUpdatedAt)
        logger.info(
            operation = "[ThemeSync] pull",
            detail = "theme=$themeName | status=success"
        )
    }

    private suspend fun pushNow(force: Boolean = false) {
        val uid = authManager.userId ?: return

        if (!force) {
            val updatedAt = themePreferences.getThemeUpdatedAt()
            val lastSynced = themePreferences.getThemeLastSyncedAt()
            if (updatedAt <= lastSynced) return
        }

        val themeName = themePreferences.getPrismTheme().first()
        val now = System.currentTimeMillis()
        val payload = mapOf("prism_theme" to themeName, "updated_at" to now)

        val ref = firestore.collection("users").document(uid)
            .collection("settings").document("theme_preferences")
        try {
            ref.set(payload, SetOptions.merge()).await()
            themePreferences.setThemeLastSyncedAt(now)
            logger.info(
                operation = "[ThemeSync] push",
                detail = "theme=$themeName | status=success"
            )
        } catch (e: Exception) {
            logger.error(
                operation = "[ThemeSync] push",
                detail = "status=failed",
                throwable = e
            )
        }
    }
}
