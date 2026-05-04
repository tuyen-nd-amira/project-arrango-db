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
    private int totalRows;
    private int totalCols;
    private String address;
}
