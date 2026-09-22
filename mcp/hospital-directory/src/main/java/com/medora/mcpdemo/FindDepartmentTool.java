package com.medora.mcpdemo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.List;
@Component public class FindDepartmentTool {
 private static final String DISCLAIMER="本建议不构成诊断，不能替代医生面诊。"; private final List<Department> departments;
 public FindDepartmentTool(){try(InputStream input=getClass().getResourceAsStream("/departments.json")){if(input==null)throw new IllegalStateException("departments fixture missing");departments=new ObjectMapper().readValue(input,new TypeReference<>(){});}catch(Exception exception){throw new IllegalStateException("departments fixture unreadable");}}
 public Result findDepartment(String symptoms){if(symptoms==null||symptoms.isBlank())throw new IllegalArgumentException("symptoms is required");String normalized=symptoms.toLowerCase();Department match=departments.stream().filter(department->department.keywords().stream().anyMatch(normalized::contains)).findFirst().orElseGet(()->departments.stream().filter(department->department.keywords().isEmpty()).findFirst().orElseThrow());return new Result(match.departmentCode(),match.departmentName(),match.reason(),DISCLAIMER);}
 public record Result(String departmentCode,String departmentName,String reason,String disclaimer){} private record Department(String departmentCode,String departmentName,List<String> keywords,String reason){}
}
