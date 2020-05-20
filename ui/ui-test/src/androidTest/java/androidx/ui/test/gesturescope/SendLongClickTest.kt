/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.test.gesturescope

import androidx.test.filters.MediumTest
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.ui.core.gesture.LongPressTimeout
import androidx.ui.core.gesture.longPressGestureFilter
import androidx.ui.layout.Stack
import androidx.ui.layout.fillMaxSize
import androidx.ui.layout.wrapContentSize
import androidx.ui.test.createComposeRule
import androidx.ui.test.doGesture
import androidx.ui.test.findByTag
import androidx.ui.test.runOnIdleCompose
import androidx.ui.test.sendLongClick
import androidx.ui.test.util.ClickableTestBox
import androidx.ui.test.util.PointerInputRecorder
import androidx.ui.test.util.areAlmostEqualTo
import androidx.ui.test.util.assertOnlyLastEventIsUp
import androidx.ui.test.util.assertTimestampsAreIncreasing
import androidx.ui.test.util.recordedDuration
import androidx.ui.unit.PxPosition
import androidx.ui.unit.milliseconds
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.Parameterized

private const val tag = "widget"
private val width = 100.0f
private val height = 100.0f
private val expectedDuration = LongPressTimeout + 100.milliseconds

/**
 * Tests [sendLongClick] without arguments. Verifies that the click is in the middle
 * of the component, that the gesture has a duration of 600 milliseconds and that all input
 * events were on the same location.
 */
@MediumTest
@RunWith(JUnit4::class)
class SendLongClickWithoutArgumentsTest {

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    private val recorder = PointerInputRecorder()
    private val recordedLongClicks = mutableListOf<PxPosition>()
    private val expectedPosition = PxPosition(width / 2, height / 2)

    private fun recordLongPress(position: PxPosition) {
        recordedLongClicks.add(position)
    }

    @Test
    fun testLongClick() {
        // Given some content
        composeTestRule.setContent {
            Stack(Modifier.fillMaxSize().wrapContentSize(Alignment.BottomEnd)) {
                ClickableTestBox(
                    modifier = Modifier.longPressGestureFilter(::recordLongPress).plus(recorder),
                    width = width,
                    height = height,
                    tag = tag
                )
            }
        }

        // When we inject a long click
        findByTag(tag).doGesture { sendLongClick() }

        // Then we record 1 long click
        assertThat(recordedLongClicks).hasSize(1)

        // And all events are at the click location
        runOnIdleCompose {
            recorder.run {
                assertTimestampsAreIncreasing()
                assertOnlyLastEventIsUp()
                events.areAlmostEqualTo(expectedPosition)
                assertThat(recordedDuration).isEqualTo(expectedDuration)
            }
        }
    }
}

/**
 * Tests [sendLongClick] with arguments. Verifies that the click is in the middle
 * of the component, that the gesture has a duration of 600 milliseconds and that all input
 * events were on the same location.
 */
@MediumTest
@RunWith(Parameterized::class)
class SendLongClickWithArgumentsTest(private val config: TestConfig) {
    data class TestConfig(
        val position: PxPosition
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun createTestSet(): List<TestConfig> {
            return mutableListOf<TestConfig>().apply {
                for (x in listOf(1.0f, width / 4)) {
                    for (y in listOf(1.0f, height / 4)) {
                        add(TestConfig(PxPosition(x, y)))
                    }
                }
            }
        }
    }

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    private val recorder = PointerInputRecorder()
    private val recordedLongClicks = mutableListOf<PxPosition>()

    private fun recordLongPress(position: PxPosition) {
        recordedLongClicks.add(position)
    }

    @Test
    fun testLongClick() {
        // Given some content
        composeTestRule.setContent {
            Stack(Modifier.fillMaxSize().wrapContentSize(Alignment.BottomEnd)) {
                ClickableTestBox(
                    modifier = Modifier.longPressGestureFilter(::recordLongPress).plus(recorder),
                    width = width,
                    height = height,
                    tag = tag
                )
            }
        }

        // When we inject a long click
        findByTag(tag).doGesture { sendLongClick(config.position) }

        // Then we record 1 long click
        assertThat(recordedLongClicks).hasSize(1)

        // And all events are at the click location
        runOnIdleCompose {
            recorder.run {
                assertTimestampsAreIncreasing()
                assertOnlyLastEventIsUp()
                events.areAlmostEqualTo(config.position)
                assertThat(recordedDuration).isEqualTo(expectedDuration)
            }
        }
    }
}