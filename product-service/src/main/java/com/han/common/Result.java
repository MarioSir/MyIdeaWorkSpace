package com.han.common;

public class Result {

    private Integer code;
    private String msg;
    private Object data;

    public Result() {
    }

    public Result(Integer code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public static Result SUCCESS(Object data){
      return new Result(0,"成功",data);
    }

    public static Result FILE(){
        return new Result(1,"失败",null);
    }

    public static Result FILE(String msg){
        return new Result(1,msg,null);
    }

    public static Result FILE(Integer code,String msg){
        return new Result(code,msg,null);
    }

    public static Result ERROR(){
        return new Result(9999,"系统异常",null);
    }
}
