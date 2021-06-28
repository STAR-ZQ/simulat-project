package cn.zh.simulate.emq.controller;

import cn.zh.simulate.emq.config.EmqConfig;
import cn.zh.simulate.emq.config.SimulateYmlConfig;
import cn.zh.simulate.emq.entity.DeviceInfo;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                System.out.println("----线程" + e.getId() + "启动时间为" + System.currentTimeMillis());
                //后续在此处进行业务处理
                DeviceInfo deviceInfo = new DeviceInfo(e.getNum(), e.getSlaveNum(), new String[0]);
                try {
                    emqConfig.conn(map, e.getId(), deviceInfo, ds.getType());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }
        List<String> ids = Lists.newArrayList();
        for (String key : map.keySet()) {
            ids.add(key);
            ids.addAll(map.get(key));
//            for (String keys : map.get(key)) {
//                ids.add(keys);
//            }
        }
        System.out.println(JSON.toJSONString(ids));

        ids.forEach(e -> {
//            emqConfig.reportThread(e);
        });

        return map;
    }

}
