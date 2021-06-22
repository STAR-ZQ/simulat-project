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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private MqttClient client;
    private MqttConnectOptions options;
    //    private String [] topic = new String[100];
    private String topic;
    //发布消息次数
    private int publishCount = 1;

    DeviceInfo vo = new DeviceInfo();

    /**
     * 开关map 最新的开关数据
     */
    Map<Integer, String> switchMap = new HashMap<>();
    /**
     * 请求开关map数据
     */
    Map switchReqMap = new HashMap();
    /**
     * publish0
     */
    StringBuffer buffer = new StringBuffer();
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

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    /**
     * 对应线路需要上报的属性  lineId:property
     */
    Map<Integer, List<String>> propertyMap = Maps.newHashMap();

    public void conn(Map<String, List<String>> map, String clientId, DeviceInfo deviceInfo, String type) throws MqttException {
        switch (type) {
            case "device":
                mapAllClient.put(clientId, type);
                break;
            case "gateway":
                mapAllClient.put(clientId, type);
                List<String> slaves = map.get(clientId);
                slaves.forEach(e -> {
                    mapAllClient.put(e, clientId + "/" + type);
                });
                break;
            default:
                break;
        }
        //初始化设备数据
        mapClient.put(clientId, deviceInfo);
//        if (!mapClient.containsKey(clientId)){
        client = new MqttClient(serverURL, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(userName);
        options.setPassword(passWord.toCharArray());
        options.setCleanSession(true);
        options.setKeepAliveInterval(keepAliveInterval);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(connectionTimeout);

        client.connect(options);

        List<String> sIds = map.get(clientId);

        switchMap = new HashMap<>();
        if (CollectionUtils.isEmpty(sIds)) {
            topic = "/" + type + "/" + clientId + "/cmd";
            for (int i = 0; i < deviceInfo.getNum(); i++) {
                switchMap.put(i, "off");
            }
        } else {
            if (deviceInfo.getNum() > 0) {
                topic = "/" + type + "/" + clientId + "/cmd";
                client.subscribe(topic);
            }
            topic = "/" + type + "/" + clientId + "/+/cmd";
            for (int i = 0; i < deviceInfo.getNum(); i++) {
                switchMap.put(i, "off");
            }
        }
        client.subscribe(topic);
//        client.subscribe("/"+type+"/");");
        // 此处使用的MqttCallbackExtended类而不是MqttCallback，是因为如果emq服务出现异常导致客户端断开连接后，重连后会自动调用connectComplete方法
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                System.out.println("连接完成...");
                try {
                    // 重连后要自己重新订阅topic，这样emq服务发的消息才能重新接收到，不然的话，断开后客户端只是重新连接了服务，并没有自动订阅，导致接收不到消息
                    client.subscribe(topic);
                    if (CollectionUtils.isEmpty(sIds)) {
                        topic = "/" + type + "/" + clientId + "/cmd";
                        for (int i = 0; i < deviceInfo.getNum(); i++) {
                            switchMap.put(i, "off");
                        }
                    } else {
                        if (deviceInfo.getNum() > 0) {
                            topic = "/" + type + "/" + clientId + "/cmd";
                            client.subscribe(topic);
                        }
                        topic = "/" + type + "/" + clientId + "/+/cmd";
                        for (int i = 0; i < deviceInfo.getNum(); i++) {
                            switchMap.put(i, "off");
                        }
                    }
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
                // subscribe后得到的消息会执行到这里面
                String content = new String(message.getPayload());
                System.out.println("接收消息主题 : " + topic);
                System.out.println("接收消息Qos : " + message.getQos());
                System.out.println("接收消息内容 : " + content);
                buildDataPublish(topic, content, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                publishCount++;
                System.out.println("deliveryComplete....");
            }
        });
        System.out.println(clientId + "连接状态：" + client.isConnected());
//        }
    }

    /**
     * 处理响应数据JSON发布信息
     *
     * @param topic   主题
     * @param content 内容
     * @param message 消息对象
     */
    public void buildDataPublish(String topic, String content, MqttMessage message) throws MqttException {
        String[] topicAttr = topic.split("/");
        //根据设备id拿到对应的对象数据
        vo = mapClient.get(topicAttr[topicAttr.length - 2]);

        // 处理数据
        JSONObject jsonObject = JSONObject.parseObject(content);
        String method = jsonObject.get("method").toString();
        String srcMsgId = jsonObject.get("msgid").toString();
        String msgId = "C" + publishCount;
        String data = jsonObject.get("data").toString();
        //拿到data数据
        JSONObject object = JSONObject.parseObject(data);

        buffer.append("{\"version\":\"2.0.0\",\"srcmsgid\":\"").append(srcMsgId).append("\",\"msgid\":\"").append(msgId).append("\",\"device\":\"").append(topicAttr[topicAttr.length - 2]).append("\",\"status\":\"OK\",");
        switch (method) {
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
                            buffer.append(",\"power_q:\":\"").append(vo.getPowerQ()[i]);
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
                            buffer.append(",\"switch\":\"").append(switchMap.get(i));
                            break;
                        default:
                            break;
                    }
                }
                break;
            default:
                break;
        }
        buffer.append("\",\"time\":\"").append(simpleDateFormat.format(new Date())).append("\"}");

//        message.setQos(1);
        message.setPayload(buffer.toString().getBytes());
        client.publish(topic.concat("_resp"), message);
        buffer = new StringBuffer();
        if ("act.do".equals(method)) {
            StringBuffer sb = new StringBuffer();
            sb.append("{\"version\":\"2.0.0\",\"data:\":[");
            switchReqMap = (Map) JSON.parse(object.get("switch").toString());
            for (int i = 0; i < switchMap.size(); i++) {
                if (!switchMap.get(i).equals(switchReqMap.get(i))) {
                    sb.append("{\"switch\":\"").append(switchReqMap.get(i));
                    if ("off".equals(switchReqMap.get(i))) {
                        //关闭开关电流变为0
                        vo.getFrequency()[i] = BigDecimal.ZERO;
                        sb.append("\",\"frequency\":").append(vo.getFrequency()[i]);
                    }
                    sb.append("\",\"line_id\":").append(i).append("},");
                }
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("],\"time\":\"").append(simpleDateFormat.format(new Date())).append("\",\"device\":\"").append(topicAttr[topicAttr.length - 2]).append("\",\"msgid\":\"").append(msgId).append("\"}");
            message.setPayload(sb.toString().getBytes());
            String reportTopic = topic.substring(0, topic.length() - 3).concat("report");
            client.publish(reportTopic, message);
            sb = new StringBuffer();
        }
    }

    /**
     * 容忍度比较判断
     * @param newData 新生成的随机数据
     * @param preData 展示数据
     * @param type 设备类型
     * @param lineId 线路id
     */
    public void judgeMethod(DeviceInfo newData, DeviceInfo preData, String type, int lineId) {
        List<String> list = Lists.newArrayList();
        switch (type) {
            case "device":
                list.add(newData.getFrequency()[lineId].subtract(preData.getFrequency()[lineId]).abs().compareTo(BigDecimal.valueOf(0.05)) == 1 ? "frequency" : null);
                list.add(Math.abs(newData.getVoltage()[lineId]-preData.getVoltage()[lineId]) > 2 ? "voltage" : null);
                list.add(Math.abs(newData.getCurrent()[lineId]-preData.getCurrent()[lineId]) > 200 ? "current" : null);
                list.add(Math.abs(newData.getLeakCurrent()[lineId]-preData.getLeakCurrent()[lineId]) > 5 ? "leak_current" : null);
                list.add(Math.abs(newData.getPowerP()[lineId]-preData.getPowerP()[lineId]) > 5 ? "power_p" : null);
                list.add(Math.abs(newData.getPowerQ()[lineId]-preData.getPowerQ()[lineId]) > 5 ? "power_q" : null);
                list.add(Math.abs(newData.getPowerS()[lineId]-preData.getPowerS()[lineId]) > 5 ? "power_s" : null);

                list.add(Math.abs(newData.getTilt()[lineId]-preData.getTilt()[lineId]) > 2 ? "tilt" : null);
                break;
            case "slave":
                list.add(newData.getSlaveInfo().getFrequency()[lineId].subtract(preData.getSlaveInfo().getFrequency()[lineId]).abs().compareTo(BigDecimal.valueOf(0.05)) == 1 ? "frequency" : null);
                list.add(Math.abs(newData.getSlaveInfo().getVoltage()[lineId]-preData.getSlaveInfo().getVoltage()[lineId]) > 2 ? "voltage" : null);
                list.add(Math.abs(newData.getSlaveInfo().getCurrent()[lineId]-preData.getSlaveInfo().getCurrent()[lineId]) > 200 ? "current" : null);
                list.add(Math.abs(newData.getSlaveInfo().getLeakCurrent()[lineId]-preData.getSlaveInfo().getLeakCurrent()[lineId]) > 5 ? "leak_current" : null);
                list.add(Math.abs(newData.getSlaveInfo().getPowerP()[lineId]-preData.getSlaveInfo().getPowerP()[lineId]) > 5 ? "power_p" : null);
                list.add(Math.abs(newData.getSlaveInfo().getPowerQ()[lineId]-preData.getSlaveInfo().getPowerQ()[lineId]) > 5 ? "power_q" : null);
                list.add(Math.abs(newData.getSlaveInfo().getPowerS()[lineId]-preData.getSlaveInfo().getPowerS()[lineId]) > 5 ? "power_s" : null);

                list.add(Math.abs(newData.getSlaveInfo().getTilt()[lineId]-preData.getSlaveInfo().getTilt()[lineId]) > 2 ? "tilt" : null);
                break;
            default:
                break;
        }
        propertyMap.put(lineId,list);
    }


    public void report(String clientId) throws MqttException {
        String[] topicAttr = topic.split("/");
        vo = mapClient.get(clientId);
        //线路数
        int switchNum = 0;

        String topic = null;
        String operation = mapAllClient.get(clientId);
        String[] split = operation.split("/");
        int length = split.length;
        if (length > 1) {
            switchNum = vo.getSlaveInfo().getSNum();
            topic = "/" + split[1] + "/" + split[0] + "/" + clientId + "report";
        } else {
            switchNum = vo.getNum();
            topic = "/" + split[0] + "/" + clientId + "report";
        }


//        DeviceInfo deviceInfo = new DeviceInfo();
        sb.append("{\"device\":\"").append(clientId);
//        if (topicAttr.length == 4) {
//            sb.append(topicAttr[topicAttr.length - 2]);
//            switchNum = deviceInfo.getSlaveInfo().getSNum();
//        } else {
//            sb.append(topicAttr[1]);
//            switchNum = deviceInfo.getNum();
//        }
        sb.append("\",\"version\":\"2.0.0\",\"msgid\":\"C").append(publishCount).append("\",\"data\":[");

        for (int i = 0; i < switchNum; i++) {
            if (propertyMap.containsKey(i)) {
                sb.append("{\"line_id\":\"").append(i);
                List<String> propertys = propertyMap.get(i);
                DeviceInfo finalVo = vo;
                int finalI = i;
                propertys.forEach(e -> {
                    switch (e) {
                        case "voltage":
                            sb.append("\",\"voltage\":\"").append(finalVo.getVoltage()[finalI]);
                            break;
                        case "power_q":
                            sb.append("\",\"power_q:\":\"").append(finalVo.getPowerQ()[finalI]);
                            break;
                        case "current":
                            sb.append("\",\"current\":\"").append(finalVo.getCurrent()[finalI]);
                            break;
                        case "power_s":
                            sb.append("\",\"power_s\":\"").append(finalVo.getPowerS()[finalI]);
                            break;
                        case "frequency":
                            sb.append("\",\"frequency\":\"").append(finalVo.getFrequency()[finalI]);
                            break;
                        case "energy_p":
                            sb.append("\",\"energy_p\":\"").append(finalVo.getEnergyP()[finalI]);
                            break;
                        case "leak_current":
                            sb.append("\",\"leak_current\":\"").append(finalVo.getLeakCurrent()[finalI]);
                            break;
                        case "energy_q":
                            sb.append("\",\"energy_q\":\"").append(finalVo.getEnergyQ()[finalI]);
                            break;
                        case "power_p":
                            sb.append("\",\"power_p\":\"").append(finalVo.getPowerP()[finalI]);
                            break;
                        case "tilt":
                            sb.append("\",\"tilt\":\"").append(finalVo.getTilt()[finalI]);
                            break;
                        case "switch":
                            sb.append("\",\"switch\":\"").append(switchMap.get(finalI));
                            break;
                        default:

                            break;
                    }
                });
                sb.append("\"},");
            }

            //            sb.append("{\"line_id\":\"")
//                    .append(i)
//                    .append("\",\"frequency\":\"")
//                    .append(vo.getFrequency()[i])
//                    .append("\",\"voltage\":\"")
//                    .append(vo.getVoltage()[i])
//                    .append("\",\"current\":\"")
//                    .append(vo.getCurrent()[i])
//                    .append("\",\"energy_p\":\"")
//                    .append(vo.getEnergyP()[i])
//                    .append("\",\"power_p\":\"")
//                    .append(vo.getPowerP()[i])
//                    .append("\"},");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("],\"time\":\"").append(simpleDateFormat.format(new Date())).append("\"}");
        MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setQos(1);
        mqttMessage.setPayload(sb.toString().getBytes());
        client.publish(topic, mqttMessage);
        sb = new StringBuffer();
        publishCount++;
    }
}
