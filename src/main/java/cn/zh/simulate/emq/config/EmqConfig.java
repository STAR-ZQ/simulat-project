package cn.zh.simulate.emq.config;

import cn.zh.simulate.emq.entity.DeviceInfo;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.SneakyThrows;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class EmqConfig {
    private Logger log = LoggerFactory.getLogger(EmqConfig.class);
    @Value("${mqtt.username}")
    private String userName;
    @Value("${mqtt.password}")
    private String passWord;
    @Value("${mqtt.serverURL}")
    private String serverURL;
    @Value("${mqtt.qos}")
    private Integer qos;
    @Value("${mqtt.isReconnect}")
    private Boolean isReconnection;
    @Value("${mqtt.keepAliveInterval}")
    private Integer keepAliveInterval;
    @Value("${mqtt.connectionTimeout}")
    private Integer connectionTimeout;
    /**
     * mqtt连接对象
     */
    private MqttClient client;
    private MqttConnectOptions options;
    //    private String [] topic = new String[100];
    private String[] topic;
    /**
     * 发布消息次数
     */
    private int publishCount = 1;

    /**
     * 休眠秒
     */
    int second = 0;

    /**
     * 方便取值对象 （中间对象）
     * 从mapClient赋值到vo
     */
    DeviceInfo vo = new DeviceInfo();
    /**
     * 开关map 最新的开关数据
     * 暂时没用  改成对象属性拿值
     */
    Map<Integer, String> switchMap = new HashMap<>();
    /**
     * 请求开关map数据
     */
    Map switchReqMap = new HashMap();
    /**
     * publish
     */
    StringBuffer buffer = new StringBuffer();
    /**
     * report 开关
     */
    StringBuffer switchSb = new StringBuffer();
    /**
     * report
     */
    StringBuffer sb = new StringBuffer();
    /**
     * 1.暂时没用 用来防止重复连接
     * 2.存最新数据  id  对象数据
     */
    Map<String, DeviceInfo> mapClient = new HashMap<>();
    /**
     * 存更新前（上）数据  id  对象数据
     */
    Map<String, DeviceInfo> mapPreClient = new HashMap<>();
    /**
     * 全部id    -id:id/type  | -id/type
     */
    Map<String, String> mapAllClient = new HashMap<>();
    /**
     * time  格式化
     */
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    /**
     * 对应线路需要上报的属性  lineId:property
     */
    Map<Integer, List<String>> propertyMap = Maps.newHashMap();

    ExecutorService service = Executors.newFixedThreadPool(1);
    ExecutorService threadPool = new ThreadPoolExecutor(
            30,
            100,
            3,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(3),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    public void conn(Map<String, List<String>> map, String clientId, DeviceInfo deviceInfo, String type) throws MqttException {
        service.execute(new Runnable() {
            @SneakyThrows
            @Override
            public void run() {
                switch (type) {
                    case "device":
                        mapAllClient.put(clientId, type);
                        break;
                    case "gateway":
                        mapAllClient.put(clientId, type);
                        List<String> slaves = map.get(clientId);
                        slaves.forEach(e -> {
                            mapAllClient.put(e, clientId + "/" + type);
                            mapClient.put(e, deviceInfo);
                        });
                        break;
                    default:
                        break;
                }
                //初始化设备数据
                mapClient.put(clientId, deviceInfo);
                client = new MqttClient(serverURL, clientId, new MemoryPersistence());
                options = new MqttConnectOptions();
                options.setUserName(userName);
                options.setPassword(passWord.toCharArray());
                options.setCleanSession(true);
                options.setKeepAliveInterval(keepAliveInterval);
                options.setAutomaticReconnect(true);
                options.setConnectionTimeout(connectionTimeout);

                List<String> sIds = map.get(clientId);
                topic = new String[CollectionUtils.isEmpty(sIds) ? 1 : 2];
                topic[0] = "/" + type + "/" + clientId + "/cmd";
                options.setWill(topic[0], "offline".getBytes(), qos, true);
                client.connect(options);


                switchMap = new HashMap<>();
                //从设备
                if (!CollectionUtils.isEmpty(sIds)) {
                    topic[1] = "/" + type + "/" + clientId + "/+/cmd";
                }
                System.out.println("topic:" + JSON.toJSONString(topic));
                client.subscribe(topic);

                MqttMessage mqttMessage = new MqttMessage();
                mqttMessage.setPayload("online".getBytes());
                client.publish(topic[0], mqttMessage);
                // 此处使用的MqttCallbackExtended类而不是MqttCallback，是因为如果emq服务出现异常导致客户端断开连接后，重连后会自动调用connectComplete方法
                client.setCallback(new MqttCallbackExtended() {

                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        System.out.println("重新连接:连接完成...");
                        try {
                            // 重连后要自己重新订阅topic，这样emq服务发的消息才能重新接收到，不然的话，断开后客户端只是重新连接了服务，并没有自动订阅，导致接收不到消息
                            client.subscribe(topic);
//                            if (CollectionUtils.isEmpty(sIds)) {
//                                topic = "/" + type + "/" + clientId + "/cmd";
//                            } else {
////                                if (deviceInfo.getNum() > 0) {
//                                topic = "/" + type + "/" + clientId + "/cmd";
//                                client.subscribe(topic);
//                            }
//                            topic = "/" + type + "/" + clientId + "/+/cmd";
////                            }
                            log.info("订阅成功");
                        } catch (Exception e) {
                            log.info("订阅出现异常:{}", e);
                        }
                    }

                    @SneakyThrows
                    @Override
                    public void connectionLost(Throwable cause) {
                        System.out.println("失去连接....");
                        client.reconnect();
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        String content = new String(message.getPayload());
//                        System.out.println("订阅后的消息回调:接收消息主题 : " + topic);
//                        System.out.println("接收消息Qos : " + message.getQos());
//                        System.out.println("接收消息内容 : " + content);
                        if ("offline".equals(content) || "online".equals(content)) {
//                            log.error("yizhu=======================");
                            return;
                        }
                        try {
                            log.info("开始进入buildDataPublish======================");
                            buildDataPublish(map, topic, content, message);
                        } catch (MqttException e) {
                            log.error("订阅回调方法出现异常:" + e.getMessage());
                        }
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        publishCount++;
                        System.out.println("发布消息后deliveryComplete....");
                    }
                });
                System.out.println(clientId + "连接状态：" + client.isConnected());
            }
        });
    }

    /**
     * 线程上报
     *
     * @param clientId 设备id
     */
    public void reportThread(String clientId) {
        service.execute(new Runnable() {
            @SneakyThrows
            @Override
            public void run() {
                while (true) {
                    Thread.sleep(2000);
                    DeviceInfo deviceInfo = mapClient.get(clientId);
                    boolean isAllReport = false;
                    String operation = mapAllClient.get(clientId);
                    String[] split = operation.split("/");
                    String type;
                    int length = split.length;
                    type = length > 1 ? "slave" : "device";
                    int size = "device".equals(type) ? deviceInfo.getNum() : deviceInfo.getSlaveInfo().getSNum();

                    if (second == 600) {
                        System.out.println("上报600s=================================================================");
                        second = 0;
                        for (int i = 0; i < size; i++) {
                            if ("slave".equals(type)) {
                                deviceInfo.getSlaveInfo().getEnergyQ()[i] = deviceInfo.getSlaveInfo().getEnergyQ()[i].add(BigDecimal.valueOf((0.44 / (3600 / deviceInfo.getSlaveInfo().getScheduleTime()) * 0.9))).setScale(2, BigDecimal.ROUND_HALF_UP);
                                deviceInfo.getSlaveInfo().getEnergyP()[i] = deviceInfo.getSlaveInfo().getEnergyP()[i].add(BigDecimal.valueOf((0.44 / (3600 / deviceInfo.getSlaveInfo().getScheduleTime()) * 0.1)).setScale(2, BigDecimal.ROUND_HALF_UP));
                            } else {
                                deviceInfo.getEnergyQ()[i] = deviceInfo.getEnergyQ()[i].add(BigDecimal.valueOf((0.44 / (3600 / deviceInfo.getScheduleTime()) * 0.9))).setScale(2, BigDecimal.ROUND_HALF_UP);
                                deviceInfo.getEnergyP()[i] = deviceInfo.getEnergyP()[i].add(BigDecimal.valueOf((0.44 / (3600 / deviceInfo.getScheduleTime()) * 0.1)).setScale(2, BigDecimal.ROUND_HALF_UP));
                            }
                            isAllReport = true;
                        }
                    }

                    for (int i = 0; i < size; i++) {
                        log.info("设备===============================:" + clientId);
                        judgeMethod(new DeviceInfo(deviceInfo.getNum(), deviceInfo.getSlaveInfo().getSNum(), deviceInfo.getSwitchStatus()), deviceInfo, type, i, isAllReport);
                    }

                    report(clientId);
                    second = second + 2;
                }
            }
        });
    }

    /**
     * 处理响应数据JSON发布信息
     *
     * @param topic   主题
     * @param content 内容
     * @param message 消息对象
     */
    public void buildDataPublish(Map<String, List<String>> map, String topic, String content, MqttMessage message) throws MqttException {
        String[] topicAttr = topic.split("/");
        //根据设备id拿到对应的对象数据
        vo = mapClient.get(topicAttr[topicAttr.length - 2]);
        System.out.println("设备id：" + topicAttr[topicAttr.length - 2] + "\nvo:" + JSON.toJSONString(vo));

        // 处理数据
        JSONObject jsonObject = JSONObject.parseObject(content);
        String method = jsonObject.get("method").toString();
        String srcMsgId = jsonObject.get("msgid").toString();
        String type = topicAttr[1];
        String msgId = "C" + publishCount;
        String data = jsonObject.get("data").toString();
        //拿到data数据
        JSONObject object = JSONObject.parseObject(data);

        buffer.append("{\"version\":\"2.0.0\",\"srcmsgid\":\"").append(srcMsgId).append("\",\"msgid\":\"").append(msgId).append("\",\"device\":\"").append(topicAttr[topicAttr.length - 2]).append("\",\"status\":\"OK\",");
        switch (method) {
            case "slave.list":
                List<String> ids = map.get(topicAttr[2]);
                buffer.append("\"data\":{\"slaves\":[");
                if (!CollectionUtils.isEmpty(ids)) {
                    for (String id : ids) {
                        buffer.append("\"").append(id).append("\",");
                    }
                    buffer.deleteCharAt(buffer.length() - 1);
                }
                buffer.append("]},");
                break;
            case "act.do":
                buffer.append("\"data\":{},");
                break;
            case "tag.get":
                String lineId = object.get("line_id").toString();
                String tags = object.get("tags").toString();
                JSONArray jsonArray = JSONArray.parseArray(tags);
                buffer.append("\"data\":{").append("\"line_id\":\"").append(lineId);

                for (int i = 0; i < jsonArray.size(); i++) {
                    switch (jsonArray.get(i).toString()) {
                        case "voltage":
                            buffer.append("\",\"voltage\":\"").append(vo.getVoltage()[i]);
                            break;
                        case "power_q":
                            buffer.append(",\"power_q\":\"").append(vo.getPowerQ()[i]);
                            break;
                        case "current":
                            buffer.append(",\"current\":\"").append(vo.getCurrent()[i]);
                            break;
                        case "power_s":
                            buffer.append(",\"power_s\":\"").append(vo.getPowerS()[i]);
                            break;
                        case "frequency":
                            buffer.append(",\"frequency\":\"").append(vo.getFrequency()[i]);
                            break;
                        case "energy_p":
                            buffer.append(",\"energy_p\":\"").append(vo.getEnergyP()[i]);
                            break;
                        case "leak_current":
                            buffer.append(",\"leak_current\":\"").append(vo.getLeakCurrent()[i]);
                            break;
                        case "energy_q":
                            buffer.append(",\"energy_q\":\"").append(vo.getEnergyQ()[i]);
                            break;
                        case "power_p":
                            buffer.append(",\"power_p\":\"").append(vo.getPowerP()[i]);
                            break;
                        case "tilt":
                            buffer.append(",\"tilt\":\"").append(vo.getTilt()[i]);
                            break;
                        case "switch":
                            buffer.append(",\"switch\":\"").append(vo.getSwitchStatus()[i]);
                            break;
                        default:
                            break;
                    }
                }
                buffer.append("\",");
                break;
            default:
                break;
        }
        buffer.append("\"time\":\"").append(simpleDateFormat.format(new Date())).append("\"}");

//        message.setQos(1);
        message.setPayload(buffer.toString().getBytes());
        client.publish(topic.concat("_resp"), message);
        buffer = new StringBuffer();
        if ("act.do".equals(method)) {
            switchSb = new StringBuffer();
            switchSb.append("{\"version\":\"2.0.0\",\"data\":[");
            boolean isReport = false;
            switchReqMap = (Map) JSON.parse(object.get("switch").toString());
            int length = "device".equals(type) || topicAttr.length==4? vo.getNum() : vo.getSlaveInfo().getSNum();
            String types = "device".equals(type) || topicAttr.length==4?"device":"gateway";
//            int length = topicAttr.length ? vo.getNum() : vo.getSlaveInfo().getSNum();
            log.error(length+"开关=========="+topic+"======="+type);
            for (int i = 0; i < length; i++) {
                switch (types) {
                    case "device":
                        if (!vo.getSwitchStatus()[i].equals(switchReqMap.get(i))) {
                            isReport = true;
                            switchSb.append("{\"switch\":\"").append(switchReqMap.get(i));
                            if ("off".equals(switchReqMap.get(i))) {
                                vo.getSwitchStatus()[i] = "off";
                                //关闭开关电流变为0
                                vo.getCurrent()[i] = 0;
                            } else {
                                vo.getSwitchStatus()[i] = "on";
                                //打开开关生成电流
                                vo.getCurrent()[i] = RandomNumber.produceRateRandomNumber(1500, 5000, Lists.newArrayList(1900, 2000), Lists.newArrayList(5.0, 90.0, 5.0));
                            }
                            switchSb.append("\",\"current\":\"").append(vo.getCurrent()[i]);
                            switchSb.append("\",\"line_id\":\"").append(i).append("\"},");
                        }
                        break;
                    case "gateway":
                        if (!vo.getSlaveInfo().getSwitchStatus()[i].equals(switchReqMap.get(i))) {
                            isReport = true;
                            switchSb.append("{\"switch\":\"").append(switchReqMap.get(i));
                            if ("off".equals(switchReqMap.get(i))) {
                                vo.getSlaveInfo().getSwitchStatus()[i] = "off";
                                //关闭开关电流变为0
                                vo.getSlaveInfo().getCurrent()[i] = 0;
                            } else {
                                vo.getSlaveInfo().getSwitchStatus()[i] = "on";
                                //打开开关生成电流
                                vo.getSlaveInfo().getCurrent()[i] = RandomNumber.produceRateRandomNumber(1500, 5000, Lists.newArrayList(1900, 2000), Lists.newArrayList(5.0, 90.0, 5.0));
                            }
                            switchSb.append("\",\"current\":\"").append(vo.getSlaveInfo().getCurrent()[i]);
                            switchSb.append("\",\"line_id\":\"").append(i).append("\"},");
                        }
                        break;
                    default:
                        break;
                }
            }
            if (isReport) {
                switchSb.deleteCharAt(switchSb.length() - 1);
            } else {
                return;
            }
            switchSb.append("],\"time\":\"").append(simpleDateFormat.format(new Date())).append("\",\"device\":\"").append(topicAttr[topicAttr.length - 2]).append("\",\"msgid\":\"").append(msgId).append("\"}");
            message.setPayload(switchSb.toString().getBytes());
            String reportTopic = topic.substring(0, topic.length() - 3).concat("report");
            System.out.println("switch-->report----------------->" + switchReqMap.get(0) + "/" + "off".equals(switchReqMap.get(0)) + "\n" + switchSb.toString());
            System.out.println("vo:" + JSON.toJSONString(vo));
            client.publish(reportTopic, message);
        }
    }

    /**
     * 容忍度比较判断
     *
     * @param newData 新生成的随机数据
     * @param preData 展示数据
     * @param type    设备类型
     * @param lineId  线路id
     */
    public void judgeMethod(DeviceInfo newData, DeviceInfo preData, String type, int lineId, boolean isAllReport) {
        vo = preData;
        List<String> list = Lists.newArrayList();
        switch (type) {
            case "device":
                list.add(newData.getFrequency()[lineId].subtract(preData.getFrequency()[lineId]).abs().compareTo(BigDecimal.valueOf(0.05)) == 1 ? "frequency" : null);
                list.add(Math.abs(newData.getVoltage()[lineId] - preData.getVoltage()[lineId]) > 2 ? "voltage" : null);
                list.add(Math.abs(newData.getCurrent()[lineId] - preData.getCurrent()[lineId]) > 200 ? "current" : null);
                list.add(Math.abs(newData.getLeakCurrent()[lineId] - preData.getLeakCurrent()[lineId]) > 5 ? "leak_current" : null);
                list.add(Math.abs(newData.getPowerP()[lineId] - preData.getPowerP()[lineId]) > 5 ? "power_p" : null);
                list.add(Math.abs(newData.getPowerQ()[lineId] - preData.getPowerQ()[lineId]) > 5 ? "power_q" : null);
                list.add(Math.abs(newData.getPowerS()[lineId] - preData.getPowerS()[lineId]) > 5 ? "power_s" : null);
                list.add(Math.abs(newData.getTilt()[lineId] - preData.getTilt()[lineId]) > 2 ? "tilt" : null);
                System.out.println("judgeMethod:device-->lineId" + lineId);
                System.out.println("judgeMethod:device-->newData" + JSON.toJSONString(newData));
                System.out.println("judgeMethod:device-->preData" + JSON.toJSONString(preData));
                vo.getFrequency()[lineId] = newData.getFrequency()[lineId].subtract(preData.getFrequency()[lineId]).abs().compareTo(BigDecimal.valueOf(0.05)) == 1 ? newData.getFrequency()[lineId] : preData.getFrequency()[lineId];
                vo.getVoltage()[lineId] = Math.abs(newData.getVoltage()[lineId] - preData.getVoltage()[lineId]) > 2 ? newData.getVoltage()[lineId] : preData.getVoltage()[lineId];
                vo.getCurrent()[lineId] = Math.abs(newData.getCurrent()[lineId] - preData.getCurrent()[lineId]) > 200 ? newData.getCurrent()[lineId] : preData.getCurrent()[lineId];
                vo.getLeakCurrent()[lineId] = Math.abs(newData.getLeakCurrent()[lineId] - preData.getLeakCurrent()[lineId]) > 5 ? newData.getLeakCurrent()[lineId] : preData.getLeakCurrent()[lineId];
                vo.getPowerP()[lineId] = Math.abs(newData.getPowerP()[lineId] - preData.getPowerP()[lineId]) > 5 ? newData.getPowerP()[lineId] : preData.getPowerP()[lineId];
                vo.getPowerQ()[lineId] = Math.abs(newData.getPowerQ()[lineId] - preData.getPowerQ()[lineId]) > 5 ? newData.getPowerQ()[lineId] : preData.getPowerQ()[lineId];
                vo.getPowerS()[lineId] = Math.abs(newData.getPowerS()[lineId] - preData.getPowerS()[lineId]) > 5 ? newData.getPowerS()[lineId] : preData.getPowerS()[lineId];
                vo.getTilt()[lineId] = Math.abs(newData.getTilt()[lineId] - preData.getTilt()[lineId]) > 2 ? newData.getTilt()[lineId] : preData.getTilt()[lineId];
                break;
            case "slave":
                list.add(newData.getSlaveInfo().getFrequency()[lineId].subtract(preData.getSlaveInfo().getFrequency()[lineId]).abs().compareTo(BigDecimal.valueOf(0.05)) == 1 ? "frequency" : null);
                list.add(Math.abs(newData.getSlaveInfo().getVoltage()[lineId] - preData.getSlaveInfo().getVoltage()[lineId]) > 2 ? "voltage" : null);
                list.add(Math.abs(newData.getSlaveInfo().getCurrent()[lineId] - preData.getSlaveInfo().getCurrent()[lineId]) > 200 ? "current" : null);
                list.add(Math.abs(newData.getSlaveInfo().getLeakCurrent()[lineId] - preData.getSlaveInfo().getLeakCurrent()[lineId]) > 5 ? "leak_current" : null);
                list.add(Math.abs(newData.getSlaveInfo().getPowerP()[lineId] - preData.getSlaveInfo().getPowerP()[lineId]) > 5 ? "power_p" : null);
                list.add(Math.abs(newData.getSlaveInfo().getPowerQ()[lineId] - preData.getSlaveInfo().getPowerQ()[lineId]) > 5 ? "power_q" : null);
                list.add(Math.abs(newData.getSlaveInfo().getPowerS()[lineId] - preData.getSlaveInfo().getPowerS()[lineId]) > 5 ? "power_s" : null);
                list.add(Math.abs(newData.getSlaveInfo().getTilt()[lineId] - preData.getSlaveInfo().getTilt()[lineId]) > 2 ? "tilt" : null);
                System.out.println("judgeMethod:slave-->lineId" + lineId);
                System.out.println("judgeMethod:slave-->newData" + JSON.toJSONString(newData));
                System.out.println("judgeMethod:slave-->preData" + JSON.toJSONString(preData));
                vo.getSlaveInfo().getFrequency()[lineId] = newData.getSlaveInfo().getFrequency()[lineId].subtract(preData.getSlaveInfo().getFrequency()[lineId]).abs().compareTo(BigDecimal.valueOf(0.05)) == 1 ? newData.getSlaveInfo().getFrequency()[lineId] : preData.getSlaveInfo().getFrequency()[lineId];
                vo.getSlaveInfo().getVoltage()[lineId] = Math.abs(newData.getSlaveInfo().getVoltage()[lineId] - preData.getSlaveInfo().getVoltage()[lineId]) > 2 ? newData.getSlaveInfo().getVoltage()[lineId] : preData.getSlaveInfo().getVoltage()[lineId];
                vo.getSlaveInfo().getCurrent()[lineId] = Math.abs(newData.getSlaveInfo().getCurrent()[lineId] - preData.getSlaveInfo().getCurrent()[lineId]) > 200 ? newData.getSlaveInfo().getCurrent()[lineId] : preData.getSlaveInfo().getCurrent()[lineId];
                vo.getSlaveInfo().getLeakCurrent()[lineId] = Math.abs(newData.getSlaveInfo().getLeakCurrent()[lineId] - preData.getSlaveInfo().getLeakCurrent()[lineId]) > 5 ? newData.getSlaveInfo().getLeakCurrent()[lineId] : preData.getSlaveInfo().getLeakCurrent()[lineId];
                vo.getSlaveInfo().getPowerP()[lineId] = Math.abs(newData.getSlaveInfo().getPowerP()[lineId] - preData.getSlaveInfo().getPowerP()[lineId]) > 5 ? newData.getSlaveInfo().getPowerP()[lineId] : preData.getSlaveInfo().getPowerP()[lineId];
                vo.getSlaveInfo().getPowerQ()[lineId] = Math.abs(newData.getSlaveInfo().getPowerQ()[lineId] - preData.getSlaveInfo().getPowerQ()[lineId]) > 5 ? newData.getSlaveInfo().getPowerQ()[lineId] : preData.getSlaveInfo().getPowerQ()[lineId];
                vo.getSlaveInfo().getPowerS()[lineId] = Math.abs(newData.getSlaveInfo().getPowerS()[lineId] - preData.getSlaveInfo().getPowerS()[lineId]) > 5 ? newData.getSlaveInfo().getPowerS()[lineId] : preData.getSlaveInfo().getPowerS()[lineId];
                vo.getSlaveInfo().getTilt()[lineId] = Math.abs(newData.getSlaveInfo().getTilt()[lineId] - preData.getSlaveInfo().getTilt()[lineId]) > 2 ? newData.getSlaveInfo().getTilt()[lineId] : preData.getSlaveInfo().getTilt()[lineId];
                break;
            default:
                break;
        }
        if (isAllReport) {
            list.add("energy_p");
            list.add("energy_q");
        }
        list = list.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(list)) {
            propertyMap.put(lineId, list);
        }
    }


//
//    public void testWhile(List<String> ids) throws MqttException, InterruptedException {
//        while (true) {
//            for (String clientId : ids) {
//                Thread.sleep(2000);
//                DeviceInfo deviceInfo = mapClient.get(clientId);
//                if (second == 600) {
//                    second = 0;
//                    for (int i = 0; i < deviceInfo.getEnergyQ().length; i++) {
//                        deviceInfo.getEnergyQ()[i] = deviceInfo.getEnergyQ()[i].add(BigDecimal.valueOf((0.44 / (3600 / 600) * 0.9))).setScale(2, BigDecimal.ROUND_HALF_UP);
//                        deviceInfo.getEnergyP()[i] = deviceInfo.getEnergyP()[i].add(BigDecimal.valueOf((0.44 / (3600 / 600) * 0.1)).setScale(2, BigDecimal.ROUND_HALF_UP));
//                    }
//                }
//                String operation = mapAllClient.get(clientId);
//                String[] split = operation.split("/");
//                String type;
//                int length = split.length;
//                if (length > 1) {
//                    type = "slave";
//                } else {
//                    type = "device";
//                }
//                int size = "device".equals(type) ? deviceInfo.getNum() : deviceInfo.getSlaveInfo().getSNum();
//                for (int i = 0; i < size; i++) {
//                    judgeMethod(new DeviceInfo(deviceInfo.getNum(), deviceInfo.getSlaveInfo().getSNum(), deviceInfo.getSwitchStatus()), deviceInfo, type, i);
//                }
//                report(clientId);
//                second = second + 2;
//            }
//        }
//
//    }

    /**
     * 上报
     *
     * @param clientId
     * @throws MqttException
     */
    public void report(String clientId) throws MqttException {
        vo = mapClient.get(clientId);
        //线路数
        int switchNum = 0;
        sb = new StringBuffer();

        String topic = null;
        String operation = mapAllClient.get(clientId);
        String[] split = operation.split("/");
        int length = split.length;
        if (length > 1) {
            switchNum = vo.getSlaveInfo().getSNum();
            topic = "/" + split[1] + "/" + split[0] + "/" + clientId + "/report";
        } else {
            switchNum = vo.getNum();
            topic = "/" + split[0] + "/" + clientId + "/report";
        }

        sb.append("{\"device\":\"").append(clientId);
        sb.append("\",\"version\":\"2.0.0\",\"msgid\":\"C").append(publishCount).append("\",\"data\":[");
        if (propertyMap.size() < 1) {
            return;
        }

        for (int i = 0; i < switchNum; i++) {
            if (propertyMap.containsKey(i)) {
                sb.append("{\"line_id\":\"").append(i);
                for (String property : propertyMap.get(i)) {
                    if (length > 1) {
                        buildSlaveData(property, i);
                    } else {
                        buildDeviceData(property, i);
                    }
                }
                sb.append("\"},");
            }
        }
        if (switchNum > 0 && sb.length() > 0) {
            System.out.println("test-->" + switchNum);
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("],\"time\":\"").append(simpleDateFormat.format(new Date())).append("\"}");
        MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setQos(1);
        mqttMessage.setPayload(sb.toString().getBytes());
//        System.out.println("report==>" + JSON.toJSONString(sb.toString()));
        client.publish(topic, mqttMessage);
        propertyMap = Maps.newHashMap();
    }

    /**
     * 构造主设备数据（/xx/id/cmd）
     *
     * @param property
     * @param i
     */
    public void buildDeviceData(String property, int i) {
        switch (property) {
            case "voltage":
                sb.append("\",\"voltage\":\"").append(vo.getVoltage()[i]);
                break;
            case "power_q":
                sb.append("\",\"power_q\":\"").append(vo.getPowerQ()[i]);
                break;
            case "current":
                sb.append("\",\"current\":\"").append(vo.getCurrent()[i]);
                break;
            case "power_s":
                sb.append("\",\"power_s\":\"").append(vo.getPowerS()[i]);
                break;
            case "frequency":
                sb.append("\",\"frequency\":\"").append(vo.getFrequency()[i]);
                break;
            case "energy_p":
                sb.append("\",\"energy_p\":\"").append(vo.getEnergyP()[i]);
                break;
            case "leak_current":
                sb.append("\",\"leak_current\":\"").append(vo.getLeakCurrent()[i]);
                break;
            case "energy_q":
                sb.append("\",\"energy_q\":\"").append(vo.getEnergyQ()[i]);
                break;
            case "power_p":
                sb.append("\",\"power_p\":\"").append(vo.getPowerP()[i]);
                break;
            case "tilt":
                sb.append("\",\"tilt\":\"").append(vo.getTilt()[i]);
                break;
            case "switch":
                sb.append("\",\"switch\":\"").append(vo.getSwitchStatus()[i]);
                break;
            default:
                break;
        }
    }

    /**
     * 构造从设备数据 （/xx/id/sid,cmd）
     *
     * @param property
     * @param i
     */
    public void buildSlaveData(String property, int i) {
        switch (property) {
            case "voltage":
                sb.append("\",\"voltage\":\"").append(vo.getSlaveInfo().getVoltage()[i]);
                break;
            case "power_q":
                sb.append("\",\"power_q\":\"").append(vo.getSlaveInfo().getPowerQ()[i]);
                break;
            case "current":
                sb.append("\",\"current\":\"").append(vo.getSlaveInfo().getCurrent()[i]);
                break;
            case "power_s":
                sb.append("\",\"power_s\":\"").append(vo.getSlaveInfo().getPowerS()[i]);
                break;
            case "frequency":
                sb.append("\",\"frequency\":\"").append(vo.getSlaveInfo().getFrequency()[i]);
                break;
            case "energy_p":
                sb.append("\",\"energy_p\":\"").append(vo.getSlaveInfo().getEnergyP()[i]);
                break;
            case "leak_current":
                sb.append("\",\"leak_current\":\"").append(vo.getSlaveInfo().getLeakCurrent()[i]);
                break;
            case "energy_q":
                sb.append("\",\"energy_q\":\"").append(vo.getSlaveInfo().getEnergyQ()[i]);
                break;
            case "power_p":
                sb.append("\",\"power_p\":\"").append(vo.getSlaveInfo().getPowerP()[i]);
                break;
            case "tilt":
                sb.append("\",\"tilt\":\"").append(vo.getSlaveInfo().getTilt()[i]);
                break;
            case "switch":
                sb.append("\",\"switch\":\"").append(vo.getSlaveInfo().getSwitchStatus()[i]);
                break;
            default:
                break;
        }
    }
}
