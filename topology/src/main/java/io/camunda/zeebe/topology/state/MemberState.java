/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.state;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public record MemberState(long version, State state, Map<Integer, PartitionState> partitions) {
  public static MemberState initializeAsActive(
      final Map<Integer, PartitionState> initialPartitions) {
    return new MemberState(0, State.ACTIVE, Map.copyOf(initialPartitions));
  }

  static MemberState uninitialized() {
    return new MemberState(0, State.UNINITIALIZED, Map.of());
  }

  MemberState toJoining() {
    if (state == State.LEAVING) {
      throw new IllegalStateException(
          String.format("Cannot transition to JOINING when current state is %s", state));
    }
    return update(State.JOINING, partitions);
  }

  MemberState toActive() {
    if (state == State.LEFT || state == State.LEAVING) {
      throw new IllegalStateException(
          String.format("Cannot transition to ACTIVE when current state is %s", state));
    }
    return update(State.ACTIVE, partitions);
  }

  public MemberState toLeaving() {
    if (state == State.LEFT) {
      throw new IllegalStateException(
          String.format("Cannot transition to LEAVING when current state is %s", state));
    }
    return update(State.LEAVING, partitions);
  }

  public MemberState toLeft() {
    return update(State.LEFT, partitions);
  }

  MemberState addPartition(final int partitionId, final PartitionState partitionState) {
    if (partitions.containsKey(partitionId)) {
      throw new IllegalStateException(
          String.format(
              "Expected add a new partition, but partition %d already exists with state %s",
              partitionId, partitions.get(partitionId)));
    }

    return internalUpdatePartition(partitionId, partitionState);
  }

  MemberState updatePartition(
      final int partitionId, final UnaryOperator<PartitionState> partitionStateUpdater) {
    if (!partitions.containsKey(partitionId)) {
      throw new IllegalStateException(
          String.format(
              "Expected to update partition %d, but partition does not exist", partitionId));
    }

    final var updatedPartitionState = partitionStateUpdater.apply(partitions.get(partitionId));
    return internalUpdatePartition(partitionId, updatedPartitionState);
  }

  MemberState removePartition(final int partitionId) {
    final var mutableMap = new HashMap<>(partitions);
    mutableMap.remove(partitionId);

    final var updatedPartitions =
        ImmutableMap.<Integer, PartitionState>builder().putAll(mutableMap).build();
    return update(state, updatedPartitions);
  }

  /**
   * Returns a new MemberState after merging this and other. This doesn't overwrite this or other.
   *
   * @param other MemberState to merge
   * @return merged MemberState
   */
  MemberState merge(final MemberState other) {
    if (other == null) {
      return this;
    }

    if (version == other.version && !equals(other)) {
      throw new IllegalStateException(
          String.format(
              "Expected to find same MemberState at same version, but found %s and %s",
              this, other));
    }
    // Choose the one with the highest version. MemberState is always updated by a member by itself.
    // It is guaranteed that the highest version number is the latest state.
    if (version >= other.version) {
      return this;
    } else {
      return other;
    }
  }

  private MemberState internalUpdatePartition(
      final int partitionId, final PartitionState partitionState) {
    final var updatedPartitions =
        ImmutableMap.<Integer, PartitionState>builder()
            .putAll(partitions)
            .put(partitionId, partitionState)
            .buildKeepingLast(); // choose last one if there are duplicate keys
    return update(state, updatedPartitions);
  }

  private MemberState update(final State state, final Map<Integer, PartitionState> partitions) {
    return new MemberState(version + 1, state, partitions);
  }

  public enum State {
    UNINITIALIZED,
    JOINING,
    ACTIVE,
    LEAVING,
    LEFT
  }
}
