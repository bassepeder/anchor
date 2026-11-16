package app.anchor

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowDevicePolicyManager
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi", shadows = [AlwaysOnVpnShadow::class])
class ScreenshotTest {
    private val compose = createComposeRule()

    // Release unit tests don't merge ui-test-manifest, which normally declares the compose host
    // activity, so it's registered before the compose rule launches it.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            shadowOf(ctx.packageManager).addOrUpdateActivity(
                ActivityInfo().apply {
                    name = ComponentActivity::class.java.name
                    packageName = ctx.packageName
                }
            )
        }
    }).around(compose)

    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun onboarding() {
        makeDeviceOwner()
        compose.mainClock.autoAdvance = false
        compose.setContent { AnchorTheme(dark = false) { OnboardingWizard(onActivate = {}, onDone = {}) } }
        capture("onboarding-welcome")
        click("Get started")
        capture("onboarding-presets")
        click("Next")
        click("Next")
        capture("onboarding-friction")
        click("Next")
        click("Next")
        capture("onboarding-finish")
    }

    @Test
    fun app() {
        protectedDemo()
        compose.mainClock.autoAdvance = false
        compose.setContent { AnchorTheme(dark = false) { AnchorApp(onActivate = {}) } }
        capture("home")
        click("Sites")
        capture("sites")
        click("Rules")
        capture("rules")
    }

    @Test
    fun homeDark() {
        protectedDemo()
        compose.mainClock.autoAdvance = false
        compose.setContent { AnchorTheme(dark = true) { AnchorApp(onActivate = {}) } }
        capture("home-dark")
    }

    private fun makeDeviceOwner() {
        shadowOf(ctx.getSystemService(DevicePolicyManager::class.java)).setDeviceOwner(Policies.admin(ctx))
    }

    private fun protectedDemo() {
        makeDeviceOwner()
        Store.setOnboarded(ctx, true)
        Store.setActivated(ctx, true)
        val now = System.currentTimeMillis()
        Store.edit(ctx) {
            putLong("activated_at", now - 23 * 86_400_000L)
            putInt("list_size", 98_213)
            putLong("list_updated_at", now - 2 * 86_400_000L)
            putStringSet(Store.HIDDEN, setOf("a", "b", "c", "d", "e", "f"))
            putStringSet(Store.USER_SITES, setOf("news.ycombinator.com", "linkedin.com/feed"))
        }
        ctx.getSharedPreferences("stats", Context.MODE_PRIVATE).edit(commit = true) {
            putString("day", LocalDate.now().toString())
            putInt("today", 37)
            putLong("total", 4_812)
        }
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage(
            filePath = "../docs/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }
}

@Implements(DevicePolicyManager::class)
class AlwaysOnVpnShadow : ShadowDevicePolicyManager() {
    @Implementation
    protected fun getAlwaysOnVpnPackage(admin: ComponentName): String = RuntimeEnvironment.getApplication().packageName
}
