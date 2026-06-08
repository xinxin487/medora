package com.medora.masterdata.controller;

import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import com.medora.masterdata.api.query.ScheduleSlotListQuery;
import com.medora.masterdata.api.response.ScheduleSlotItem;
import com.medora.masterdata.service.ScheduleSlotQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "号源", description = "排班号源查询")
@RestController
@RequestMapping("/api/v1/schedule-slots")
public class ScheduleSlotController {

    private final ScheduleSlotQueryService scheduleSlotQueryService;

    public ScheduleSlotController(ScheduleSlotQueryService scheduleSlotQueryService) {
        this.scheduleSlotQueryService = scheduleSlotQueryService;
    }

    @Operation(summary = "查询号源列表")
    @GetMapping
    public ApiResponse<PageResponse<ScheduleSlotItem>> list(
            @Parameter(description = "科室 ID") @RequestParam(required = false) String departmentId,
            @Parameter(description = "医生 ID") @RequestParam(required = false) String doctorId,
            @Parameter(description = "出诊日期 yyyy-MM-dd", required = true) @RequestParam String scheduleDate,
            @Parameter(description = "时段 AM/PM") @RequestParam(required = false) String periodCode,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                scheduleSlotQueryService.list(
                        new ScheduleSlotListQuery(
                                departmentId,
                                doctorId,
                                scheduleDate,
                                periodCode,
                                status,
                                pageNo,
                                pageSize
                        )
                )
        );
    }
}
