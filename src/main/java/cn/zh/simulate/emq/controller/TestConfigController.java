package cn.zh.simulate.emq.controller;

import cn.zh.simulate.emq.config.EmqConfig;
import cn.zh.simulate.emq.config.SimulateYmlConfig;
import cn.zh.simulate.emq.entity.DeviceInfo;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.SneakyThrows;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author ZQ
 */
@RestController
public class TestConfigController {
    @Autowired
    SimulateYmlConfig config;
    @Autowired
    EmqConfig emqConfig;
    /**
     * 启用多线程
     */
    static CountDownLatch countDownLatch = new CountDownLatch(10);
    static ExecutorService threadPool = new ThreadPoolExecutor(
            5,
            5,
            1,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());


    private static String clientId;
    /**
     * 主开关数量
     */
    private static Integer num;
    /**
     * 从开关数量
     */
    private static Integer sNum;

    @RequestMapping("/test")
    public Object testConfig() {
        //配置文件对象类
        List<SimulateYmlConfig.Devices> devices = config.getDevices();
        //type:info(list)
        Map<String, List<SimulateYmlConfig.Devices.Info>> devicesMap = devices.stream().collect(Collectors.toMap(SimulateYmlConfig.Devices::getType, SimulateYmlConfig.Devices::getInfo));
        System.out.println(JSON.toJSONString(devicesMap));

        List<SimulateYmlConfig.Devices.Info> infoList = Lists.newArrayList();
        // id:sid<list>
        Map<String, List<String>> map = new HashMap<>();

        for (SimulateYmlConfig.Devices ds : devices) {
            infoList = devicesMap.get(ds.getType());
            infoList.forEach(e -> {
                map.put(e.getId(), e.getSlaves());
//                threadPool.execute(() -> {
                System.out.println("----线程" + e.getId() + "启动时间为" + System.currentTimeMillis());
                //后续在此处进行业务处理
                DeviceInfo deviceInfo = new DeviceInfo(e.getNum(), e.getSlaveNum());
                try {
                    emqConfig.conn(map, e.getId(), deviceInfo, ds.getType());
                    while (true){
                        emqConfig.testWhile(e.getId());
                    }
//                    emqConfig.report(e.getId());
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
//                });
            });
        }

//        while (true){
////            Thread.sleep(2000);
//        }

//        System.out.println("============" + Thread.activeCount());        //关闭线程处理
//        try {
//            countDownLatch.await();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        //关闭线程池
//        threadPool.shutdown();
        return map;
    }

    public static void main(String[] args) {
//        //配置文件对象类
//        List<SimulateYmlConfig.Devices> devices = config.getDevices();
//        //type:info(list)
//        Map<String, List<SimulateYmlConfig.Devices.Info>> devicesMap = devices.stream().collect(Collectors.toMap(SimulateYmlConfig.Devices::getType, SimulateYmlConfig.Devices::getInfo));
//        System.out.println(JSON.toJSONString(devicesMap));
//
//        List<SimulateYmlConfig.Devices.Info> infoList = Lists.newArrayList();
//        // id:sid<list>
//        Map<String, List<String>> map = new HashMap<>();
//
//        for (SimulateYmlConfig.Devices ds : devices) {
//            infoList = devicesMap.get(ds.getType());
//            infoList.forEach(e -> {
//                map.put(e.getId(), e.getSlaves());
////                threadPool.execute(() -> {
//                System.out.println("----线程" + e.getId() + "启动时间为" + System.currentTimeMillis());
//                //后续在此处进行业务处理
//                DeviceInfo deviceInfo = new DeviceInfo(e.getNum(), e.getSlaveNum());
//                try {
//                    emqConfig.conn(map, e.getId(), deviceInfo, ds.getType());
//                    emqConfig.report(e.getId());
//                } catch (Exception ex) {
//                    ex.printStackTrace();
//                } finally {
//                    countDownLatch.countDown();
//                }
////                });
//            });
//        }
////        //关闭线程处理
////        System.out.println("============"+Thread.activeCount());
////        try {
////            countDownLatch.await();
////        } catch (InterruptedException e) {
////            e.printStackTrace();
////        }
////        //关闭线程池
////        threadPool.shutdown();
    }
}
