package com.medora.agent.tool.tools;

import com.medora.agent.tool.ToolArguments;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.masterdata.api.query.DepartmentListQuery;
import com.medora.masterdata.service.DepartmentQueryService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryDepartmentsTool implements ToolDefinition {

    private final DepartmentQueryService departmentQueryService;

    public QueryDepartmentsTool(DepartmentQueryService departmentQueryService) {
        this.departmentQueryService = departmentQueryService;
    }

    @Override
    public String name() {
        return ToolNames.QUERY_DEPARTMENTS;
    }

    @Override
    public String description() {
        return "查询可挂号科室";
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        var result = departmentQueryService.list(new DepartmentListQuery(
                ToolArguments.stringValue(arguments, "keyword"),
                "ACTIVE",
                1,
                ToolArguments.intValue(arguments, "pageSize", 5)
        ));
        return ToolExecutionResult.success(name(), result, "已查询到 " + result.list().size() + " 个科室。");
    }
}
