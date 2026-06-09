package com.bus.predictor.flink;

import com.bus.predictor.common.constant.KafkaTopicConstant;
import com.bus.predictor.common.model.GpsData;
import com.bus.predictor.common.model.VehiclePosition;
import com.bus.predictor.common.model.VehicleStatus;
import com.bus.predictor.common.util.GeoHashUtil;
import com.bus.predictor.common.util.GpsValidator;
import com.bus.predictor.common.util.JsonUtil;
import com.bus.predictor.flink.function.GpsCleanFunction;
import com.bus.predictor.flink.function.RedisPositionSinkFunction;
import com.bus.predictor.flink.function.MySqlTrajectorySinkFunction;
import com.bus.predictor.flink.function.VehicleStatusFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;

import java.util.Properties;

public class FlinkProcessorApplication {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);
        env.getCheckpointConfig().setCheckpointTimeout(30000);

        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", getProperty(args, "kafka.servers", "localhost:9092"));
        kafkaProps.setProperty("group.id", "bus-gps-processor");

        FlinkKafkaConsumer<String> rawConsumer = new FlinkKafkaConsumer<>(
                KafkaTopicConstant.GPS_RAW_TOPIC,
                new SimpleStringSchema(),
                kafkaProps
        );
        rawConsumer.setStartFromLatest();

        DataStream<String> rawStream = env.addSource(rawConsumer);

        DataStream<GpsData> gpsStream = rawStream
                .map(json -> JsonUtil.fromJson(json, GpsData.class))
                .filter(GpsValidator::isValid);

        SingleOutputStreamOperator<GpsData> cleanedStream = gpsStream
                .keyBy(GpsData::getVehicleId)
                .process(new GpsCleanFunction());

        cleanedStream.map(JsonUtil::toJson)
                .addSink(new FlinkKafkaProducer<>(
                        getProperty(args, "kafka.servers", "localhost:9092"),
                        KafkaTopicConstant.GPS_CLEANED_TOPIC,
                        new SimpleStringSchema()));

        DataStream<VehiclePosition> positionStream = cleanedStream
                .map(gps -> VehiclePosition.builder()
                        .vehicleId(gps.getVehicleId())
                        .longitude(gps.getLongitude())
                        .latitude(gps.getLatitude())
                        .speed(gps.getSpeed())
                        .direction(gps.getDirection())
                        .geoHash(GeoHashUtil.encode6(gps.getLatitude(), gps.getLongitude()))
                        .gpsTime(gps.getTimestamp())
                        .receiveTime(System.currentTimeMillis())
                        .status(VehicleStatus.ONLINE)
                        .build());

        String redisHost = getProperty(args, "redis.host", "localhost");
        int redisPort = Integer.parseInt(getProperty(args, "redis.port", "6379"));

        positionStream.addSink(new RedisPositionSinkFunction(redisHost, redisPort));

        String mysqlUrl = getProperty(args, "mysql.url", "jdbc:mysql://localhost:3306/bus_predictor");
        String mysqlUser = getProperty(args, "mysql.user", "root");
        String mysqlPass = getProperty(args, "mysql.password", "root");

        cleanedStream.addSink(new MySqlTrajectorySinkFunction(mysqlUrl, mysqlUser, mysqlPass));

        gpsStream.keyBy(GpsData::getVehicleId)
                .process(new VehicleStatusFunction())
                .map(JsonUtil::toJson)
                .addSink(new FlinkKafkaProducer<>(
                        getProperty(args, "kafka.servers", "localhost:9092"),
                        KafkaTopicConstant.VEHICLE_STATUS_TOPIC,
                        new SimpleStringSchema()));

        env.execute("Bus GPS Stream Processor");
    }

    private static String getProperty(String[] args, String key, String defaultValue) {
        String prefix = "--" + key + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return defaultValue;
    }
}
