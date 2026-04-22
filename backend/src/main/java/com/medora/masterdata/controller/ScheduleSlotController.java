package com.medora.masterdata.controller;

import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import com.medora.masterdata.api.query.ScheduleSlotListQuery;
import com.medora.masterdata.api.response.ScheduleSlotItem;
import com.medora.masterdata.service.ScheduleSlotQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule-slots")
public class ScheduleSlotController {

    private final ScheduleSlotQueryService scheduleSlotQueryService;

    public ScheduleSlotController(ScheduleSlotQueryService scheduleSlotQueryService) {
        this.scheduleSlotQueryService = scheduleSlotQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ScheduleSlotItem>> list(
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String doctorId,
            @RequestParam String scheduleDate,
            @RequestParam(required = false) String periodCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
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
