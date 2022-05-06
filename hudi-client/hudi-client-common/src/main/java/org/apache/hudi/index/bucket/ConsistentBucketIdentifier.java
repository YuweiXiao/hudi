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

import org.apache.hudi.common.fs.FSUtils;
import org.apache.hudi.common.model.ConsistentHashingNode;
import org.apache.hudi.common.model.HoodieConsistentHashingMetadata;
import org.apache.hudi.common.model.HoodieKey;
import org.apache.hudi.common.util.hash.HashID;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentBucketIdentifier extends BucketIdentifier {

  /**
   * Hashing metadata of a partition
   */
  private final HoodieConsistentHashingMetadata metadata;
  /**
   * in-memory structure to speed up rang mapping (hashing value -> hashing node)
   */
  private TreeMap<Integer, ConsistentHashingNode> ring;
  /**
   * mapping from fileId -> hashing node
   */
  private Map<String, ConsistentHashingNode> fileIdToBucket;

  public ConsistentBucketIdentifier(HoodieConsistentHashingMetadata metadata) {
    this.metadata = metadata;
    initialize();
  }

  public Collection<ConsistentHashingNode> getNodes() {
    return ring.values();
  }

  public HoodieConsistentHashingMetadata getMetadata() {
    return metadata;
  }

  public int getNumBuckets() {
    return getNodes().size();
  }

  /**
   * Get bucket of the given file group
   *
   * @param fileId the file group id. NOTE: not filePfx (i.e., uuid)
   * @return
   */
  public ConsistentHashingNode getBucketByFileId(String fileId) {
    return fileIdToBucket.get(fileId);
  }

  public ConsistentHashingNode getBucket(HoodieKey hoodieKey, String indexKeyFields) {
    return getBucket(getHashKeys(hoodieKey, indexKeyFields));
  }

  protected ConsistentHashingNode getBucket(List<String> hashKeys) {
    int hashValue = 0;
    for (int i = 0; i < hashKeys.size(); ++i) {
      hashValue = HashID.getXXHash32(hashKeys.get(i), hashValue);
    }
    return getBucket(hashValue & HoodieConsistentHashingMetadata.MAX_HASH_VALUE);
  }

  protected ConsistentHashingNode getBucket(int hashValue) {
    SortedMap<Integer, ConsistentHashingNode> tailMap = ring.tailMap(hashValue);
    return tailMap.isEmpty() ? ring.firstEntry().getValue() : tailMap.get(tailMap.firstKey());
  }

  /**
   * Initialize necessary data structure to facilitate bucket identifying.
   * Specifically, we construct:
   * - a in-memory tree (ring) to speed up range mapping searching.
   * - a hash table (fileIdToBucket) to allow lookup of bucket using fileId.
   */
  private void initialize() {
    this.fileIdToBucket = new HashMap<>();
    this.ring = new TreeMap<>();
    for (ConsistentHashingNode p : metadata.getNodes()) {
      ring.put(p.getValue(), p);
      // one bucket has only one file group, so append 0 directly
      fileIdToBucket.put(FSUtils.createNewFileId(p.getFileIdPfx(), 0), p);
    }
  }
}
