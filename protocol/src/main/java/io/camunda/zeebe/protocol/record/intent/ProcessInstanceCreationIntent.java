/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol.record.intent;

public enum ProcessInstanceCreationIntent implements Intent, ProcessInstanceRelatedIntent {
  CREATE(0, false),
  CREATED(1, true),
  CREATE_WITH_AWAITING_RESULT(2, false);

  private final short value;
  private final boolean shouldBanInstance;

  ProcessInstanceCreationIntent(final int value, final boolean shouldBanInstance) {
    this((short) value, shouldBanInstance);
  }

  ProcessInstanceCreationIntent(final short value, final boolean shouldBanInstance) {
    this.value = value;
    this.shouldBanInstance = shouldBanInstance;
  }

  @Override
  public short value() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return CREATE_WITH_AWAITING_RESULT;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }
}
