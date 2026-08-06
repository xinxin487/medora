package com.medora.masterdata.controller;

import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import com.medora.masterdata.api.query.DoctorListQuery;
import com.medora.masterdata.api.response.DoctorDetailResponse;
import com.medora.masterdata.api.response.DoctorItem;
import com.medora.masterdata.service.DoctorQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "医生", description = "医生列表与详情查询")
@RestController
@RequestMapping("/api/v1/doctors")
public class DoctorController {

    private final DoctorQueryService doctorQueryService;

    public DoctorController(DoctorQueryService doctorQueryService) {
        this.doctorQueryService = doctorQueryService;
    }

    @Operation(summary = "查询医生列表")
    @GetMapping
    public ApiResponse<PageResponse<DoctorItem>> list(
            @Parameter(description = "科室 ID 筛选") @RequestParam(required = false) String departmentId,
            @Parameter(description = "关键字搜索") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                doctorQueryService.list(new DoctorListQuery(departmentId, keyword, status, pageNo, pageSize))
        );
    }

    @Operation(summary = "查询医生详情")
    @GetMapping("/{doctorId}")
    public ApiResponse<DoctorDetailResponse> detail(
            @Parameter(description = "医生 ID") @PathVariable String doctorId) {
        return ApiResponse.success(doctorQueryService.getDetail(doctorId));
    }
}
