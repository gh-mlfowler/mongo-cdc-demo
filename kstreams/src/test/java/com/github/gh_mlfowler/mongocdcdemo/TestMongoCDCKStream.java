package com.github.gh_mlfowler.mongocdcdemo;

import org.apache.kafka.common.utils.Bytes;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class TestMongoCDCKStream {

    private static String INITIAL = "{\"schema\":{\"type\":\"struct\",\"fields\":[{\"type\":\"string\",\"optional\":true,\"name\":\"io.debezium.data.Json\",\"version\":1,\"field\":\"after\"},{\"type\":\"string\",\"optional\":true,\"name\":\"io.debezium.data.Json\",\"version\":1,\"field\":\"patch\"},{\"type\":\"string\",\"optional\":true,\"name\":\"io.debezium.data.Json\",\"version\":1,\"field\":\"filter\"},{\"type\":\"struct\",\"fields\":[{\"type\":\"string\",\"optional\":false,\"field\":\"version\"},{\"type\":\"string\",\"optional\":false,\"field\":\"connector\"},{\"type\":\"string\",\"optional\":false,\"field\":\"name\"},{\"type\":\"int64\",\"optional\":false,\"field\":\"ts_ms\"},{\"type\":\"string\",\"optional\":true,\"name\":\"io.debezium.data.Enum\",\"version\":1,\"parameters\":{\"allowed\":\"true,last,false\"},\"default\":\"false\",\"field\":\"snapshot\"},{\"type\":\"string\",\"optional\":false,\"field\":\"db\"},{\"type\":\"string\",\"optional\":false,\"field\":\"rs\"},{\"type\":\"string\",\"optional\":false,\"field\":\"collection\"},{\"type\":\"int32\",\"optional\":false,\"field\":\"ord\"},{\"type\":\"int64\",\"optional\":true,\"field\":\"h\"},{\"type\":\"int64\",\"optional\":true,\"field\":\"tord\"}],\"optional\":false,\"name\":\"io.debezium.connector.mongo.Source\",\"field\":\"source\"},{\"type\":\"string\",\"optional\":true,\"field\":\"op\"},{\"type\":\"int64\",\"optional\":true,\"field\":\"ts_ms\"},{\"type\":\"struct\",\"fields\":[{\"type\":\"string\",\"optional\":false,\"field\":\"id\"},{\"type\":\"int64\",\"optional\":false,\"field\":\"total_order\"},{\"type\":\"int64\",\"optional\":false,\"field\":\"data_collection_order\"}],\"optional\":true,\"field\":\"transaction\"}],\"optional\":false,\"name\":\"mongocdc.wishlist.product.Envelope\"},\"payload\":{\"after\":\"{\\\"_id\\\": {\\\"$oid\\\": \\\"5ee39875c94bffb83b95288e\\\"},\\\"item\\\": \\\"Software Engineering at Google: Lessons Learned from Programming Over Time\\\",\\\"target_prize\\\": 20,\\\"store\\\": \\\"amazon.co.uk\\\",\\\"price\\\": 31.99,\\\"last_check\\\": \\\"2020-05-24T16:05:43Z\\\",\\\"url\\\": \\\"https://www.amazon.co.uk/dp/1492082791/\\\"}\",\"patch\":null,\"filter\":null,\"source\":{\"version\":\"1.1.2.Final\",\"connector\":\"mongodb\",\"name\":\"mongocdc\",\"ts_ms\":1591974225000,\"snapshot\":\"last\",\"db\":\"wishlist\",\"rs\":\"mongo-replica-set\",\"collection\":\"product\",\"ord\":1,\"h\":0,\"tord\":null},\"op\":\"r\",\"ts_ms\":1591974232360,\"transaction\":null}}";
    private static String SECOND = "{\"schema\":{\"type\":\"struct\",\"fields\":[{\"type\":\"string\",\"optional\":true,\"name\":\"io.debezium.data.Json\",\"version\":1,\"field\":\"after\"},{\"type\":\"string\",\"optional\":true,\"name\":\"io.debezium.data.Json\",\"version\":1,\"field\":\"patch\"},{\"type\":\"string\",\"optional\":true,\"name\":\"io.debezium.data.Json\",\"version\":1,\"field\":\"filter\"},{\"type\":\"struct\",\"fields\":[{\"type\":\"string\",\"optional\":false,\"field\":\"version\"},{\"type\":\"string\",\"optional\":false,\"field\":\"connector\"},{\"type\":\"string\",\"optional\":false,\"field\":\"name\"},{\"type\":\"int64\",\"optional\":false,\"field\":\"ts_ms\"},{\"type\":\"string\",\"optional\":true,\"name\":\"io.debezium.data.Enum\",\"version\":1,\"parameters\":{\"allowed\":\"true,last,false\"},\"default\":\"false\",\"field\":\"snapshot\"},{\"type\":\"string\",\"optional\":false,\"field\":\"db\"},{\"type\":\"string\",\"optional\":false,\"field\":\"rs\"},{\"type\":\"string\",\"optional\":false,\"field\":\"collection\"},{\"type\":\"int32\",\"optional\":false,\"field\":\"ord\"},{\"type\":\"int64\",\"optional\":true,\"field\":\"h\"},{\"type\":\"int64\",\"optional\":true,\"field\":\"tord\"}],\"optional\":false,\"name\":\"io.debezium.connector.mongo.Source\",\"field\":\"source\"},{\"type\":\"string\",\"optional\":true,\"field\":\"op\"},{\"type\":\"int64\",\"optional\":true,\"field\":\"ts_ms\"},{\"type\":\"struct\",\"fields\":[{\"type\":\"string\",\"optional\":false,\"field\":\"id\"},{\"type\":\"int64\",\"optional\":false,\"field\":\"total_order\"},{\"type\":\"int64\",\"optional\":false,\"field\":\"data_collection_order\"}],\"optional\":true,\"field\":\"transaction\"}],\"optional\":false,\"name\":\"mongocdc.wishlist.product.Envelope\"},\"payload\":{\"after\":null,\"patch\":\"{\\\"$v\\\": 1,\\\"$set\\\": {\\\"last_check\\\": {\\\"$date\\\": 1591974366044},\\\"price\\\": 24.9}}\",\"filter\":\"{\\\"_id\\\": {\\\"$oid\\\": \\\"5ee39875c94bffb83b95288e\\\"}}\",\"source\":{\"version\":\"1.1.2.Final\",\"connector\":\"mongodb\",\"name\":\"mongocdc\",\"ts_ms\":1591974366000,\"snapshot\":\"false\",\"db\":\"wishlist\",\"rs\":\"mongo-replica-set\",\"collection\":\"product\",\"ord\":1,\"h\":0,\"tord\":null},\"op\":\"u\",\"ts_ms\":1591974365467,\"transaction\":null}}";

    @Test
    public void testPatchedUpdate() {

        UpdateStrategy strategy = UpdateStrategy.PATCH;

        String firstOut = "{\"_id\": {\"$oid\": \"5ee39875c94bffb83b95288e\"},\"item\": \"Software Engineering at Google: Lessons Learned from Programming Over Time\",\"target_prize\": 20,\"store\": \"amazon.co.uk\",\"price\": 31.99,\"last_check\": \"2020-05-24T16:05:43Z\",\"url\": \"https://www.amazon.co.uk/dp/1492082791/\"}";
        String finalOut = "{\"_id\":{\"$oid\":\"5ee39875c94bffb83b95288e\"},\"item\":\"Software Engineering at Google: Lessons Learned from Programming Over Time\",\"target_prize\":20,\"store\":\"amazon.co.uk\",\"price\":\"24.9\",\"last_check\":\"2020-06-12T15:06:06Z\",\"url\":\"https://www.amazon.co.uk/dp/1492082791/\"}";

        Bytes stored = MongoCDCKStream.merge(null, new Bytes(INITIAL.getBytes(StandardCharsets.UTF_8)), strategy);
        assertEquals(firstOut, stored.toString());

        stored = MongoCDCKStream.merge(stored, new Bytes(SECOND.getBytes(StandardCharsets.UTF_8)), strategy);
        assertEquals(finalOut, stored.toString());
    }

    @Test
    public void testBeforeAfterUpdate() {

        UpdateStrategy strategy = UpdateStrategy.BEFORE_AFTER;

        String firstOut = "{\"after\":{\"_id\":{\"$oid\":\"5ee39875c94bffb83b95288e\"},\"item\":\"Software Engineering at Google: Lessons Learned from Programming Over Time\",\"target_prize\":20,\"store\":\"amazon.co.uk\",\"price\":31.99,\"last_check\":\"2020-05-24T16:05:43Z\",\"url\":\"https://www.amazon.co.uk/dp/1492082791/\"}}";
        String finalOut = "{\"after\":{\"_id\":{\"$oid\":\"5ee39875c94bffb83b95288e\"},\"item\":\"Software Engineering at Google: Lessons Learned from Programming Over Time\",\"target_prize\":20,\"store\":\"amazon.co.uk\",\"price\":\"24.9\",\"last_check\":\"2020-06-12T15:06:06Z\",\"url\":\"https://www.amazon.co.uk/dp/1492082791/\"},\"before\":{\"_id\":{\"$oid\":\"5ee39875c94bffb83b95288e\"},\"item\":\"Software Engineering at Google: Lessons Learned from Programming Over Time\",\"target_prize\":20,\"store\":\"amazon.co.uk\",\"price\":31.99,\"last_check\":\"2020-05-24T16:05:43Z\",\"url\":\"https://www.amazon.co.uk/dp/1492082791/\"}}";

        Bytes stored = MongoCDCKStream.merge(null, new Bytes(INITIAL.getBytes(StandardCharsets.UTF_8)), strategy);
        assertEquals(firstOut, stored.toString());

        stored = MongoCDCKStream.merge(stored, new Bytes(SECOND.getBytes(StandardCharsets.UTF_8)), strategy);
        assertEquals(finalOut, stored.toString());
    }

    @Test
    public void testDeltaUpdate() {

        UpdateStrategy strategy = UpdateStrategy.DELTA;

        String firstOut = "{\"after\":{\"_id\":{\"$oid\":\"5ee39875c94bffb83b95288e\"},\"item\":\"Software Engineering at Google: Lessons Learned from Programming Over Time\",\"target_prize\":20,\"store\":\"amazon.co.uk\",\"price\":31.99,\"last_check\":\"2020-05-24T16:05:43Z\",\"url\":\"https://www.amazon.co.uk/dp/1492082791/\"}}";
        String finalOut = "{\"after\":{\"_id\":{\"$oid\":\"5ee39875c94bffb83b95288e\"},\"item\":\"Software Engineering at Google: Lessons Learned from Programming Over Time\",\"target_prize\":20,\"store\":\"amazon.co.uk\",\"price\":\"24.9\",\"last_check\":\"2020-06-12T15:06:06Z\",\"url\":\"https://www.amazon.co.uk/dp/1492082791/\"},\"before\":{\"_id\":{\"$oid\":\"5ee39875c94bffb83b95288e\"},\"item\":\"Software Engineering at Google: Lessons Learned from Programming Over Time\",\"target_prize\":20,\"store\":\"amazon.co.uk\",\"price\":31.99,\"last_check\":\"2020-05-24T16:05:43Z\",\"url\":\"https://www.amazon.co.uk/dp/1492082791/\"},\"delta\":{\"last_check\":\"PT1638023S\",\"price\":-7.09}}";

        Bytes stored = MongoCDCKStream.merge(null, new Bytes(INITIAL.getBytes(StandardCharsets.UTF_8)), strategy);
        assertEquals(firstOut, stored.toString());

        stored = MongoCDCKStream.merge(stored, new Bytes(SECOND.getBytes(StandardCharsets.UTF_8)), strategy);
        assertEquals(finalOut, stored.toString());
    }

}