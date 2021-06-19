package cn.zh.simulate.emq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.io.Serializable;
import java.util.List;

/**
 * @author ZQ
 */
@Configuration
@PropertySource(value = {"file:${spring.profiles.path}/config/simulate.yml"}, factory = YamlPropertyLoaderFactory.class, ignoreResourceNotFound = true)
@ConfigurationProperties("emq")
public class SimulateYmlConfig implements Serializable {
    private List<Devices> devices;

    public List<Devices> getDevices() {
        return devices;
    }

    public void setDevices(List<Devices> devices) {
        this.devices = devices;
    }

    @Override
    public String toString() {
        return "SimulateYmlConfig{" +
                "devices=" + devices +
                '}';
    }

    public static class Devices {
        /**
         * 设备类型
         */
        private String type;
        /**
         * 基础信息类
         */
        private List<Info> info;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Info> getInfo() {
            return info;
        }

        public void setInfo(List<Info> info) {
            this.info = info;
        }

        /**
         * 基础信息类
         */
        public static class Info {
            private String id;
            private List<String> slaves;
            private Integer num;
            private Integer slaveNum;

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public List<String> getSlaves() {
                return slaves;
            }

            public void setSlaves(List<String> slaves) {
                this.slaves = slaves;
            }

            public Integer getNum() {
                return num;
            }

            public void setNum(Integer num) {
                this.num = num;
            }

            public Integer getSlaveNum() {
                return slaveNum;
            }

            public void setSlaveNum(Integer slaveNum) {
                this.slaveNum = slaveNum;
            }

            @Override
            public String toString() {
                return "Info{" +
                        "id='" + id + '\'' +
                        ", slaves=" + slaves +
                        ", num=" + num +
                        ", slaveNum=" + slaveNum +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "SimulateYmlConfig{" +
                    "type='" + type + '\'' +
                    ", info=" + info +
                    '}';
        }
    }
}
