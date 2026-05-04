package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @JsonProperty("_key")
    private String key;

    @JsonProperty("_id")
    private String id;

    private String name;
    private String username;
    private String email;
    private String password;
    private String phone;
    private double totalSpent;
    private String memberRank;  // "normal" | "vip" | "premium"
    private String createdAt;
}
