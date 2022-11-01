/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import static io.camunda.zeebe.engine.util.RecordToWrite.command;
import static io.camunda.zeebe.engine.util.RecordToWrite.event;
import static io.camunda.zeebe.engine.util.RecordToWrite.rejection;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;

import io.camunda.zeebe.engine.api.RecordProcessor;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
public final class StreamProcessorReplayTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord RECORD = Records.processInstance(1);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  public void shouldReplayEvents() {
    // given
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    streamPlatform.startStreamProcessor();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).replay(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldProcessAfterReplay() {
    // given
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    streamPlatform.startStreamProcessor();

    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).replay(any());
    inOrder.verify(recordProcessor, TIMEOUT).process(any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSkipCommands() {
    // given
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    // when
    streamPlatform.startStreamProcessor();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).replay(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSkipRejections() {
    // given
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        rejection().processInstance(ACTIVATE_ELEMENT, RECORD).causedBy(0));

    // when
    streamPlatform.startStreamProcessor();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).init(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRestoreFromLog() {
    // given
    final var eventKeyBeforeSnapshot = Protocol.encodePartitionId(1, 19);

    // on replay the positions and keys should be restored
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event()
            .processInstance(ELEMENT_ACTIVATING, RECORD)
            .key(eventKeyBeforeSnapshot)
            .causedBy(0));

    // when
    // starting the stream processor awaits the opening/replay phase
    final var streamProcessor = streamPlatform.startStreamProcessor();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).replay(any());
    inOrder.verifyNoMoreInteractions();

    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () ->
                Assertions.assertThat(streamProcessor.getLastProcessedPositionAsync().join())
                    .isEqualTo(1L));
    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () ->
                Assertions.assertThat(streamProcessor.getLastWrittenPositionAsync().join())
                    .isEqualTo(2L));
    Assertions.assertThat(Protocol.decodeKeyInPartition(streamPlatform.getCurrentKey()))
        .isEqualTo(19L);
  }

  @Test
  public void shouldRestoreFromSnapshot() throws Exception {
    // given
    final var eventKeyBeforeSnapshot = Protocol.encodePartitionId(1, 19);

    // on replay the positions and keys are restored
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event()
            .processInstance(ELEMENT_ACTIVATING, RECORD)
            .key(eventKeyBeforeSnapshot)
            .causedBy(0));
    // starting the stream processor awaits the opening/replay phase
    streamPlatform.startStreamProcessor();
    // the snapshot will contain the key and the position (in the metadata)
    streamPlatform.snapshot();
    streamPlatform.closeStreamProcessor();
    Mockito.clearInvocations(streamPlatform.getDefaultMockedRecordProcessor());

    // when
    final var streamProcessor = streamPlatform.startStreamProcessor();

    // then
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).init(any());
    inOrder.verify(recordProcessor, never()).replay(any());
    inOrder.verifyNoMoreInteractions();

    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () ->
                Assertions.assertThat(streamProcessor.getLastProcessedPositionAsync().join())
                    .isEqualTo(1L));
    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () ->
                Assertions.assertThat(streamProcessor.getLastWrittenPositionAsync().join())
                    .isEqualTo(2L));
    Assertions.assertThat(Protocol.decodeKeyInPartition(streamPlatform.getCurrentKey()))
        .isEqualTo(19L);
  }

  @Test
  public void shouldNotReplayEventIfAlreadyApplied() throws Exception {
    // given
    final var eventKeyBeforeSnapshot = Protocol.encodePartitionId(1, 19);
    final var eventKeyAfterSnapshot = Protocol.encodePartitionId(1, 21);

    // on replay the positions and keys are restored
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event()
            .processInstance(ELEMENT_ACTIVATING, RECORD)
            .key(eventKeyBeforeSnapshot)
            .causedBy(0));
    // starting the stream processor awaits the opening/replay phase
    streamPlatform.startStreamProcessor();
    // the snapshot will contain the key and the position (in the metadata)
    streamPlatform.snapshot();
    streamPlatform.closeStreamProcessor();
    Mockito.clearInvocations(streamPlatform.getDefaultMockedRecordProcessor());

    // when
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().key(eventKeyAfterSnapshot).processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));
    final var streamProcessor = streamPlatform.startStreamProcessor();

    // then
    final ArgumentCaptor<TypedRecord> recordCaptor = ArgumentCaptor.forClass(TypedRecord.class);
    final RecordProcessor recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final InOrder inOrder = inOrder(recordProcessor);
    inOrder.verify(recordProcessor, TIMEOUT).replay(recordCaptor.capture());
    inOrder.verifyNoMoreInteractions();

    Assertions.assertThat(recordCaptor.getValue().getKey()).isEqualTo(eventKeyAfterSnapshot);

    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () ->
                Assertions.assertThat(streamProcessor.getLastProcessedPositionAsync().join())
                    .isEqualTo(3L));
    Awaitility.await("position has to be set on processing start")
        .untilAsserted(
            () ->
                Assertions.assertThat(streamProcessor.getLastWrittenPositionAsync().join())
                    .isEqualTo(4L));
    Assertions.assertThat(Protocol.decodeKeyInPartition(streamPlatform.getCurrentKey()))
        .isEqualTo(21L);
  }

  @Test
  public void shouldIgnoreKeysFromDifferentPartition() {
    // given
    final var keyOfThisPartition = Protocol.encodePartitionId(1, 19L);
    final var keyOfOtherPartition = Protocol.encodePartitionId(2, 21L);

    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().key(keyOfThisPartition).processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0),
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().key(keyOfOtherPartition).processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(2));

    // when
    streamPlatform.startStreamProcessor();

    // then
    Assertions.assertThat(Protocol.decodeKeyInPartition(streamPlatform.getCurrentKey()))
        .isEqualTo(19L);
  }
}
