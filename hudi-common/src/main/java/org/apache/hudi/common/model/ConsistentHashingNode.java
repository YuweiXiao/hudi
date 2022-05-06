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

package org.apache.hudi.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Used in consistent hashing index, representing nodes in the consistent hash ring.
 * Record the end hash range value and its corresponding file group id.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsistentHashingNode implements Serializable {

  private int value;
  private String fileIdPfx;

  public ConsistentHashingNode() {
  }

  public ConsistentHashingNode(int value, String fileIdPfx) {
    this.value = value;
    this.fileIdPfx = fileIdPfx;
  }

  public static String toJsonString(List<ConsistentHashingNode> nodes) throws IOException {
    return getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(nodes);
  }

  public static List<ConsistentHashingNode> fromJsonString(String json) throws Exception {
    if (json == null || json.isEmpty()) {
      return Collections.emptyList();
    }

    ConsistentHashingNode[] nodes = getObjectMapper().readValue(json, ConsistentHashingNode[].class);
    return Arrays.asList(nodes);
  }

  protected static ObjectMapper getObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    return mapper;
  }

  public int getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }

  public String getFileIdPfx() {
    return fileIdPfx;
  }

  public void setFileIdPfx(String fileIdPfx) {
    this.fileIdPfx = fileIdPfx;
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("ConsistentHashingNode{");
    sb.append("value=").append(value);
    sb.append(", fileIdPfx='").append(fileIdPfx).append('\'');
    sb.append('}');
    return sb.toString();
  }
}