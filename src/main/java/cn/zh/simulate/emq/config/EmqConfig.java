package cn.zh.simulate.emq.config;

import cn.zh.simulate.emq.entity.DeviceInfo;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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

    private int publishCount = 1;

    /**
     * 开关map 最新的开关数据
     */
    Map<Integer, String> switchMap = new HashMap<>();
    /**
     * 请求开关map数据
     */
    Map switchReqMap = new HashMap();

    StringBuffer buffer = new StringBuffer();

    public void conn(Map<String, List<String>> map, String clientId, DeviceInfo deviceInfo, String type) throws MqttException {
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
                buildDataPublish(topic, content, message, deviceInfo);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                publishCount++;
                System.out.println("deliveryComplete....");
            }
        });
        System.out.println("连接状态：" + client.isConnected());
    }

    /**
     * 处理响应数据JSON发布信息
     *
     * @param topic   主题
     * @param content 内容
     * @param message 消息对象
     */
    public void buildDataPublish(String topic, String content, MqttMessage message, DeviceInfo deviceInfo) throws MqttException {
        String[] topicAttr = topic.split("/");

        // 处理数据
        JSONObject jsonObject = JSONObject.parseObject(content);
        String method = jsonObject.get("method").toString();
        String srcMsgId = jsonObject.get("msgid").toString();
        String msgId = "C" + publishCount;
        String data = jsonObject.get("data").toString();
        //拿到data数据
        JSONObject object = JSONObject.parseObject(data);
        String time = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        buffer.append("{\"version\":\"2.0.0\",\"srcmsgid\":\"").append(srcMsgId).append("\",\"msgid\":\"").append(msgId).append("\",\"device\":\"").append(topicAttr[topicAttr.length - 2]).append("\",\"status\":\"OK\",");
        switch (method) {
            case "act.do":
                buffer.append("\"data\":{},");
                break;
            case "tag.get":
                String lineId = object.get("line_id").toString();
                String tags = object.get("tags").toString();
                JSONArray jsonArray = JSONArray.parseArray(tags);
                buffer.append("\"data\":{").append("\"line_id\":").append(lineId);

                for (int i = 0; i < jsonArray.size(); i++) {
                    switch (jsonArray.get(i).toString()) {
                        case "voltage":
                            buffer.append(",\"voltage\":").append(deviceInfo.getVoltage());
                            break;
                        case "power_q":
                            buffer.append(",\"power_q:\":").append(deviceInfo.getPowerQ());
                            break;
                        case "current":
                            buffer.append(",\"current\":").append(deviceInfo.getCurrent());
                            break;
                        case "power_s":
                            buffer.append(",\"power_s\":").append(deviceInfo.getPowerS());
                            break;
                        case "frequency":
                            buffer.append(",\"frequency\":").append(deviceInfo.getFrequency());
                            break;
                        case "energy_p":
                            buffer.append(",\"energy_p\":").append(deviceInfo.getEnergyP());
                            break;
                        case "leak_current":
                            buffer.append(",\"leak_current\":").append(deviceInfo.getLeakCurrent());
                            break;
                        case "energy_q":
                            buffer.append(",\"energy_q\":").append(deviceInfo.getEnergyQ());
                            break;
                        case "power_p":
                            buffer.append(",\"power_p\":").append(deviceInfo.getPowerP());
                            break;
                        case "switch":
                            buffer.append(",\"switch\":").append(switchMap.get(lineId));
                            break;
                        default:
                            break;
                    }
                }
                break;
            default:
                break;
        }
        buffer.append("\"time\":\"").append(time).append("\"}");

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
                    sb.append("{\"switch\":\"").append(switchReqMap.get(i)).append("\",\"line_id\":").append(i).append("},");
                }
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("],\"time\":\"").append(time).append("\",\"device\":\"").append(topicAttr[topicAttr.length - 2]).append("\",\"msgid\":\"").append(msgId).append("\"}");
            message.setPayload(sb.toString().getBytes());
            String reportTopic = topic.substring(0, topic.length() - 3).concat("report");
            client.publish(reportTopic, message);
            sb = new StringBuffer();
        }
    }

//    public void report(String topic,DeviceInfo vo) throws MqttException {
//        StringBuffer sb = new StringBuffer();
//        deviceInfo deviceInfo = new deviceInfo();
//        sb.append("{\"device\":\"010301000001\",\"version\":\"2.0.0\",\"msgid\":\"C17\",\"data\":[");
//        for (int i = 0; i < deviceInfo.getSwitchNum(); i++) {
//            sb.append("{\"line_id\":\"")
//                    .append(i)
//                    .append("\",\"frequency\":\"")
//                    .append(vo.getFrequency())
//                    .append("\",\"voltage\":\"")
//                    .append(vo.getVoltage())
//                    .append("\",\"current\":\"")
//                    .append(vo.getCurrent())
//                    .append("\",\"energy_p\":\"")
//                    .append(vo.getEnergyP())
//                    .append("\",\"power_p\":\"")
//                    .append(vo.getPowerP())
//                    .append("\"},");
//        }
//        sb.deleteCharAt(sb.length() - 1);
//        sb.append("],\"time\":\"20210616110708\"}");
//        MqttMessage mqttMessage = new MqttMessage();
//        mqttMessage.setQos(1);
//        mqttMessage.setPayload(sb.toString().getBytes());
//        client.publish(topic, mqttMessage);
//        sb = new StringBuffer();
//    }
}
