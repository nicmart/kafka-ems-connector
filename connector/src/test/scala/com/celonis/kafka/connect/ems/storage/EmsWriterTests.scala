/*
 * Copyright 2017-2022 Celonis Ltd
 */
package com.celonis.kafka.connect.ems.storage
import com.celonis.kafka.connect.ems.model._
import com.celonis.kafka.connect.ems.storage.formats.FormatWriter
import org.apache.avro.generic.GenericRecord
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import java.io.File
import scala.concurrent.duration._

class EmsWriterTests extends AnyFunSuite with Matchers with MockitoSugar with SampleData {

  test("writes a records and updates the state") {
    val formatWriter = mock[FormatWriter]
    when(formatWriter.rolloverFileOnSchemaChange()).thenReturn(true)
    val startingRecords = 3L
    val startingSize    = 1000000L
    val emsWriter = new EmsWriter(
      "sinkA",
      DefaultCommitPolicy(10000, 1000.minutes.toMillis, 10000),
      formatWriter,
      WriterState(
        TopicPartition(new Topic("A"), new Partition(0)),
        new Offset(-1),
        None,
        startingRecords,
        startingRecords,
        System.currentTimeMillis(),
        simpleSchemaV1,
        new File("abc"),
      ),
    )

    val struct  = buildSimpleStruct()
    val record1 = Record(struct, RecordMetadata(TopicPartition(new Topic("a"), new Partition(0)), new Offset(10)))

    val record2 = Record(struct, RecordMetadata(TopicPartition(new Topic("a"), new Partition(0)), new Offset(11)))

    val firstSize = startingSize + 1002L
    when(formatWriter.size).thenReturn(firstSize)
    emsWriter.write(record1)
    var currentState = emsWriter.state
    currentState.records shouldBe startingRecords + 1
    currentState.fileSize shouldBe firstSize

    val secondSize = firstSize + 1002L
    when(formatWriter.size).thenReturn(secondSize)
    emsWriter.write(record2)
    currentState = emsWriter.state
    currentState.records shouldBe startingRecords + 1 + 1
    currentState.fileSize shouldBe secondSize
  }

  test("failing to write a record does not change the state") {
    val formatWriter = mock[FormatWriter]
    when(formatWriter.rolloverFileOnSchemaChange()).thenReturn(true)

    val expected = WriterState(
      TopicPartition(new Topic("A"), new Partition(0)),
      new Offset(-1),
      None,
      10,
      1,
      System.currentTimeMillis(),
      simpleSchemaV1,
      new File("abc"),
    )
    val emsWriter =
      new EmsWriter("sinkA", DefaultCommitPolicy(10000, 1000.minutes.toMillis, 10000), formatWriter, expected)

    val struct = buildSimpleStruct()
    val record = Record(struct, RecordMetadata(TopicPartition(new Topic("a"), new Partition(0)), new Offset(10)))

    val ex = new RuntimeException("throwing")
    when(formatWriter.write(any[GenericRecord])).thenThrow(ex)
    val actualExceptions = the[RuntimeException] thrownBy emsWriter.write(record)
    actualExceptions shouldBe ex
    emsWriter.state shouldBe expected
  }

  test("writes only records with the offset greater than the last offset one") {
    val formatWriter = mock[FormatWriter]
    when(formatWriter.rolloverFileOnSchemaChange()).thenReturn(true)
    val startingRecords = 3L
    val startingSize    = 1000000L
    val emsWriter = new EmsWriter(
      "sinkA",
      DefaultCommitPolicy(10000, 1000.minutes.toMillis, 10000),
      formatWriter,
      WriterState(
        TopicPartition(new Topic("A"), new Partition(0)),
        new Offset(9),
        None,
        startingRecords,
        startingRecords,
        System.currentTimeMillis(),
        simpleSchemaV1,
        new File("abc"),
      ),
    )

    val struct  = buildSimpleStruct()
    val record1 = Record(struct, RecordMetadata(TopicPartition(new Topic("a"), new Partition(0)), new Offset(10)))

    val firstSize = startingSize + 1002L
    when(formatWriter.size).thenReturn(firstSize)
    emsWriter.write(record1)
    var currentState = emsWriter.state
    currentState.records shouldBe startingRecords + 1
    currentState.fileSize shouldBe firstSize

    val secondSize = firstSize + 1002L
    when(formatWriter.size).thenReturn(secondSize)
    emsWriter.write(record1)
    currentState = emsWriter.state
    currentState.records shouldBe startingRecords + 1
    currentState.fileSize shouldBe firstSize

    val record2 = Record(struct, RecordMetadata(TopicPartition(new Topic("a"), new Partition(0)), new Offset(9)))

    emsWriter.write(record2)
    currentState = emsWriter.state
    currentState.records shouldBe startingRecords + 1
    currentState.fileSize shouldBe firstSize
  }

  test("shouldRollover returns true when schema changes and format writer sets rollover on schema change to true") {
    val formatWriter = mock[FormatWriter]
    when(formatWriter.rolloverFileOnSchemaChange()).thenReturn(true)
    val startingRecords = 3L
    val emsWriter = new EmsWriter(
      "sinkA",
      DefaultCommitPolicy(10000, 1000.minutes.toMillis, 10000),
      formatWriter,
      WriterState(
        TopicPartition(new Topic("A"), new Partition(0)),
        new Offset(-1),
        None,
        startingRecords,
        startingRecords,
        System.currentTimeMillis(),
        simpleSchemaV1,
        new File("abc"),
      ),
    )

    emsWriter.shouldRollover(simpleSchemaV2) shouldBe true
  }

  test(
    "shouldRollover returns false when schema remains unchanged and format writer sets rollover on schema change to true",
  ) {
    val formatWriter = mock[FormatWriter]
    when(formatWriter.rolloverFileOnSchemaChange()).thenReturn(true)
    val startingRecords = 3L
    val emsWriter = new EmsWriter(
      "sinkA",
      DefaultCommitPolicy(10000, 1000.minutes.toMillis, 10000),
      formatWriter,
      WriterState(
        TopicPartition(new Topic("A"), new Partition(0)),
        new Offset(-1),
        None,
        startingRecords,
        startingRecords,
        System.currentTimeMillis(),
        simpleSchemaV1,
        new File("abc"),
      ),
    )

    emsWriter.shouldRollover(simpleSchemaV1) shouldBe false
  }

  test("shouldRollover returns false when schema changes and format writer sets rollover on schema change to false") {
    val formatWriter = mock[FormatWriter]
    when(formatWriter.rolloverFileOnSchemaChange()).thenReturn(false)
    val startingRecords = 3L
    val emsWriter = new EmsWriter(
      "sinkA",
      DefaultCommitPolicy(10000, 1000.minutes.toMillis, 10000),
      formatWriter,
      WriterState(
        TopicPartition(new Topic("A"), new Partition(0)),
        new Offset(-1),
        None,
        startingRecords,
        startingRecords,
        System.currentTimeMillis(),
        simpleSchemaV1,
        new File("abc"),
      ),
    )

    emsWriter.shouldRollover(simpleSchemaV2) shouldBe false
  }

  test("shouldFlush returns true once commit policy is matched") {
    val formatWriter = mock[FormatWriter]
    when(formatWriter.rolloverFileOnSchemaChange()).thenReturn(false)
    val startingRecords = 0L
    val emsWriter = new EmsWriter(
      "sinkA",
      DefaultCommitPolicy(10000, 1000.minutes.toMillis, 1),
      formatWriter,
      WriterState(
        TopicPartition(new Topic("A"), new Partition(0)),
        new Offset(-1),
        None,
        startingRecords,
        startingRecords,
        System.currentTimeMillis(),
        simpleSchemaV1,
        new File("abc"),
      ),
    )

    val struct  = buildSimpleStruct()
    val record1 = Record(struct, RecordMetadata(TopicPartition(new Topic("a"), new Partition(0)), new Offset(10)))

    emsWriter.write(record1)

    emsWriter.shouldFlush shouldBe true
  }

  test("shouldFlush returns false when commit policy remains unmatched") {
    val formatWriter = mock[FormatWriter]
    when(formatWriter.rolloverFileOnSchemaChange()).thenReturn(false)
    val startingRecords = 0L
    val emsWriter = new EmsWriter(
      "sinkA",
      DefaultCommitPolicy(10000, 1000.minutes.toMillis, 1),
      formatWriter,
      WriterState(
        TopicPartition(new Topic("A"), new Partition(0)),
        new Offset(-1),
        None,
        startingRecords,
        startingRecords,
        System.currentTimeMillis(),
        simpleSchemaV1,
        new File("abc"),
      ),
    )

    emsWriter.shouldFlush shouldBe false
  }

}
