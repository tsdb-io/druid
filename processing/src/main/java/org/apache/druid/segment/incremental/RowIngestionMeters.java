/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.segment.incremental;

import org.apache.druid.data.input.InputStats;
import org.apache.druid.guice.annotations.ExtensionPoint;

import java.util.Map;

/**
 * A collection of meters for row ingestion stats, with support for moving average calculations.
 * This can eventually replace SegmentGenerationMetrics, but moving averages for other stats collected by
 * SegmentGenerationMetrics are not currently supported, so we continue to use SegmentGenerationMetrics alongside
 * RowIngestionMeters to avoid unnecessary overhead from maintaining these moving averages.
 */
@ExtensionPoint
public interface RowIngestionMeters extends InputStats
{
  String BUILD_SEGMENTS = "buildSegments";
  String DETERMINE_PARTITIONS = "determinePartitions";

  String PROCESSED = "processed";
  String PROCESSED_WITH_ERROR = "processedWithError";
  String UNPARSEABLE = "unparseable";
  String THROWN_AWAY = "thrownAway";

  /**
   * Number of bytes read by an ingestion task.
   *
   * Note: processedBytes is a misleading name; this generally measures size when data is initially read or fetched,
   * not when it is processed by the ingest task. It's measuring a stage somewhat earlier in the pipeline. In other
   * words, "processed" and "processedBytes" do not use the same definition of "process". A better name might be
   * "bytesRead" or "inputBytes", although if we change it, we must consider compatibility with existing readers.
   */
  String PROCESSED_BYTES = "processedBytes";

  long getProcessed();
  void incrementProcessed();

  @Override
  default void incrementProcessedBytes(long incrementByValue)
  {

  }

  @Override
  default long getProcessedBytes()
  {
    return 0;
  }

  long getProcessedWithError();
  void incrementProcessedWithError();

  long getUnparseable();
  void incrementUnparseable();

  long getThrownAway();
  void incrementThrownAway();

  RowIngestionMetersTotals getTotals();

  Map<String, Object> getMovingAverages();
}
