package com.medora.agent.tool.tools;

import com.medora.agent.tool.ToolArguments;
import com.medora.agent.tool.ToolDefinition;
import com.medora.agent.tool.ToolExecutionContext;
import com.medora.agent.tool.ToolExecutionResult;
import com.medora.agent.tool.ToolNames;
import com.medora.common.exception.BusinessException;
import com.medora.masterdata.api.query.DepartmentListQuery;
import com.medora.masterdata.api.query.DoctorListQuery;
import com.medora.masterdata.api.query.ScheduleSlotListQuery;
import com.medora.masterdata.service.DepartmentQueryService;
import com.medora.masterdata.service.DoctorQueryService;
import com.medora.masterdata.service.ScheduleSlotQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryScheduleSlotsTool implements ToolDefinition {

    private final ScheduleSlotQueryService scheduleSlotQueryService;
    private final DepartmentQueryService departmentQueryService;
    private final DoctorQueryService doctorQueryService;

    public QueryScheduleSlotsTool(ScheduleSlotQueryService scheduleSlotQueryService,
                                  DepartmentQueryService departmentQueryService,
                                  DoctorQueryService doctorQueryService) {
        this.scheduleSlotQueryService = scheduleSlotQueryService;
        this.departmentQueryService = departmentQueryService;
        this.doctorQueryService = doctorQueryService;
    }

    @Override
    public String name() {
        return ToolNames.QUERY_SCHEDULE_SLOTS;
    }

    @Override
    public String description() {
        return "查询可预约号源";
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String scheduleDate = ToolArguments.stringValue(arguments, "scheduleDate");
        if (scheduleDate == null) {
            throw new BusinessException("AGENT_TOOL_ARGUMENT_MISSING", HttpStatus.UNPROCESSABLE_ENTITY,
                    "查询号源需要明确日期");
        }
        String departmentId = ToolArguments.stringValue(arguments, "departmentId");
        if (departmentId == null) {
            departmentId = resolveDepartmentId(ToolArguments.stringValue(arguments, "departmentKeyword"));
        }
        String doctorId = ToolArguments.stringValue(arguments, "doctorId");
        if (doctorId == null) {
            doctorId = resolveDoctorId(ToolArguments.stringValue(arguments, "doctorKeyword"), departmentId);
        }
        var result = scheduleSlotQueryService.list(new ScheduleSlotListQuery(
                departmentId,
                doctorId,
                scheduleDate,
                normalizePeriodCode(ToolArguments.stringValue(arguments, "periodCode")),
                "OPEN",
                1,
                ToolArguments.intValue(arguments, "pageSize", 5)
        ));
        return ToolExecutionResult.success(name(), result, "已查询到 " + result.list().size() + " 个可预约号源。");
    }

    private String resolveDepartmentId(String departmentKeyword) {
        if (departmentKeyword == null) {
            return null;
        }
        var departments = departmentQueryService.list(new DepartmentListQuery(departmentKeyword, "ACTIVE", 1, 1));
        return departments.list().isEmpty() ? null : departments.list().getFirst().departmentId();
    }

    private String resolveDoctorId(String doctorKeyword, String departmentId) {
        if (doctorKeyword == null) {
            return null;
        }
        var doctors = doctorQueryService.list(new DoctorListQuery(departmentId, doctorKeyword, "ACTIVE", 1, 1));
        return doctors.list().isEmpty() ? null : doctors.list().getFirst().doctorId();
    }

    private String normalizePeriodCode(String periodCode) {
        if (periodCode == null) {
            return null;
        }
        String normalized = periodCode.trim().toUpperCase();
        if ("AM".equals(normalized)
                || "MORNING".equals(normalized)
                || "上午".equals(periodCode)
                || "早上".equals(periodCode)) {
            return "AM";
        }
        if ("PM".equals(normalized)
                || "AFTERNOON".equals(normalized)
                || "EVENING".equals(normalized)
                || "下午".equals(periodCode)
                || "晚上".equals(periodCode)) {
            return "PM";
        }
        return periodCode;
    }
}
