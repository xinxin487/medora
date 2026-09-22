package com.medora.mcpdemo;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class FindDepartmentToolTest { @Test void mapsRespiratorySymptomsAndAlwaysAddsDisclaimer(){var result=new FindDepartmentTool().findDepartment("咳嗽呼吸困难");assertEquals("RESP",result.departmentCode());assertTrue(result.disclaimer().contains("不能替代"));} }
