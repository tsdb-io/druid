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

package org.apache.druid.frame.testutil;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import org.apache.druid.frame.Frame;
import org.apache.druid.frame.FrameType;
import org.apache.druid.frame.allocation.HeapMemoryAllocator;
import org.apache.druid.frame.channel.ByteTracker;
import org.apache.druid.frame.channel.FrameChannelSequence;
import org.apache.druid.frame.channel.ReadableFrameChannel;
import org.apache.druid.frame.file.FrameFileWriter;
import org.apache.druid.frame.read.FrameReader;
import org.apache.druid.frame.segment.FrameSegment;
import org.apache.druid.frame.segment.FrameStorageAdapter;
import org.apache.druid.frame.util.SettableLongVirtualColumn;
import org.apache.druid.java.util.common.Intervals;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.java.util.common.guava.Sequences;
import org.apache.druid.query.OrderBy;
import org.apache.druid.query.dimension.DefaultDimensionSpec;
import org.apache.druid.segment.ColumnProcessors;
import org.apache.druid.segment.ColumnSelectorFactory;
import org.apache.druid.segment.ColumnValueSelector;
import org.apache.druid.segment.Cursor;
import org.apache.druid.segment.CursorBuildSpec;
import org.apache.druid.segment.CursorHolder;
import org.apache.druid.segment.DimensionSelector;
import org.apache.druid.segment.StorageAdapter;
import org.apache.druid.segment.VirtualColumns;
import org.apache.druid.segment.column.ColumnType;
import org.apache.druid.segment.column.RowSignature;
import org.apache.druid.segment.column.ValueType;
import org.apache.druid.segment.data.IndexedInts;
import org.apache.druid.segment.vector.VectorColumnSelectorFactory;
import org.apache.druid.segment.vector.VectorCursor;
import org.apache.druid.timeline.SegmentId;
import org.junit.Assert;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FrameTestUtil
{
  public static final String ROW_NUMBER_COLUMN = "__row_number";

  private FrameTestUtil()
  {
    // No instantiation.
  }

  public static File writeFrameFile(final Sequence<Frame> frames, final File file) throws IOException
  {
    try (
        final FileOutputStream fos = new FileOutputStream(file);
        final FrameFileWriter writer = FrameFileWriter.open(
            Channels.newChannel(fos), null, ByteTracker.unboundedTracker()
        )
    ) {
      frames.forEach(
          frame -> {
            try {
              writer.writeFrame(frame, FrameFileWriter.NO_PARTITION);
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
      );
    }

    return file;
  }

  public static File writeFrameFileWithPartitions(
      final Sequence<IntObjectPair<Frame>> framesWithPartitions,
      final File file
  ) throws IOException
  {
    try (final FrameFileWriter writer = FrameFileWriter.open(
        Channels.newChannel(new FileOutputStream(file)),
        null,
        ByteTracker.unboundedTracker()
    )) {
      framesWithPartitions.forEach(
          frameWithPartition -> {
            try {
              writer.writeFrame(frameWithPartition.right(), frameWithPartition.leftInt());
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
      );
    }

    return file;
  }

  public static void assertRowsEqual(final Sequence<List<Object>> expected, final Sequence<List<Object>> actual)
  {
    final List<List<Object>> expectedRows = expected.toList();
    final List<List<Object>> actualRows = actual.toList();

    Assert.assertEquals("number of rows", expectedRows.size(), actualRows.size());

    for (int i = 0; i < expectedRows.size(); i++) {
      assertRowEqual("row #" + i, expectedRows.get(i), actualRows.get(i));
    }
  }

  /**
   * Asserts that two rows are equal, using {@link Objects#deepEquals} to work properly on {@code Object[]}.
   */
  public static void assertRowEqual(final List<Object> expected, final List<Object> actual)
  {
    assertRowEqual(null, expected, actual);
  }

  /**
   * Asserts that two rows are equal, using {@link Objects#deepEquals} to work properly on {@code Object[]}.
   */
  public static void assertRowEqual(final String message, final List<Object> expected, final List<Object> actual)
  {
    boolean ok;

    if (expected.size() == actual.size()) {
      ok = true;

      for (int i = 0; i < expected.size(); i++) {
        final Object expectedValue = expected.get(i);
        final Object actualValue = actual.get(i);

        if (!Objects.deepEquals(expectedValue, actualValue)) {
          ok = false;
          break;
        }
      }
    } else {
      ok = false;
    }

    if (!ok) {
      // Call Assert.assertEquals, which we expect to fail, to get a nice failure message
      Assert.assertEquals(
          message,
          Arrays.deepToString(expected.toArray()),
          Arrays.deepToString(actual.toArray())
      );

      // Just in case it doesn't fail for some reason, fail anyway.
      Assert.fail(message);
    }
  }

  public static Frame adapterToFrame(final StorageAdapter adapter, final FrameType frameType)
  {
    return Iterables.getOnlyElement(
        FrameSequenceBuilder.fromAdapter(adapter)
                            .allocator(HeapMemoryAllocator.unlimited())
                            .frameType(frameType)
                            .frames()
                            .toList()
    );
  }

  public static FrameSegment adapterToFrameSegment(
      final StorageAdapter adapter,
      final FrameType frameType
  )
  {
    return new FrameSegment(
        adapterToFrame(adapter, frameType),
        FrameReader.create(adapter.getRowSignature()),
        SegmentId.of("TestFrame", adapter.getInterval(), "0", 0)
    );
  }

  public static FrameSegment adapterToFrameSegment(
      final StorageAdapter adapter,
      final FrameType frameType,
      final SegmentId segmentId
  )
  {
    return new FrameSegment(
        adapterToFrame(adapter, frameType),
        FrameReader.create(adapter.getRowSignature()),
        segmentId
    );
  }

  /**
   * Reads a sequence of rows from a frame channel using a non-vectorized cursor from
   * {@link FrameStorageAdapter#makeCursorHolder(CursorBuildSpec)}.
   *
   * @param channel     the channel
   * @param frameReader reader for this channel
   */
  public static Sequence<List<Object>> readRowsFromFrameChannel(
      final ReadableFrameChannel channel,
      final FrameReader frameReader
  )
  {
    return new FrameChannelSequence(channel)
        .flatMap(
            frame -> {
              final CursorHolder cursorHolder = new FrameStorageAdapter(frame, frameReader, Intervals.ETERNITY)
                  .makeCursorHolder(CursorBuildSpec.FULL_SCAN);
              final Cursor cursor = cursorHolder.asCursor();
              if (cursor == null) {
                return Sequences.withBaggage(Sequences.empty(), cursorHolder);
              }
              return readRowsFromCursor(cursor, frameReader.signature()).withBaggage(cursorHolder);
            }
        );
  }

  /**
   * Reads a sequence of rows from a storage adapter.
   *
   * If {@param populateRowNumberIfPresent} is set, and the provided signature contains {@link #ROW_NUMBER_COLUMN},
   * then that column will be populated with a row number from the adapter.
   *
   * @param adapter           the adapter
   * @param signature         optional signature for returned rows; will use {@code adapter.rowSignature()} if null
   * @param populateRowNumber whether to populate {@link #ROW_NUMBER_COLUMN}
   */
  public static Sequence<List<Object>> readRowsFromAdapter(
      final StorageAdapter adapter,
      @Nullable final RowSignature signature,
      final boolean populateRowNumber
  )
  {
    final RowSignature signatureToUse = signature == null ? adapter.getRowSignature() : signature;
    final CursorHolder cursorHolder = makeCursorForAdapter(adapter, populateRowNumber);
    final Cursor cursor = cursorHolder.asCursor();
    if (cursor == null) {
      return Sequences.withBaggage(Sequences.empty(), cursorHolder);
    }
    return readRowsFromCursor(cursor, signatureToUse).withBaggage(cursorHolder);
  }

  /**
   * Creates a Cursor and  from a storage adapter.
   *
   * If {@param populateRowNumber} is set, the row number will be populated into {@link #ROW_NUMBER_COLUMN}.
   *
   * @param adapter           the adapter
   * @param populateRowNumber whether to populate {@link #ROW_NUMBER_COLUMN}
   */
  public static CursorHolder makeCursorForAdapter(
      final StorageAdapter adapter,
      final boolean populateRowNumber
  )
  {
    final SettableLongVirtualColumn rowNumberVirtualColumn;
    final VirtualColumns virtualColumns;

    if (populateRowNumber) {
      rowNumberVirtualColumn = new SettableLongVirtualColumn(ROW_NUMBER_COLUMN);
      virtualColumns = VirtualColumns.create(Collections.singletonList(rowNumberVirtualColumn));
    } else {
      rowNumberVirtualColumn = null;
      virtualColumns = VirtualColumns.EMPTY;
    }

    final CursorBuildSpec buildSpec = CursorBuildSpec.builder()
                                                     .setVirtualColumns(virtualColumns)
                                                     .build();

    final CursorHolder cursorHolder = adapter.makeCursorHolder(buildSpec);
    if (populateRowNumber) {
      return new CursorHolder()
      {
        @Nullable
        @Override
        public Cursor asCursor()
        {
          final Cursor cursor = cursorHolder.asCursor();
          if (cursor == null) {
            return null;
          }
          return new RowNumberUpdatingCursor(cursor, rowNumberVirtualColumn);
        }

        @Override
        public List<OrderBy> getOrdering()
        {
          return cursorHolder.getOrdering();
        }

        @Override
        public void close()
        {
          cursorHolder.close();
        }
      };
    } else {
      return cursorHolder;
    }
  }

  public static Sequence<List<Object>> readRowsFromCursor(final Cursor cursor, final RowSignature signature)
  {
    final ColumnSelectorFactory columnSelectorFactory = cursor.getColumnSelectorFactory();

    final List<Supplier<Object>> columnReaders = new ArrayList<>();

    for (int i = 0; i < signature.size(); i++) {
      final String columnName = signature.getColumnName(i);
      final Optional<ColumnType> columnType = signature.getColumnType(i);

      if (columnType.isPresent() && columnType.get().is(ValueType.STRING)) {
        final DimensionSelector selector =
            columnSelectorFactory.makeDimensionSelector(DefaultDimensionSpec.of(columnName));

        columnReaders.add(dimensionSelectorReader(selector));
      } else {
        final ColumnValueSelector<?> selector = columnSelectorFactory.makeColumnValueSelector(columnName);
        columnReaders.add(selector::getObject);
      }
    }

    final List<List<Object>> retVal = new ArrayList<>();
    while (!cursor.isDone()) {
      final List<Object> o = columnReaders.stream().map(Supplier::get).collect(Collectors.toList());
      retVal.add(o);
      cursor.advance();
    }

    return Sequences.simple(retVal);
  }

  public static Sequence<List<Object>> readRowsFromVectorCursor(final VectorCursor cursor, final RowSignature signature)
  {
    final VectorColumnSelectorFactory columnSelectorFactory = cursor.getColumnSelectorFactory();

    final List<Supplier<Object[]>> columnReaders = new ArrayList<>();

    for (int i = 0; i < signature.size(); i++) {
      final String columnName = signature.getColumnName(i);
      final Supplier<Object[]> columnReader = ColumnProcessors.makeVectorProcessor(
          columnName,
          RowReadingVectorColumnProcessorFactory.INSTANCE,
          columnSelectorFactory
      );

      columnReaders.add(columnReader);
    }

    final List<List<Object>> retVal = new ArrayList<>();

    while (!cursor.isDone()) {
      final int vectorSize = cursor.getCurrentVectorSize();
      final List<Object[]> columns = columnReaders.stream().map(Supplier::get).collect(Collectors.toList());

      for (int i = 0; i < vectorSize; i++) {
        final List<Object> row = new ArrayList<>();

        for (final Object[] column : columns) {
          row.add(column[i]);
        }

        retVal.add(row);
      }

      cursor.advance();
    }

    return Sequences.simple(retVal);
  }

  private static Supplier<Object> dimensionSelectorReader(final DimensionSelector selector)
  {
    return () -> {
      // Different from selector.getObject(): allows us to differentiate null, [null], and []
      final IndexedInts row = selector.getRow();
      final int sz = row.size();

      if (sz == 1) {
        return selector.lookupName(row.get(0));
      } else {
        final List<String> retVal = new ArrayList<>(sz);

        for (int i = 0; i < sz; i++) {
          retVal.add(selector.lookupName(row.get(i)));
        }

        return retVal;
      }
    };
  }
}
