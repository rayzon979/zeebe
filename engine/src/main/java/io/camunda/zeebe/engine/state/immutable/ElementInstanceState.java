/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import java.util.List;
import java.util.function.BiFunction;
import org.agrona.DirectBuffer;

public interface ElementInstanceState {

  ElementInstance getInstance(long key);

  List<ElementInstance> getChildren(long parentKey);

  /**
   * Applies the provided visitor to each child element of the given parent. The visitor can
   * indicate via the return value, whether the iteration should continue or not. This means if the
   * visitor returns false the iteration will stop.
   *
   * <p>The given {@code startAtKey} indicates where the iteration should start. If the key exists,
   * the first key-value-pair will contain the equal key as {@code startAtKey}. If the key doesn't
   * exist it will start after.
   *
   * @param parentKey the key of the parent element instance
   * @param startAtKey the element instance key of child the iteration should start at
   * @param visitor the visitor which is applied for each child
   */
  void forEachChild(
      long parentKey, long startAtKey, BiFunction<Long, ElementInstance, Boolean> visitor);

  AwaitProcessInstanceResultMetadata getAwaitResultRequestMetadata(long processInstanceKey);

  /**
   * Returns the number of the taken sequence flows that are connected to the given parallel
   * (joining) gateway. Each sequence flow counts only as one, even if it is taken multiple times.
   *
   * <p>The number helps to determine if a parallel gateway can be activated or not.
   *
   * @param flowScopeKey the key of the flow scope that contains the gateway
   * @param gatewayElementId the element id of the gateway
   * @return the number of taken sequence flows of the given gateway
   */
  int getNumberOfTakenSequenceFlows(final long flowScopeKey, final DirectBuffer gatewayElementId);

  /**
   * Returns a list of process instance keys that belong to a specific process definition.
   *
   * <p>Caution: This will also return the keys of banned process instances!
   *
   * @param processDefinitionKey the key of the process definition
   * @return a list of process instance keys
   */
  List<Long> getProcessInstanceKeysByDefinitionKey(final long processDefinitionKey);

  /**
   * Verifies if there are active process instances for a given process definition
   *
   * @param processDefinitionKey the key of the process definition
   * @return a boolean indicating if there are running instances
   */
  boolean hasActiveProcessInstances(long processDefinitionKey);
}
