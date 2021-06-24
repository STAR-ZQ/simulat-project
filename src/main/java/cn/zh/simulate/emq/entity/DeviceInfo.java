package cn.zh.simulate.emq.entity;

import cn.zh.simulate.emq.config.RandomNumber;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;

@Data
public class DeviceInfo {
    /**
     * 开关状态
     */
    private String[] switchStatus;
    /**
     * 电压
     */
    private Integer[] voltage;
    /**
     * 电流
     */
    private Integer[] current;
    /**
     * 漏电电流
     */
    private Integer[] leakCurrent;
    /**
     * 频率
     */
    private BigDecimal[] frequency;
    /**
     * 有功功率
     */
    private Integer[] powerP;
    /**
     * 无功功率
     */
    private Integer[] powerQ;
    /**
     * 视在功率
     */
    private Integer[] powerS;
    /**
     * 有功电能
     */
    private BigDecimal[] energyP;
    /**
     * 无功电能
     */
    private BigDecimal[] energyQ;
    /**
     * 倾斜度
     */
    private Integer[] tilt;
    /**
     * 线路id
     */
    private Integer lineId;
    /**
     * 定时时间
     */
    private static Integer scheduleTime = 600;

    private Integer num;

    private SlaveInfo slaveInfo;


    @Data
    public static class SlaveInfo {
        /**
         * 开关状态
         */
        private String[] switchStatus;
        /**
         * 电压
         */
        private Integer[] voltage;
        /**
         * 电流
         */
        private Integer[] current;
        /**
         * 漏电电流
         */
        private Integer[] leakCurrent;
        /**
         * 频率
         */
        private BigDecimal[] frequency;
        /**
         * 有功功率
         */
        private Integer[] powerP;
        /**
         * 无功功率
         */
        private Integer[] powerQ;
        /**
         * 视在功率
         */
        private Integer[] powerS;
        /**
         * 有功电能
         */
        private BigDecimal[] energyP;
        /**
         * 无功电能
         */
        private BigDecimal[] energyQ;
        /**
         * 倾斜度
         */
        private Integer[] tilt;

        private Integer sNum;

        public SlaveInfo(Integer sNum) {
            this.sNum = sNum;
            this.switchStatus = new String[this.sNum];
            this.voltage = new Integer[this.sNum];
            this.current = new Integer[this.sNum];
            this.leakCurrent = new Integer[this.sNum];
            this.frequency = new BigDecimal[this.sNum];
            this.powerP = new Integer[this.sNum];
            this.powerQ = new Integer[this.sNum];
            this.powerS = new Integer[this.sNum];
            this.energyP = new BigDecimal[this.sNum];
            this.energyQ = new BigDecimal[this.sNum];
            this.tilt = new Integer[this.sNum];
            initSlaveData();
        }

        public void initSlaveData() {
            for (int i = 0; i < sNum; i++) {
                this.switchStatus[i] = "off";
                this.voltage[i] = RandomNumber.produceRateRandomNumber(198, 280, Lists.newArrayList(215, 225, 253), Lists.newArrayList(5.0, 85.0, 5.0, 5.0));
                this.current[i] = RandomNumber.produceRateRandomNumber(1500, 5000, Lists.newArrayList(1900, 2000), Lists.newArrayList(5.0, 90.0, 5.0));
                this.leakCurrent[i] = RandomNumber.produceRateRandomNumber(0, 300, Lists.newArrayList(5, 30), Lists.newArrayList(5.0, 90.0, 5.0));

                this.frequency[i] = BigDecimal.valueOf(RandomNumber.produceRateRandomNumber(49.8, 60.0, Lists.newArrayList(49.95, 50.05), Lists.newArrayList(5.0, 90.0, 5.0))).setScale(2, BigDecimal.ROUND_HALF_UP);

                this.powerP[i] = (int) ((this.voltage[i] * this.current[i]) * 0.9) / 1000;
                this.powerQ[i] = (int) ((this.voltage[i] * this.current[i]) * 0.1) / 1000;
                this.powerS[i] = this.voltage[i] * this.current[i];
                this.energyP[i] = BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.9)).setScale(2, BigDecimal.ROUND_HALF_UP);
                this.energyQ[i] = BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.1)).setScale(2, BigDecimal.ROUND_HALF_UP);
//                this.energyP[i] = energyP[i].add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.9))).setScale(2, BigDecimal.ROUND_HALF_UP);
//                this.energyQ[i] = energyQ[i].add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.1))).setScale(2, BigDecimal.ROUND_HALF_UP);
                this.tilt[i] = RandomNumber.produceRateRandomNumber(0, 90, Lists.newArrayList(2, 15), Lists.newArrayList(90.0, 7.0, 3.0));
            }
//        this.lineId = random(3,0);
        }
    }

    public DeviceInfo(Integer num, Integer sNum) {
        this.num = num;
        this.switchStatus = new String[this.num];
        this.voltage = new Integer[this.num];
        this.current = new Integer[this.num];
        this.leakCurrent = new Integer[this.num];
        this.frequency = new BigDecimal[this.num];
        this.powerP = new Integer[this.num];
        this.powerQ = new Integer[this.num];
        this.powerS = new Integer[this.num];
        this.energyP = new BigDecimal[this.num];
        this.energyQ = new BigDecimal[this.num];
        this.tilt = new Integer[this.num];
        this.slaveInfo = new SlaveInfo(sNum);
        initDeviceData();
    }

//    public void initArray(Integer num){
//        this.voltage = new Integer[num];
//        this.current[i]
//        this.leakCurrent[i]
//        this.frequency[i]
//
//
//    }

    public void initDeviceData() {
        for (int i = 0; i < num; i++) {
            this.switchStatus[i] = "off";
            this.voltage[i] = RandomNumber.produceRateRandomNumber(198, 280, Lists.newArrayList(215, 225, 253), Lists.newArrayList(5.0, 85.0, 5.0, 5.0));
            if ("off".equals(this.switchStatus[i])) {
                this.current[i] = 0;
            } else {
                this.current[i] = RandomNumber.produceRateRandomNumber(1500, 5000, Lists.newArrayList(1900, 2000), Lists.newArrayList(5.0, 90.0, 5.0));
            }
            this.leakCurrent[i] = RandomNumber.produceRateRandomNumber(0, 300, Lists.newArrayList(5, 30), Lists.newArrayList(5.0, 90.0, 5.0));

            this.frequency[i] = BigDecimal.valueOf(RandomNumber.produceRateRandomNumber(49.8, 60.0, Lists.newArrayList(49.95, 50.05), Lists.newArrayList(5.0, 90.0, 5.0))).setScale(2, BigDecimal.ROUND_HALF_UP);

            this.powerP[i] = (int) ((this.voltage[i] * this.current[i]) * 0.9) / 1000;
            this.powerQ[i] = (int) ((this.voltage[i] * this.current[i]) * 0.1) / 1000;
            this.powerS[i] = this.voltage[i] * this.current[i] / 1000;
//            this.energyP[i] = energyP[i].add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.9))).setScale(2, BigDecimal.ROUND_HALF_UP);
//            this.energyQ[i] = energyQ[i].add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.1))).setScale(2, BigDecimal.ROUND_HALF_UP);
            this.energyP[i] = BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.9)).setScale(2, BigDecimal.ROUND_HALF_UP);
            this.energyQ[i] = BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.1)).setScale(2, BigDecimal.ROUND_HALF_UP);
            this.tilt[i] = RandomNumber.produceRateRandomNumber(0, 90, Lists.newArrayList(2, 15), Lists.newArrayList(90.0, 7.0, 3.0));
        }
//        this.lineId = random(3,0);
    }

    //    public int random(double max, double min) {
//        return (int) (Math.random() * (max - min) + min);
//    }
//
//    public DeviceInfo(BigDecimal energyP, BigDecimal energyQ) {
////        this.switchStatus = switchStatus;
//        this.voltage = random(253, 198);
//        this.current = random(2000, 1800);
//        this.leakCurrent = random(30, 0);
//        this.frequency = BigDecimal.valueOf(BigDecimal.valueOf((long) random(50.2, 49.8)).doubleValue()).setScale(2, BigDecimal.ROUND_HALF_UP);
//        this.powerP = (int) ((this.voltage * this.current) * 0.9);
//        this.powerQ = (int) ((this.voltage * this.current) * 0.1);
//        this.powerS = this.voltage * this.current;
//        this.energyP = energyP.add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.9))).setScale(2, BigDecimal.ROUND_HALF_UP);
//        this.energyQ = energyQ.add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.1))).setScale(2, BigDecimal.ROUND_HALF_UP);
//        this.tilt = random(15, 0);
////        this.lineId = lineId;
//    }
//
    public DeviceInfo() {
////        this.switchStatus = switchStatus;
//        this.voltage = random(253, 198);
//        this.current = random(2000, 1800);
//        this.leakCurrent = random(30, 0);
//        this.frequency = BigDecimal.valueOf(BigDecimal.valueOf((long) random(50.2, 49.8)).doubleValue()).setScale(2, BigDecimal.ROUND_HALF_UP);
//        this.powerP = (int) ((this.voltage * this.current) * 0.9);
//        this.powerQ = (int) ((this.voltage * this.current) * 0.1);
//        this.powerS = this.voltage * this.current;
////        this.energyP = energyP.add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.9))).setScale(2,BigDecimal.ROUND_HALF_UP);
////        this.energyQ = energyQ.add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.1))).setScale(2,BigDecimal.ROUND_HALF_UP);
//        this.tilt = random(15, 0);
////        this.lineId = random(3,0);
    }

    public static void main(String[] args) {
        DeviceInfo deviceInfo = new DeviceInfo(6, 4);
        System.out.println(JSON.toJSONString(deviceInfo));
        System.out.println(JSON.toJSONString(deviceInfo.getSlaveInfo()));
    }
}
