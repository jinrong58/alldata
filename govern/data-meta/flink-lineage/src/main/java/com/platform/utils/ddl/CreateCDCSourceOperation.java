/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.platform.utils.ddl;

import com.platform.utils.*;
import com.platform.utils.job.Operation;
import com.platform.utils.job.Schema;
import com.platform.utils.job.SinkBuilderFactory;
import com.platform.utils.module.*;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.TableResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CreateCDCSourceOperation
 *
 * @author AllDataDC
 * @date 2022/11/29 23:25
 */
public class CreateCDCSourceOperation extends AbstractOperation implements Operation {
    private static final String KEY_WORD = "EXECUTE CDCSOURCE";

    public CreateCDCSourceOperation() {
    }

    public CreateCDCSourceOperation(String statement) {
        super(statement);
    }

    @Override
    public String getHandle() {
        return KEY_WORD;
    }

    @Override
    public Operation create(String statement) {
        return new CreateCDCSourceOperation(statement);
    }

    @Override
    public TableResult build(Executor executor) {
        logger.info("Start build CDCSOURCE Task...");
        CDCSource cdcSource = CDCSource.build(statement);
        FlinkCDCConfig config = new FlinkCDCConfig(cdcSource.getConnector(), cdcSource.getHostname(), cdcSource.getPort(), cdcSource.getUsername()
                , cdcSource.getPassword(), cdcSource.getCheckpoint(), cdcSource.getParallelism(), cdcSource.getDatabase(), cdcSource.getSchema()
                , cdcSource.getTable(), cdcSource.getStartupMode(), cdcSource.getDebezium(), cdcSource.getSource(), cdcSource.getSink(), cdcSource.getJdbc());
        try {
            CDCBuilder cdcBuilder = CDCBuilderFactory.buildCDCBuilder(config);
            Map<String, Map<String, String>> allConfigMap = cdcBuilder.parseMetaDataConfigs();
            config.setSchemaFieldName(cdcBuilder.getSchemaFieldName());
            List<Schema> schemaList = new ArrayList<>();
            final List<String> schemaNameList = cdcBuilder.getSchemaList();
            final List<String> tableRegList = cdcBuilder.getTableList();
            final List<String> schemaTableNameList = new ArrayList<>();
            for (String schemaName : schemaNameList) {
                Schema schema = Schema.build(schemaName);
                if (!allConfigMap.containsKey(schemaName)) {
                    continue;
                }
                DriverConfig driverConfig = DriverConfig.build(allConfigMap.get(schemaName));
                Driver driver = Driver.build(driverConfig);
                final List<Table> tables = driver.listTables(schemaName);
                for (Table table : tables) {
                    if (!Asserts.isEquals(table.getType(), "VIEW")) {
                        if (Asserts.isNotNullCollection(tableRegList)) {
                            for (String tableReg : tableRegList) {
                                if (table.getSchemaTableName().matches(tableReg.trim()) && !schema.getTables().contains(Table.build(table.getName()))) {
                                    table.setColumns(driver.listColumnsSortByPK(schemaName, table.getName()));
                                    schema.getTables().add(table);
                                    schemaTableNameList.add(table.getSchemaTableName());
                                    break;
                                }
                            }
                        } else {
                            table.setColumns(driver.listColumnsSortByPK(schemaName, table.getName()));
                            schemaTableNameList.add(table.getSchemaTableName());
                            schema.getTables().add(table);
                        }
                    }
                }
                schemaList.add(schema);
            }
            logger.info("A total of " + schemaTableNameList.size() + " tables were detected...");
            for (int i = 0; i < schemaTableNameList.size(); i++) {
                logger.info((i + 1) + ": " + schemaTableNameList.get(i));
            }
            config.setSchemaTableNameList(schemaTableNameList);
            config.setSchemaList(schemaList);
            StreamExecutionEnvironment streamExecutionEnvironment = executor.getStreamExecutionEnvironment();
            if (Asserts.isNotNull(config.getParallelism())) {
                streamExecutionEnvironment.setParallelism(config.getParallelism());
                logger.info("Set parallelism: " + config.getParallelism());
            }
            if (Asserts.isNotNull(config.getCheckpoint())) {
                streamExecutionEnvironment.enableCheckpointing(config.getCheckpoint());
                logger.info("Set checkpoint: " + config.getCheckpoint());
            }
            DataStreamSource<String> streamSource = cdcBuilder.build(streamExecutionEnvironment);
            logger.info("Build " + config.getType() + " successful...");
            if (cdcSource.getSinks() == null || cdcSource.getSinks().size() == 0) {
                SinkBuilderFactory.buildSinkBuilder(config).build(cdcBuilder, streamExecutionEnvironment, executor.getCustomTableEnvironment(), streamSource);
            } else {
                for (Map<String, String> sink : cdcSource.getSinks()) {
                    config.setSink(sink);
                    SinkBuilderFactory.buildSinkBuilder(config).build(cdcBuilder, streamExecutionEnvironment, executor.getCustomTableEnvironment(), streamSource);
                }
            }
            logger.info("Build CDCSOURCE Task successful!");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
}
