package com.fc.v2.common.conf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 到期告知事项的执行档口配置（postdue 节）。
 * 出手的时辰照值守排成两段；夜里与法定节假日整段静默；
 * 旺季那半个月两段改照值守班表，不走平日档口。
 *
 * @author fuce
 */
@Component
@ConfigurationProperties(prefix = "postdue")
public class PostDueProperties {

    /** 平日两段档口，形如 09:00-11:30,14:00-17:00 */
    private String windows = "09:00-11:30,14:00-17:00";

    /** 旺季半月起（月-日），如 11-01 */
    private String peakStart = "11-01";

    /** 旺季半月止（月-日，含当日），如 11-15 */
    private String peakEnd = "11-15";

    /** 旺季值守班表两段，形如 08:30-12:00,13:30-18:30 */
    private String peakRoster = "08:30-12:00,13:30-18:30";

    /** 法定节假日（yyyy-MM-dd，逗号分隔），整段静默，以国务院公布为准 */
    private String holidays = "";

    /** 送达途径整季停摆开关：true 时该送达的一律转送不下去 */
    private Boolean channelSuspended = false;

    public String getWindows() {
        return windows;
    }

    public void setWindows(String windows) {
        this.windows = windows;
    }

    public String getPeakStart() {
        return peakStart;
    }

    public void setPeakStart(String peakStart) {
        this.peakStart = peakStart;
    }

    public String getPeakEnd() {
        return peakEnd;
    }

    public void setPeakEnd(String peakEnd) {
        this.peakEnd = peakEnd;
    }

    public String getPeakRoster() {
        return peakRoster;
    }

    public void setPeakRoster(String peakRoster) {
        this.peakRoster = peakRoster;
    }

    public String getHolidays() {
        return holidays;
    }

    public void setHolidays(String holidays) {
        this.holidays = holidays;
    }

    public Boolean getChannelSuspended() {
        return channelSuspended;
    }

    public void setChannelSuspended(Boolean channelSuspended) {
        this.channelSuspended = channelSuspended;
    }
}
