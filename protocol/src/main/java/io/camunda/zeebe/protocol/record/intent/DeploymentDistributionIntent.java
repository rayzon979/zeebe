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

/**
 * DeploymentDistributionIntent is deprecated as of 8.3.0. A generalised way of distributing
 * commands has been introduced in this version. The DeploymentCreateProcessor is now using this new
 * way. This intent only remains to stay backwards compatible.
 */
@Deprecated
public enum DeploymentDistributionIntent implements Intent {
  DISTRIBUTING((short) 0),
  COMPLETE((short) 1),
  COMPLETED((short) 2);

  private final short value;

  DeploymentDistributionIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return DISTRIBUTING;
      case 1:
        return COMPLETE;
      case 2:
        return COMPLETED;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }
}
