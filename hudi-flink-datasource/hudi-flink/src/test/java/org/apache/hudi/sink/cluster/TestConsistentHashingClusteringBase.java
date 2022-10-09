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

package org.apache.hudi.sink.cluster;

import org.apache.hudi.client.clustering.plan.strategy.FlinkConsistentBucketClusteringPlanStrategy;
import org.apache.hudi.common.model.HoodieTableType;
import org.apache.hudi.common.model.WriteOperationType;
import org.apache.hudi.common.table.HoodieTableMetaClient;
import org.apache.hudi.config.HoodieClusteringConfig;
import org.apache.hudi.configuration.FlinkOptions;
import org.apache.hudi.index.HoodieIndex;
import org.apache.hudi.sink.clustering.FlinkClusteringConfig;
import org.apache.hudi.sink.clustering.update.strategy.FlinkConsistentBucketDuplicateUpdateStrategy;
import org.apache.hudi.util.CompactionUtil;
import org.apache.hudi.util.StreamerUtil;
import org.apache.hudi.utils.TestConfigurations;
import org.apache.hudi.utils.TestData;
import org.apache.hudi.utils.TestSQL;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.config.ExecutionConfigOptions;
import org.apache.flink.table.api.internal.TableEnvironmentImpl;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestConsistentHashingClusteringBase {

  protected static final Map<String, String> EXPECTED_AFTER_INITIAL_INSERT = new HashMap<>();
  protected static final Map<String, String> EXPECTED_AFTER_UPSERT = new HashMap<>();

  static {
    EXPECTED_AFTER_INITIAL_INSERT.put("", "id1,,id1,Danny,23,1000,, id2,,id2,Stephen,33,2000,, " +
        "id3,,id3,Julian,53,3000,, id4,,id4,Fabian,31,4000,, id5,,id5,Sophia,18,5000,, " +
        "id6,,id6,Emma,20,6000,, id7,,id7,Bob,44,7000,, id8,,id8,Han,56,8000,, ]");
    EXPECTED_AFTER_UPSERT.put("", "[id1,,id1,Danny,24,1000,, id2,,id2,Stephen,34,2000,, id3,,id3,Julian,54,3000,, " +
        "id4,,id4,Fabian,32,4000,, id5,,id5,Sophia,18,5000,, id6,,id6,Emma,20,6000,, " +
        "id7,,id7,Bob,44,7000,, id8,,id8,Han,56,8000,, id9,,id9,Jane,19,6000,, " +
        "id10,,id10,Ella,38,7000,, id11,,id11,Phoebe,52,8000,,]");
  }

  @TempDir
  File tempFile;

  protected void prepareData(TableEnvironment tableEnv) throws Exception {
    // Insert initial data
    Map<String, String> options = getDefaultConsistentHashingOption();
    String hoodieTableDDL = TestConfigurations.getCreateHoodieTableDDL("t1", options, false, "");
    tableEnv.executeSql(hoodieTableDDL);
    tableEnv.executeSql(TestSQL.INSERT_T1).await();
    TimeUnit.SECONDS.sleep(3);

    // Validate the insertion
    TestData.checkWrittenData(tempFile, EXPECTED_AFTER_INITIAL_INSERT, 0);
  }

  protected TableEnvironment setupTableEnv() {
    EnvironmentSettings settings = EnvironmentSettings.newInstance().inBatchMode().build();
    TableEnvironment tableEnv = TableEnvironmentImpl.create(settings);
    tableEnv.getConfig().getConfiguration()
        .setInteger(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM, 4);
    return tableEnv;
  }

  protected Configuration getDefaultConfiguration() throws Exception {
    FlinkClusteringConfig cfg = new FlinkClusteringConfig();
    cfg.path = tempFile.getAbsolutePath();
    Configuration conf = FlinkClusteringConfig.toFlinkConfig(cfg);

    HoodieTableMetaClient metaClient = StreamerUtil.createMetaClient(conf);

    conf.setString(FlinkOptions.TABLE_NAME, metaClient.getTableConfig().getTableName());
    conf.setString(FlinkOptions.RECORD_KEY_FIELD, metaClient.getTableConfig().getRecordKeyFieldProp());
    conf.setString(FlinkOptions.PARTITION_PATH_FIELD, metaClient.getTableConfig().getPartitionFieldProp());
    for (Map.Entry<String, String> e : getDefaultConsistentHashingOption().entrySet()) {
      conf.setString(e.getKey(), e.getValue());
    }
    CompactionUtil.setAvroSchema(conf, metaClient);

    return conf;
  }

  protected Map<String, String> getDefaultConsistentHashingOption() {
    Map<String, String> options = new HashMap<>();
    options.put(FlinkOptions.PATH.key(), tempFile.getAbsolutePath());
    options.put(FlinkOptions.TABLE_TYPE.key(), HoodieTableType.MERGE_ON_READ.name());
    options.put(FlinkOptions.OPERATION.key(), WriteOperationType.UPSERT.name());
    options.put(FlinkOptions.INDEX_TYPE.key(), HoodieIndex.IndexType.BUCKET.name());
    options.put(FlinkOptions.BUCKET_INDEX_ENGINE_TYPE.key(), HoodieIndex.BucketIndexEngineType.CONSISTENT_HASHING.name());
    options.put(FlinkOptions.BUCKET_INDEX_NUM_BUCKETS.key(), "4");
    options.put(FlinkOptions.CLUSTERING_UPDATE_STRATEGY.key(), FlinkConsistentBucketDuplicateUpdateStrategy.class.getName());
    options.put(FlinkOptions.CLUSTERING_PLAN_STRATEGY_CLASS.key(), FlinkConsistentBucketClusteringPlanStrategy.class.getName());
    // Flink currently only support schedule, and the clustering execution have to be done by Spark engine.
    options.put(HoodieClusteringConfig.EXECUTION_STRATEGY_CLASS_NAME.key(), "org.apache.hudi.client.clustering.run.strategy.SparkConsistentBucketClusteringExecutionStrategy");

    // Disable compaction/clustering by default
    options.put(FlinkOptions.COMPACTION_ASYNC_ENABLED.key(), "false");
    options.put(FlinkOptions.CLUSTERING_SCHEDULE_ENABLED.key(), "false");

    return options;
  }
}
