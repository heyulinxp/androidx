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

package androidx.ui.core.selection

import androidx.compose.frames.commit
import androidx.compose.frames.open
import androidx.test.filters.SmallTest
import androidx.ui.core.LayoutCoordinates
import androidx.ui.text.style.TextDirection
import androidx.ui.unit.PxPosition
import androidx.ui.unit.px
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class SelectionManagerLongPressDragTest {
    private val selectionRegistrar = SelectionRegistrarImpl()
    private val selectable = mock<Selectable>()
    private val selectionManager = SelectionManager(selectionRegistrar)

    private val startSelectable = mock<Selectable>()
    private val endSelectable = mock<Selectable>()

    private val fakeInitialSelection: Selection = Selection(
        start = Selection.AnchorInfo(
            direction = TextDirection.Ltr,
            offset = 0,
            selectable = startSelectable
        ),
        end = Selection.AnchorInfo(
            direction = TextDirection.Ltr,
            offset = 5,
            selectable = endSelectable
        )
    )

    private val fakeResultSelection: Selection = Selection(
        start = Selection.AnchorInfo(
            direction = TextDirection.Ltr,
            offset = 5,
            selectable = endSelectable
        ),
        end = Selection.AnchorInfo(
            direction = TextDirection.Ltr,
            offset = 0,
            selectable = startSelectable
        )
    )

    private var selection: Selection? = null
    private val lambda: (Selection?) -> Unit = { selection = it }
    private val spyLambda = spy(lambda)

    @Before
    fun setup() {
        open(false) // we open a Frame so state reads are allowed
        val containerLayoutCoordinates = mock<LayoutCoordinates> {
            on { isAttached } doReturn true
        }
        selectionRegistrar.subscribe(selectable)

        whenever(
            selectable.getSelection(
                startPosition = any(),
                endPosition = any(),
                containerLayoutCoordinates = any(),
                longPress = any(),
                previousSelection = anyOrNull(),
                isStartHandle = any()
            )
        ).thenReturn(fakeResultSelection)

        selectionManager.containerLayoutCoordinates = containerLayoutCoordinates
        selectionManager.onSelectionChange = spyLambda
        selectionManager.selection = selection
        selectionManager.hapticFeedBack = mock()
    }

    @After
    fun after() {
        commit() // we close the Frame
    }

    @Test
    fun longPressDragObserver_onLongPress_calls_getSelection_change_selection() {
        val position = PxPosition(100.px, 100.px)

        selectionManager.longPressDragObserver.onLongPress(position)

        verify(selectable, times(1))
            .getSelection(
                startPosition = position,
                endPosition = position,
                containerLayoutCoordinates = selectionManager.requireContainerCoordinates(),
                longPress = true,
                previousSelection = null,
                isStartHandle = true
            )
        assertThat(selection).isEqualTo(fakeResultSelection)
        verify(spyLambda, times(1)).invoke(fakeResultSelection)
    }

    @Test
    fun longPressDragObserver_onDragStart_reset_dragTotalDistance() {
        // Setup. Make sure selectionManager.dragTotalDistance is not 0.
        val dragDistance1 = PxPosition(15.px, 10.px)
        val beginPosition1 = PxPosition(30.px, 20.px)
        val dragDistance2 = PxPosition(100.px, 300.px)
        val beginPosition2 = PxPosition(300.px, 200.px)
        selectionManager.longPressDragObserver.onLongPress(beginPosition1)
        selectionManager.longPressDragObserver.onDragStart()
        selectionManager.longPressDragObserver.onDrag(dragDistance1)
        // Setup. Cancel selection and reselect.
        selectionManager.onRelease()
        // Start the new selection
        selectionManager.longPressDragObserver.onLongPress(beginPosition2)
        selectionManager.selection = fakeInitialSelection
        selection = fakeInitialSelection

        // Act. Reset selectionManager.dragTotalDistance to zero.
        selectionManager.longPressDragObserver.onDragStart()
        selectionManager.longPressDragObserver.onDrag(dragDistance2)

        // Verify.
        verify(selectable, times(1))
            .getSelection(
                startPosition = beginPosition2,
                endPosition = beginPosition2 + dragDistance2,
                containerLayoutCoordinates = selectionManager.requireContainerCoordinates(),
                longPress = true,
                previousSelection = fakeInitialSelection
            )
        assertThat(selection).isEqualTo(fakeResultSelection)
        verify(spyLambda, times(3)).invoke(fakeResultSelection)
    }

    @Test
    fun longPressDragObserver_onDrag_calls_getSelection_change_selection() {
        val dragDistance = PxPosition(15.px, 10.px)
        val beginPosition = PxPosition(30.px, 20.px)
        selectionManager.longPressDragObserver.onLongPress(beginPosition)
        selectionManager.selection = fakeInitialSelection
        selection = fakeInitialSelection
        selectionManager.longPressDragObserver.onDragStart()

        val result = selectionManager.longPressDragObserver.onDrag(dragDistance)

        assertThat(result).isEqualTo(dragDistance)
        verify(selectable, times(1))
            .getSelection(
                startPosition = beginPosition,
                endPosition = beginPosition + dragDistance,
                containerLayoutCoordinates = selectionManager.requireContainerCoordinates(),
                longPress = true,
                previousSelection = fakeInitialSelection
            )
        assertThat(selection).isEqualTo(fakeResultSelection)
        verify(spyLambda, times(2)).invoke(fakeResultSelection)
    }

    @Test
    fun longPressDragObserver_onDrag_directly_not_call_getSelection_not_change_selection() {
        val dragDistance = PxPosition(15.px, 10.px)
        val beginPosition = PxPosition(30.px, 20.px)

        selection = fakeInitialSelection
        val result = selectionManager.longPressDragObserver.onDrag(dragDistance)

        assertThat(result).isEqualTo(PxPosition.Origin)
        verify(selectable, times(0))
            .getSelection(
                startPosition = beginPosition,
                endPosition = beginPosition + dragDistance,
                containerLayoutCoordinates = selectionManager.requireContainerCoordinates(),
                longPress = true
            )
        assertThat(selection).isEqualTo(fakeInitialSelection)
        verify(spyLambda, times(0)).invoke(fakeResultSelection)
    }
}