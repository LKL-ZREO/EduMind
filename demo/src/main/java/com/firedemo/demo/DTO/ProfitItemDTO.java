package com.firedemo.demo.DTO;

public class ProfitItemDTO {
    private String station;
    private String name;
    private String total;
    private String hour;

    public ProfitItemDTO() {}

    public ProfitItemDTO(String station, String name, String total, String hour) {
        this.station = station;
        this.name = name;
        this.total = total;
        this.hour = hour;
    }

    // getters and setters
    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTotal() { return total; }
    public void setTotal(String total) { this.total = total; }
    public String getHour() { return hour; }
    public void setHour(String hour) { this.hour = hour; }
}