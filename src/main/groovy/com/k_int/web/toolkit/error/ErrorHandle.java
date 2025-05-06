package com.k_int.web.toolkit.error;

public class ErrorHandle {
  public int code;
  public String message;

  public ErrorHandle(String msg, int code) {
    this.code = code;
    this.message = msg;
  }

  public ErrorHandle(String msg) {
    this(msg, 500);
  }
};