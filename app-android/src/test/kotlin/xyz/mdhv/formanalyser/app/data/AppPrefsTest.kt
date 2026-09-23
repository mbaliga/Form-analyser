package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPrefsTest {
    @Test
    fun pairedSessionWalkthroughDefaultsToUnseenAndPersistsCompletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPrefs(context)

        prefs.setPairedSessionWalkthroughSeen(false)
        assertFalse(prefs.pairedSessionWalkthroughSeen.first())

        prefs.setPairedSessionWalkthroughSeen(true)
        assertTrue(prefs.pairedSessionWalkthroughSeen.first())
    }
}
