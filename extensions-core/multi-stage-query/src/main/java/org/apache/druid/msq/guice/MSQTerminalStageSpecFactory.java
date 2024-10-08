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

package org.apache.druid.msq.guice;

import org.apache.druid.msq.indexing.destination.SegmentGenerationStageSpec;
import org.apache.druid.msq.indexing.destination.TerminalStageSpec;
import org.apache.druid.sql.calcite.planner.PlannerContext;
import org.apache.druid.sql.calcite.rel.DruidQuery;

public class MSQTerminalStageSpecFactory
{
  /**
   * Creates a {@link TerminalStageSpec} which determines the final of a query. Currently, always returns a segment
   * generation spec, but this can be used to configure a wide range of behaviours.
   */
  public TerminalStageSpec createTerminalStageSpec(DruidQuery druidQuery, PlannerContext plannerContext)
  {
    return SegmentGenerationStageSpec.instance();
  }
}
