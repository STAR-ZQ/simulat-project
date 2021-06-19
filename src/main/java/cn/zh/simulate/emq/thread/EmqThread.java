//package cn.zh.simulate.emq.thread;
//
//import cn.zh.simulate.emq.config.SimulateYmlConfig;
//import cn.zh.simulate.emq.entity.DeviceInfo;
//import lombok.SneakyThrows;
//
//import java.util.List;
//
//public class EmqThread implements Runnable {
//    private List<SimulateYmlConfig.Devices.Info> infoList;
//
//    public List<SimulateYmlConfig.Devices.Info> getInfoList() {
//        return infoList;
//    }
//
//    public void setInfoList(List<SimulateYmlConfig.Devices.Info> infoList) {
//        this.infoList = infoList;
//    }
//
//    @Override
//    public void run() {
//        infoList.forEach(e -> {
//            map.put(e.getId(), e.getSlaves());
//            DeviceInfo deviceInfo = new DeviceInfo(e.getNum(), e.getSlaveNum());
//            emqConfig.conn(map, e.getId(), deviceInfo, ds.getType());
//        });
//    }
//}
