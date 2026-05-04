package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cinema {

    @JsonProperty("_key")
    private String key;

    @JsonProperty("_id")
    private String id;

    private String name;
    private int capacity;
    private String type;

    @JsonProperty("totalRows")
    public int getTotalRows() {
        return switch ((type == null ? "" : type).toLowerCase()) {
            case "vip" -> 5;
            case "imax" -> 10;
            default -> capacity >= 60 ? 10 : 2;
        };
    }

    @JsonProperty("totalCols")
    public int getTotalCols() {
        return switch ((type == null ? "" : type).toLowerCase()) {
            case "vip" -> 4;
            case "imax" -> 8;
            default -> capacity >= 60 ? 6 : 3;
        };
    }
}
