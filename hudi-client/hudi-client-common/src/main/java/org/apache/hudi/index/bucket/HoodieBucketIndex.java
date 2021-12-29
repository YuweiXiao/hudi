/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.index.bucket;

import org.apache.hudi.client.WriteStatus;
import org.apache.hudi.client.utils.LazyIterableIterator;
import org.apache.hudi.common.data.HoodieData;
import org.apache.hudi.common.engine.HoodieEngineContext;
import org.apache.hudi.common.model.HoodieKey;
import org.apache.hudi.common.model.HoodieRecord;
import org.apache.hudi.common.model.HoodieRecordLocation;
import org.apache.hudi.common.model.WriteOperationType;
import org.apache.hudi.common.util.Option;
import org.apache.hudi.config.HoodieWriteConfig;
import org.apache.hudi.exception.HoodieIndexException;
import org.apache.hudi.index.HoodieIndex;
import org.apache.hudi.index.HoodieIndexUtils;
import org.apache.hudi.table.HoodieTable;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Hash indexing mechanism.
 */
public abstract class HoodieBucketIndex extends HoodieIndex<Object, Object> {

  private static final Logger LOG = LogManager.getLogger(HoodieBucketIndex.class);

  protected int numBuckets;
  protected String indexKeyFields;

  public HoodieBucketIndex(HoodieWriteConfig config) {
    super(config);

    this.numBuckets = config.getBucketIndexNumBuckets();
    this.indexKeyFields = config.getBucketIndexHashField();
    LOG.info("use bucket index, numBuckets = " + numBuckets + ", indexFields: " + indexKeyFields);
  }

  @Override
  public HoodieData<WriteStatus> updateLocation(HoodieData<WriteStatus> writeStatuses,
                                                HoodieEngineContext context,
                                                HoodieTable hoodieTable)
      throws HoodieIndexException {
    return writeStatuses;
  }

  @Override
  public <R> HoodieData<HoodieRecord<R>> tagLocation(
      HoodieData<HoodieRecord<R>> records, HoodieEngineContext context,
      HoodieTable hoodieTable)
      throws HoodieIndexException {
    // initialize necessary information before tagging. e.g., hashing metadata
    List<String> partitions = records.map(HoodieRecord::getPartitionPath).distinct().collectAsList();
    LOG.info("Initializing hashing metadata for partitions: " + partitions);
    initialize(hoodieTable, partitions);

    return records.mapPartitions(iterator ->
        new LazyIterableIterator<HoodieRecord<R>, HoodieRecord<R>>(iterator) {

          @Override
          protected void start() {

          }

          @Override
          protected HoodieRecord<R> computeNext() {
            // TODO maybe batch the operation to improve performance
            HoodieRecord record = inputItr.next();
            HoodieRecordLocation loc = getBucket(record.getKey(), record.getPartitionPath());
            return HoodieIndexUtils.getTaggedRecord(record, Option.ofNullable(loc));
          }

          @Override
          protected void end() {

          }
        }
    );
  }

  @Override
  public boolean requiresTagging(WriteOperationType operationType) {
    switch (operationType) {
      case INSERT:
      case INSERT_OVERWRITE:
      case UPSERT:
      case DELETE:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean rollbackCommit(String instantTime) {
    return true;
  }

  @Override
  public boolean isGlobal() {
    return false;
  }

  @Override
  public boolean canIndexLogFiles() {
    return true;
  }

  @Override
  public boolean isImplicitWithStorage() {
    return true;
  }

  public int getNumBuckets() {
    return numBuckets;
  }

  /**
   * Initialize necessary fields
   * @param table
   * @param partitions
   */
  protected abstract void initialize(HoodieTable table, List<String> partitions);

  /**
   * Get record location given the record key and its partition
   * @param key
   * @param partitionPath
   * @return
   */
  protected abstract HoodieRecordLocation getBucket(HoodieKey key, String partitionPath);
}
