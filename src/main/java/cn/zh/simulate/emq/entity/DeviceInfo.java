package cn.zh.simulate.emq.entity;

import lombok.Data;

import java.math.BigDecimal;
@Data
public class DeviceInfo {
    /**
     * 开关状态
     */
    private String switchStatus;
    /**
     * 电压
     */
    private Integer voltage;
    /**
     * 电流
     */
    private Integer current;
    /**
     * 漏电电流
     */
    private Integer leakCurrent;
    /**
     * 频率
     */
    private BigDecimal frequency;
    /**
     * 有功功率
     */
    private Integer powerP;
    /**
     * 无功功率
     */
    private Integer powerQ;
    /**
     * 视在功率
     */
    private Integer powerS;
    /**
     * 有功电能
     */
    private BigDecimal energyP;
    /**
     * 无功电能
     */
    private BigDecimal energyQ;
    /**
     * 倾斜度
     */
    private Integer tilt;
    /**
     * 线路id
     */
    private Integer lineId;
    /**
     * 定时时间
     */
    private Integer scheduleTime = 600;

    private Integer num ;

    private Integer sNum;

    public DeviceInfo(Integer num,Integer sNum){
        this.num = num;
        this.sNum = sNum;
    }

    public int random(double max,double min){
        return (int)(Math.random()*(max-min)+min);
    }

    public DeviceInfo(BigDecimal energyP, BigDecimal energyQ) {
//        this.switchStatus = switchStatus;
        this.voltage = random(253,198);
        this.current = random(2000 , 1800);
        this.leakCurrent = random(30,0);
        this.frequency = BigDecimal.valueOf(BigDecimal.valueOf((long)random(50.2,49.8)).doubleValue()).setScale(2, BigDecimal.ROUND_HALF_UP);
        this.powerP = (int) ((this.voltage * this.current) * 0.9);
        this.powerQ = (int) ((this.voltage * this.current) * 0.1);
        this.powerS = this.voltage * this.current;
        this.energyP = energyP.add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.9))).setScale(2,BigDecimal.ROUND_HALF_UP);
        this.energyQ = energyQ.add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.1))).setScale(2,BigDecimal.ROUND_HALF_UP);
        this.tilt = random(15,0);
//        this.lineId = lineId;
    }
    public DeviceInfo() {
//        this.switchStatus = switchStatus;
        this.voltage = random(253,198);
        this.current = random(2000 , 1800);
        this.leakCurrent = random(30,0);
        this.frequency = BigDecimal.valueOf(BigDecimal.valueOf((long)random(50.2,49.8)).doubleValue()).setScale(2, BigDecimal.ROUND_HALF_UP);
        this.powerP = (int) ((this.voltage * this.current) * 0.9);
        this.powerQ = (int) ((this.voltage * this.current) * 0.1);
        this.powerS = this.voltage * this.current;
//        this.energyP = energyP.add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.9))).setScale(2,BigDecimal.ROUND_HALF_UP);
//        this.energyQ = energyQ.add(BigDecimal.valueOf((0.44 / (3600 / scheduleTime) * 0.1))).setScale(2,BigDecimal.ROUND_HALF_UP);
        this.tilt = random(15,0);
//        this.lineId = random(3,0);
    }
}
