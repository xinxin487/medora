package com.medora.mcpdemo;
import org.springframework.stereotype.Component;
@Component public class FindDepartmentTool {
 public Result findDepartment(String symptoms){if(symptoms==null||symptoms.isBlank())throw new IllegalArgumentException("symptoms is required");String s=symptoms.toLowerCase(); if(s.contains("咳")||s.contains("呼吸"))return result("RESP","呼吸内科","咳嗽或呼吸不适可先由呼吸内科评估");if(s.contains("皮")||s.contains("疹"))return result("DERM","皮肤科","皮疹可先由皮肤科评估");return result("GENERAL","普通门诊","症状信息不足时建议先到普通门诊分诊");}
 private Result result(String c,String n,String r){return new Result(c,n,r,"本建议不构成诊断，不能替代医生面诊。");} public record Result(String departmentCode,String departmentName,String reason,String disclaimer){}
}
