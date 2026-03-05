package com.example.occasio.model;

import java.io.Serializable;

public class Organization implements Serializable {
    private Long id;
    private String orgName;
    private String email;
    private String password;
    private String description;

    // Default constructor
    public Organization() {}

    // Constructor with basic parameters
    public Organization(String orgName, String email, String password) {
        this.orgName = orgName;
        this.email = email;
        this.password = password;
    }

    // Constructor with ID (for existing organizations)
    public Organization(Long id, String orgName, String email, String password) {
        this.id = id;
        this.orgName = orgName;
        this.email = email;
        this.password = password;
    }

    // Full constructor with all fields
    public Organization(String orgName, String email, String password, String description) {
        this.orgName = orgName;
        this.email = email;
        this.password = password;
        this.description = description;
    }

    // Constructor with ID and all fields
    public Organization(Long id, String orgName, String email, String password, String description) {
        this.id = id;
        this.orgName = orgName;
        this.email = email;
        this.password = password;
        this.description = description;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Organization{" +
                "id=" + id +
                ", orgName='" + orgName + '\'' +
                ", email='" + email + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
