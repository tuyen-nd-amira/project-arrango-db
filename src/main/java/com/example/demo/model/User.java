package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonProperty("full_name")
    @JsonAlias("name")
    private String name;
    private String username;
    private String email;
    private String password;
    private String phone;
    private double totalSpent;
    @JsonProperty("role")
    @JsonAlias("memberRank")
    private String memberRank;
    private String createdAt;
}
