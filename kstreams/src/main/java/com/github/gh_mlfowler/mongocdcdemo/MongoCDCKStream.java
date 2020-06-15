package com.github.gh_mlfowler.mongocdcdemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.cli.*;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.joda.time.Duration;
import org.joda.time.LocalDateTime;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.TimeZone;

public class MongoCDCKStream {

    private static final SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    static {
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {

        Options opts = new Options();

        Option inTopic = new Option("i", "in", true, "Source topic (Debezium)");
        inTopic.setRequired(true);
        opts.addOption(inTopic);

        Option outTopic = new Option("o", "out", true, "Destination topic");
        outTopic.setRequired(true);
        opts.addOption(outTopic);

        Option kafkaOpt = new Option("k", "kafka", true, "Kafka bootstrap (e.g. 127.0.0.1:9092)");
        kafkaOpt.setRequired(true);
        opts.addOption(kafkaOpt);

        Option updateOpt = new Option("u", "update", true, "Update type (1) Patch, (2) Before/After, (3) Before/After with Delta");
        updateOpt.setType(Integer.class);
        updateOpt.setRequired(false);
        opts.addOption(updateOpt);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        UpdateStrategy updateStrategy = UpdateStrategy.PATCH;

        try {
            cmd = parser.parse(opts, args);

            String [] updates = cmd.getOptionValues(updateOpt.getOpt());
            if(updates != null && updates.length > 0) {
                switch (Integer.parseInt(updates[0])) {
                    case 1:
                        updateStrategy = UpdateStrategy.PATCH;
                        break;
                    case 2:
                        updateStrategy = UpdateStrategy.BEFORE_AFTER;
                        break;
                    case 3:
                        updateStrategy = UpdateStrategy.DELTA;
                        break;
                    default:
                        System.err.println("Update strategy is a value from 1-3.");
                        formatter.printHelp("mongo-cdc", opts);
                        System.exit(1);
                }
            }
        } catch (org.apache.commons.cli.ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("mongo-cdc", opts);

            System.exit(1);
        }

        String source = cmd.getOptionValue("in");
        String dest = cmd.getOptionValue("out");
        String kafka = cmd.getOptionValue("kafka");
        final UpdateStrategy strategy = updateStrategy;

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, source + "-cdc");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafka);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Bytes().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.Bytes().getClass());

        StreamsBuilder builder = new StreamsBuilder();
        KStream<Bytes, Bytes> cdc = builder.stream(source);
        KTable<Bytes, Bytes> table = builder.table(dest);

        cdc.leftJoin(table, (left, right) -> merge(right, left, strategy))
                .groupByKey()
                .reduce((agg, bytes) -> bytes, Materialized.as(dest + ".table"))
                .toStream().to(dest);


        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();
    }

    static Bytes merge(Bytes oldValue, Bytes newValue, UpdateStrategy strategy) {

        try {
            if (newValue == null) {
                return null;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode newjson = mapper.readTree(
                    new String(newValue.get(), StandardCharsets.UTF_8)
            );
            JsonNode payload = newjson.get("payload");
            String data = null;

            switch (payload.get("op").asText()) {
                case "c":
                case "r":
                    switch (strategy) {
                        case PATCH:
                            data = payload.get("after").asText();
                            break;
                        case BEFORE_AFTER:
                        case DELTA:
                            JsonNode after = mapper.readTree(payload.get("after").asText());
                            ObjectNode out = mapper.createObjectNode();
                            out.set("after", after);
                            data = out.toString();
                            break;
                    }
                    break;
                case "u":
                    if(oldValue != null) {
                        switch (strategy) {
                            case PATCH:
                                data = patchedUpdate(new String(oldValue.get(), StandardCharsets.UTF_8), payload.get("patch").asText());
                                break;
                            case BEFORE_AFTER:
                                data = beforeAfterUpdate(
                                        new String(oldValue.get(), StandardCharsets.UTF_8), payload.get("patch").asText());
                                break;
                            case DELTA:
                                data = beforeAfterDeltaUpdate(new String(oldValue.get(), StandardCharsets.UTF_8), payload.get("patch").asText());
                                break;
                        }
                    }
                    break;
                case "d":
                    data = null;
                    break;
            }

            if (data != null) {
                return new Bytes(data.getBytes(StandardCharsets.UTF_8));
            } else {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    static String patchedUpdate(String oldjson, String patch) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode after = (ObjectNode) mapper.readTree(oldjson);
        JsonNode json = mapper.readTree(patch);

        if (json.has("$set")) {
            JsonNode set = json.get("$set");
            for (Iterator<String> it = set.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                after.put(key, tidyValue(set.get(key)));
            }
        }

        if (json.has("$unset")) {
            JsonNode unset = json.get("$unset");
            for (Iterator<String> it = unset.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                after.remove(key);
            }
        }

        return after.toString();
    }

    static String beforeAfterUpdate(String oldjson, String patch) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode out = (ObjectNode) mapper.readTree(oldjson);
        out.set("before", out.get("after"));

        ObjectNode after = out.get("after").deepCopy();
        JsonNode json = mapper.readTree(patch);

        if (json.has("$set")) {
            JsonNode set = json.get("$set");
            for (Iterator<String> it = set.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                after.put(key, tidyValue(set.get(key)));
            }
        }

        if (json.has("$unset")) {
            JsonNode unset = json.get("$unset");
            for (Iterator<String> it = unset.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                after.remove(key);
            }
        }

        out.set("after", after);
        return out.toString();
    }

    static String beforeAfterDeltaUpdate(String oldjson, String patch) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode out = (ObjectNode) mapper.readTree(oldjson);
        JsonNode before = out.get("after");

        ObjectNode after = out.get("after").deepCopy();
        JsonNode json = mapper.readTree(patch);

        ObjectNode delta = mapper.createObjectNode();

        if (json.has("$set")) {
            JsonNode set = json.get("$set");
            for (Iterator<String> it = set.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                JsonNode afterValue = set.get(key);
                after.put(key, tidyValue(set.get(key)));

                if(before.has(key)) {
                    JsonNode beforeValue = before.get(key);
                    if(beforeValue.asDouble(Double.MIN_VALUE) > Double.MIN_VALUE) {
                        delta.put(key, BigDecimal
                                .valueOf(afterValue.asDouble())
                                .subtract(BigDecimal.valueOf(beforeValue.asDouble()))
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue());
                    } else if (set.get(key).has("$date")) {
                        LocalDateTime beforeTime = LocalDateTime.parse(before.get(key).asText().replace("Z", ""));
                        LocalDateTime afterTime = LocalDateTime.parse(after.get(key).asText().replace("Z", ""));
                        delta.put(key, new Duration(beforeTime.toDateTime(), afterTime.toDateTime()).toString());
                    }
                }
            }
        }

        if (json.has("$unset")) {
            JsonNode unset = json.get("$unset");
            for (Iterator<String> it = unset.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                after.remove(key);
            }
        }

        out.set("before", before);
        out.set("delta", delta);
        out.set("after", after);

        return out.toString();
    }

    static String tidyValue(JsonNode value) {
        if (value.has("$date")) {
            Date date = new Date(Long.parseLong(value.get("$date").asText()));
            return sdf.format(date);
        } else return value.asText();
    }
}
