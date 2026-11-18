package com.checa.crm.pojo.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class SysUser {
    private String userId;
    private String deptId;
    private String username;
    private String password;
    private String realName;
    private String gender;
    private String phone;
    private String email;
    private String position;
    private String status;
    private LocalDate hireDate;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
    public String getDeptId(){return deptId;} public void setDeptId(String v){deptId=v;}
    public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getPassword(){return password;} public void setPassword(String v){password=v;}
    public String getRealName(){return realName;} public void setRealName(String v){realName=v;}
    public String getGender(){return gender;} public void setGender(String v){gender=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getPosition(){return position;} public void setPosition(String v){position=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public LocalDate getHireDate(){return hireDate;} public void setHireDate(LocalDate v){hireDate=v;}
    public Integer getDeleted(){return deleted;} public void setDeleted(Integer v){deleted=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
    public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
}
