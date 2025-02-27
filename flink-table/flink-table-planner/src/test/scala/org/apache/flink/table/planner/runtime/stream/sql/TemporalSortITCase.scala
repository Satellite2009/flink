/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.table.planner.runtime.stream.sql

import org.apache.flink.api.scala._
import org.apache.flink.table.api._
import org.apache.flink.table.api.bridge.scala._
import org.apache.flink.table.planner.factories.TestValuesTableFactory
import org.apache.flink.table.planner.runtime.utils._
import org.apache.flink.table.planner.runtime.utils.BatchTestBase.row
import org.apache.flink.table.planner.runtime.utils.StreamingWithStateTestBase.StateBackendMode
import org.apache.flink.table.planner.runtime.utils.TimeTestUtil.TimestampAndWatermarkWithOffset
import org.apache.flink.testutils.junit.extensions.parameterized.ParameterizedTestExtension
import org.apache.flink.types.Row

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith

import java.time.{Instant, LocalDateTime}

import scala.collection.Seq

@ExtendWith(Array(classOf[ParameterizedTestExtension]))
class TemporalSortITCase(mode: StateBackendMode) extends StreamingWithStateTestBase(mode) {

  @TestTemplate
  def testOnlyEventTimeOrderBy(): Unit = {
    val data = List(
      (3L, 2L, "Hello world", 3),
      (2L, 2L, "Hello", 2),
      (6L, 3L, "Luke Skywalker", 6),
      (5L, 3L, "I am fine.", 5),
      (7L, 4L, "Comment#1", 7),
      (9L, 4L, "Comment#3", 9),
      (10L, 4L, "Comment#4", 10),
      (8L, 4L, "Comment#2", 8),
      (1L, 1L, "Hi", 2),
      (1L, 1L, "Hi", 1),
      (4L, 3L, "Helloworld, how are you?", 4)
    )

    val t = failingDataSource(data)
      .assignTimestampsAndWatermarks(
        new TimestampAndWatermarkWithOffset[(Long, Long, String, Int)](10L))
      .toTable(tEnv, 'rowtime.rowtime, 'key, 'str, 'int)
    tEnv.createTemporaryView("T", t)

    val sqlQuery = "SELECT key, str, `int` FROM T ORDER BY rowtime"

    val sink = new TestingRetractSink
    val results = tEnv.sqlQuery(sqlQuery).toRetractStream[Row]
    results.addSink(sink).setParallelism(1)
    env.execute()

    val expected = Seq(
      "1,Hi,2",
      "1,Hi,1",
      "2,Hello,2",
      "2,Hello world,3",
      "3,Helloworld, how are you?,4",
      "3,I am fine.,5",
      "3,Luke Skywalker,6",
      "4,Comment#1,7",
      "4,Comment#2,8",
      "4,Comment#3,9",
      "4,Comment#4,10"
    )

    assertThat(sink.getRetractResults).isEqualTo(expected)
  }

  @TestTemplate
  def testEventTimeOrderByWithParallelInput(): Unit = {
    val data = List(
      (3L, 2L, "Hello world", 3),
      (2L, 2L, "Hello", 2),
      (6L, 3L, "Luke Skywalker", 6),
      (5L, 3L, "I am fine.", 5),
      (7L, 4L, "Comment#1", 7),
      (9L, 4L, "Comment#3", 9),
      (10L, 4L, "Comment#4", 10),
      (8L, 4L, "Comment#2", 8),
      (1L, 1L, "Hi", 1),
      (4L, 3L, "Helloworld, how are you?", 4)
    )

    val t = failingDataSource(data)
      .assignTimestampsAndWatermarks(
        new TimestampAndWatermarkWithOffset[(Long, Long, String, Int)](10L))
      .setParallelism(env.getParallelism)
      .toTable(tEnv, 'rowtime.rowtime, 'key, 'str, 'int)
    tEnv.createTemporaryView("T", t)

    val sqlQuery = "SELECT key, str, `int` FROM T ORDER BY rowtime"

    val sink = new TestingRetractSink
    val results = tEnv.sqlQuery(sqlQuery).toRetractStream[Row]
    results.addSink(sink).setParallelism(1)
    env.execute()

    val expected = Seq(
      "1,Hi,1",
      "2,Hello,2",
      "2,Hello world,3",
      "3,Helloworld, how are you?,4",
      "3,I am fine.,5",
      "3,Luke Skywalker,6",
      "4,Comment#1,7",
      "4,Comment#2,8",
      "4,Comment#3,9",
      "4,Comment#4,10"
    )

    assertThat(sink.getRetractResults).isEqualTo(expected)
  }

  @TestTemplate
  def testTimestampEventTimeAndOtherFieldOrderBy(): Unit = {
    val rows = Seq(
      row(LocalDateTime.parse("1970-01-01T00:00:03"), 2L, "Hello world", 3),
      row(LocalDateTime.parse("1970-01-01T00:00:02"), 2L, "Hello", 2),
      row(LocalDateTime.parse("1970-01-01T00:00:06"), 3L, "Luke Skywalker", 6),
      row(LocalDateTime.parse("1970-01-01T00:00:05"), 3L, "I am fine.", 5),
      row(LocalDateTime.parse("1970-01-01T00:00:07"), 4L, "Comment#1", 7),
      row(LocalDateTime.parse("1970-01-01T00:00:09"), 4L, "Comment#3", 9),
      row(LocalDateTime.parse("1970-01-01T00:00:10"), 4L, "Comment#4", 10),
      row(LocalDateTime.parse("1970-01-01T00:00:08"), 4L, "Comment#2", 8),
      row(LocalDateTime.parse("1970-01-01T00:00:01"), 1L, "Hi", 2),
      row(LocalDateTime.parse("1970-01-01T00:00:01"), 1L, "Hi", 1),
      row(LocalDateTime.parse("1970-01-01T00:00:04"), 3L, "Helloworld, how are you?", 4)
    )

    val tableId = TestValuesTableFactory.registerData(rows)
    tEnv.executeSql(s"""
                       |CREATE TABLE T (
                       |  rowtime TIMESTAMP(3),
                       |  key BIGINT,
                       |  str STRING,
                       |  `int` INT,
                       |  WATERMARK FOR rowtime AS rowtime - interval '10' SECOND
                       |) WITH (
                       |  'connector' = 'values',
                       |  'data-id' = '$tableId',
                       |  'bounded' = 'true'
                       |)
                       |""".stripMargin)

    val sqlQuery = "SELECT key, str, `int` FROM T ORDER BY rowtime, `int`"

    val sink = new TestingRetractSink
    val results = tEnv.sqlQuery(sqlQuery).toRetractStream[Row]
    results.addSink(sink).setParallelism(1)
    env.execute()

    val expected = Seq(
      "1,Hi,1",
      "1,Hi,2",
      "2,Hello,2",
      "2,Hello world,3",
      "3,Helloworld, how are you?,4",
      "3,I am fine.,5",
      "3,Luke Skywalker,6",
      "4,Comment#1,7",
      "4,Comment#2,8",
      "4,Comment#3,9",
      "4,Comment#4,10"
    )

    assertThat(sink.getRetractResults).isEqualTo(expected)
  }

  @TestTemplate
  def testTimestampLtzEventTimeAndOtherFieldOrderBy(): Unit = {
    val rows = Seq(
      row(Instant.ofEpochSecond(3L), 2L, "Hello world", 3),
      row(Instant.ofEpochSecond(2L), 2L, "Hello", 2),
      row(Instant.ofEpochSecond(6L), 3L, "Luke Skywalker", 6),
      row(Instant.ofEpochSecond(5L), 3L, "I am fine.", 5),
      row(Instant.ofEpochSecond(7L), 4L, "Comment#1", 7),
      row(Instant.ofEpochSecond(9L), 4L, "Comment#3", 9),
      row(Instant.ofEpochSecond(10L), 4L, "Comment#4", 10),
      row(Instant.ofEpochSecond(8L), 4L, "Comment#2", 8),
      row(Instant.ofEpochSecond(1L), 1L, "Hi", 2),
      row(Instant.ofEpochSecond(1L), 1L, "Hi", 1),
      row(Instant.ofEpochSecond(4L), 3L, "Helloworld, how are you?", 4)
    )

    val tableId = TestValuesTableFactory.registerData(rows)
    tEnv.executeSql(s"""
                       |CREATE TABLE T (
                       |  rowtime TIMESTAMP_LTZ(3),
                       |  key BIGINT,
                       |  str STRING,
                       |  `int` INT,
                       |  WATERMARK FOR rowtime AS rowtime - interval '10' SECOND
                       |) WITH (
                       |  'connector' = 'values',
                       |  'data-id' = '$tableId',
                       |  'bounded' = 'true'
                       |)
                       |""".stripMargin)

    val sqlQuery = "SELECT key, str, `int` FROM T ORDER BY rowtime, `int`"

    val sink = new TestingRetractSink
    val results = tEnv.sqlQuery(sqlQuery).toRetractStream[Row]
    results.addSink(sink).setParallelism(1)
    env.execute()

    val expected = Seq(
      "1,Hi,1",
      "1,Hi,2",
      "2,Hello,2",
      "2,Hello world,3",
      "3,Helloworld, how are you?,4",
      "3,I am fine.,5",
      "3,Luke Skywalker,6",
      "4,Comment#1,7",
      "4,Comment#2,8",
      "4,Comment#3,9",
      "4,Comment#4,10"
    )

    assertThat(sink.getRetractResults).isEqualTo(expected)
  }

  @TestTemplate
  def testProcTimeOrderBy(): Unit = {
    val t = failingDataSource(TestData.tupleData3)
      .toTable(tEnv, 'a, 'b, 'c, 'proctime.proctime)
    tEnv.createTemporaryView("T", t)

    val sql = "SELECT a, b, c FROM T ORDER BY proctime"

    val sink = new TestingRetractSink
    val results = tEnv.sqlQuery(sql).toRetractStream[Row]
    results.addSink(sink).setParallelism(1)
    env.execute()

    val expected = Seq(
      "1,1,Hi",
      "2,2,Hello",
      "3,2,Hello world",
      "4,3,Hello world, how are you?",
      "5,3,I am fine.",
      "6,3,Luke Skywalker",
      "7,4,Comment#1",
      "8,4,Comment#2",
      "9,4,Comment#3",
      "10,4,Comment#4",
      "11,5,Comment#5",
      "12,5,Comment#6",
      "13,5,Comment#7",
      "14,5,Comment#8",
      "15,5,Comment#9",
      "16,6,Comment#10",
      "17,6,Comment#11",
      "18,6,Comment#12",
      "19,6,Comment#13",
      "20,6,Comment#14",
      "21,6,Comment#15"
    )

    assertThat(sink.getRetractResults).isEqualTo(expected)
  }

}
