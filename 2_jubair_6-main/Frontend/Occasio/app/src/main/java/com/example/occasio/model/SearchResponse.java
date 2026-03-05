package com.example.occasio.model;
import com.example.occasio.R;

import java.util.List;

public class SearchResponse {
    private boolean success;
    private String message;
    private List<Friend> data;
    private int totalCount;

    public SearchResponse() {
        // Default constructor
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Friend> getData() {
        return data;
    }

    public void setData(List<Friend> data) {
        this.data = data;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
