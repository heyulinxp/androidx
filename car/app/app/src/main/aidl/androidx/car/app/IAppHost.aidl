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

package androidx.car.app;

import androidx.car.app.ISurfaceListener;

/** @hide */
interface IAppHost {
  /** Requests the current template to be invalidated. */
  void invalidate() = 1;

  /** Shows a toast on the car screen. */
  void showToast(CharSequence text, int duration) = 2;

  /**
   * Registers the listener to get callbacks for surface events.
   */
  void setSurfaceListener(@nullable ISurfaceListener listener) = 3;
}
