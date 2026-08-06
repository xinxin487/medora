package com.medora.agent.tool.tools;

import com.medora.agent.tool.ToolArguments;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.masterdata.api.query.DepartmentListQuery;
import com.medora.masterdata.api.query.DoctorListQuery;
import com.medora.masterdata.service.DepartmentQueryService;
import com.medora.masterdata.service.DoctorQueryService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryDoctorsTool implements ToolDefinition {

    private final DoctorQueryService doctorQueryService;
    private final DepartmentQueryService departmentQueryService;

    public QueryDoctorsTool(DoctorQueryService doctorQueryService,
                            DepartmentQueryService departmentQueryService) {
        this.doctorQueryService = doctorQueryService;
        this.departmentQueryService = departmentQueryService;
    }

    @Override
    public String name() {
        return ToolNames.QUERY_DOCTORS;
    }

    @Override
    public String description() {
        return "查询医生";
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String departmentId = ToolArguments.stringValue(arguments, "departmentId");
        if (departmentId == null) {
            departmentId = resolveDepartmentId(ToolArguments.stringValue(arguments, "departmentKeyword"));
        }
        var result = doctorQueryService.list(new DoctorListQuery(
                departmentId,
                ToolArguments.stringValue(arguments, "keyword"),
                "ACTIVE",
                1,
                ToolArguments.intValue(arguments, "pageSize", 5)
        ));
        return ToolExecutionResult.success(name(), result, "已查询到 " + result.list().size() + " 位医生。");
    }

    private String resolveDepartmentId(String departmentKeyword) {
        if (departmentKeyword == null) {
            return null;
        }
        var departments = departmentQueryService.list(new DepartmentListQuery(departmentKeyword, "ACTIVE", 1, 1));
        return departments.list().isEmpty() ? null : departments.list().getFirst().departmentId();
    }
}
