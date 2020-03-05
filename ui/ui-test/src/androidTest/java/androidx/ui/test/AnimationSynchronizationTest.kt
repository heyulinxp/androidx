/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.test

import android.os.Handler
import android.os.Looper
import androidx.animation.FloatPropKey
import androidx.animation.LinearEasing
import androidx.animation.transitionDefinition
import androidx.compose.Composable
import androidx.compose.State
import androidx.compose.mutableStateOf
import androidx.compose.remember
import androidx.test.espresso.Espresso.onIdle
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import androidx.ui.animation.Transition
import androidx.ui.foundation.Canvas
import androidx.ui.foundation.DrawBackground
import androidx.ui.geometry.Rect
import androidx.ui.graphics.Color
import androidx.ui.graphics.Paint
import androidx.ui.layout.Container
import androidx.ui.layout.LayoutSize
import androidx.ui.test.android.ComposeIdlingResource
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

private const val nonIdleDuration = 1000L

private const val animateFromX = 0f
private const val animateToX = 50f
private val animatedRect = Rect.fromLTWH(0f, 0f, 50f, 50f)

@LargeTest
class AnimationSynchronizationTest {

    private val handler = Handler(Looper.getMainLooper())

    private var animationRunning = false
    private val recordedAnimatedValues = mutableListOf<Float>()
    private var hasRecomposed = false

    @get:Rule
    val composeTestRule = createComposeRule()
    private val clockTestRule = composeTestRule.clockTestRule

    /**
     * High level test to only verify that [ComposeTestRule.runOnIdleCompose] awaits animations.
     */
    @Test
    fun testRunOnIdleCompose() {
        val animationState = mutableStateOf(AnimationStates.From)
        composeTestRule.setContent { Ui(animationState) }

        composeTestRule.runOnIdleCompose {
            // Kick off the animation
            animationRunning = true
            animationState.value = AnimationStates.To
        }

        // Verify that animation is kicked off
        assertThat(animationRunning).isTrue()
        // Wait until it is finished
        composeTestRule.runOnIdleCompose {
            // Verify it was finished
            assertThat(animationRunning).isFalse()
        }
    }

    /**
     * High level test to only verify that [onIdle] awaits animations.
     */
    @Test
    fun testAnimationIdle_simple() {
        val animationState = mutableStateOf(AnimationStates.From)
        composeTestRule.setContent { Ui(animationState) }

        composeTestRule.runOnIdleCompose {
            // Kick off the animation
            animationRunning = true
            animationState.value = AnimationStates.To
        }

        // Verify that animation is kicked off
        assertThat(animationRunning).isTrue()
        // Wait until it is finished
        onIdle()
        // Verify it was finished
        assertThat(animationRunning).isFalse()
    }

    /**
     * Detailed test to verify if [ComposeIdlingResource.isIdle] reports idleness correctly at
     * key moments during the animation kick-off process.
     */
    @Test
    fun testAnimationIdle_detailed() {
        var wasIdleAfterCommit = false
        var wasIdleAfterRecompose = false
        var wasIdleBeforeKickOff = false
        var wasIdleBeforeCommit = false

        val animationState = mutableStateOf(AnimationStates.From)
        composeTestRule.setContent { Ui(animationState) }

        composeTestRule.runOnIdleCompose {
            // Record idleness after this frame is committed. The mutation we're about to make
            // will trigger a commit of the frame, which is posted at the front of the handler's
            // queue. By posting a message at the front of the queue here, it will be executed
            // right after the frame commit.
            handler.postAtFrontOfQueue {
                wasIdleAfterCommit = ComposeIdlingResource.isIdle()
            }

            // Record idleness after the next recomposition. Since we can't get a signal from the
            // recomposer, keep polling until we detect we have been recomposed.
            hasRecomposed = false
            handler.pollUntil({ hasRecomposed }) {
                wasIdleAfterRecompose = ComposeIdlingResource.isIdle()
            }

            // Record idleness before kickoff of animation
            wasIdleBeforeKickOff = ComposeIdlingResource.isIdle()

            // Kick off the animation
            animationRunning = true
            animationState.value = AnimationStates.To

            // Record idleness after kickoff of animation, but before the frame is committed
            wasIdleBeforeCommit = ComposeIdlingResource.isIdle()
        }

        // Verify that animation is kicked off
        assertThat(animationRunning).isTrue()
        // Wait until it is finished
        onIdle()
        // Verify it was finished
        assertThat(animationRunning).isFalse()

        // Before the animation is kicked off, it is still idle
        assertThat(wasIdleBeforeKickOff).isTrue()
        // After animation is kicked off, but before the frame is committed, it must be busy
        assertThat(wasIdleBeforeCommit).isFalse()
        // After the frame is committed, it must still be busy
        assertThat(wasIdleAfterCommit).isFalse()
        // After recomposition, it must still be busy
        assertThat(wasIdleAfterRecompose).isFalse()
    }

    /**
     * Tests if advancing the clock manually works when the clock is paused, and that idleness is
     * reported correctly when doing that.
     */
    @Test
    @MediumTest
    fun testAnimation_manuallyAdvanceClock_paused() {
        clockTestRule.pauseClock()

        val animationState = mutableStateOf(AnimationStates.From)
        composeTestRule.setContent { Ui(animationState) }

        composeTestRule.runOnIdleCompose {
            recordedAnimatedValues.clear()

            // Kick off the animation
            animationRunning = true
            animationState.value = AnimationStates.To

            // Changes need to trickle down the animation system, so compose should be non-idle
            assertThat(ComposeIdlingResource.isIdle()).isFalse()
        }

        // Await recomposition
        onIdle()

        // Did one recomposition, but no animation frames
        // TODO(b/149754986): Canvas draws twice in our use case. Fix expected values when fixed
        assertThat(recordedAnimatedValues).isEqualTo(listOf(0f, 0f))

        // Animation doesn't actually start until the next frame.
        // Advance by 0ms to force dispatching of a frame time.
        composeTestRule.runOnIdleCompose {
            // Advance clock on main thread so we can assert Compose is not idle afterwards
            clockTestRule.advanceClock(0)
            assertThat(ComposeIdlingResource.isIdle()).isFalse()
        }

        // Await start animation frame
        onIdle()

        // Did start animation frame
        assertThat(recordedAnimatedValues).isEqualTo(listOf(0f, 0f, 0f))

        // Advance first half of the animation (.5 sec)
        composeTestRule.runOnIdleCompose {
            clockTestRule.advanceClock(500)
            assertThat(ComposeIdlingResource.isIdle()).isFalse()
        }

        // Await next animation frame
        onIdle()

        // Did one animation frame
        assertThat(recordedAnimatedValues).isEqualTo(listOf(0f, 0f, 0f, 25f))

        // Advance second half of the animation (.5 sec)
        composeTestRule.runOnIdleCompose {
            clockTestRule.advanceClock(500)
            assertThat(ComposeIdlingResource.isIdle()).isFalse()
        }

        // Await next animation frame
        onIdle()

        // Did last animation frame
        assertThat(recordedAnimatedValues).isEqualTo(listOf(0f, 0f, 0f, 25f, 50f))
    }

    /**
     * Tests if advancing the clock manually works when the clock is resumed, and that idleness
     * is reported correctly when doing that.
     */
    @Test
    @MediumTest
    @Ignore("b/150357516: not yet implemented")
    fun testAnimation_manuallyAdvanceClock_resumed() {
        // TODO(b/150357516): Test advancing the clock while it is resumed
    }

    private fun Handler.pollUntil(condition: () -> Boolean, onDone: () -> Unit) {
        object : Runnable {
            override fun run() {
                if (condition()) {
                    onDone()
                } else {
                    this@pollUntil.post(this)
                }
            }
        }.run()
    }

    @Composable
    private fun Ui(animationState: State<AnimationStates>) {
        val paint = remember { Paint().also { it.color = Color.Cyan } }

        hasRecomposed = true
        Container(modifier = DrawBackground(color = Color.Yellow) + LayoutSize.Fill) {
            hasRecomposed = true
            Transition(
                definition = animationDefinition,
                toState = animationState.value,
                onStateChangeFinished = { animationRunning = false }
            ) { state ->
                hasRecomposed = true
                Canvas(modifier = LayoutSize.Fill) {
                    recordedAnimatedValues.add(state[x])
                    drawRect(animatedRect.translate(state[x], 0f), paint)
                }
            }
        }
    }

    private val x = FloatPropKey()

    private enum class AnimationStates {
        From,
        To
    }

    private val animationDefinition = transitionDefinition {
        state(AnimationStates.From) {
            this[x] = animateFromX
        }
        state(AnimationStates.To) {
            this[x] = animateToX
        }
        transition(AnimationStates.From to AnimationStates.To) {
            x using tween {
                easing = LinearEasing
                duration = nonIdleDuration.toInt()
            }
        }
        transition(AnimationStates.To to AnimationStates.From) {
            x using snap()
        }
    }
}