package com.medora.masterdata.controller;

import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import com.medora.masterdata.api.query.DepartmentListQuery;
import com.medora.masterdata.api.response.DepartmentItem;
import com.medora.masterdata.service.DepartmentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "科室", description = "科室列表查询")
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentQueryService departmentQueryService;

    public DepartmentController(DepartmentQueryService departmentQueryService) {
        this.departmentQueryService = departmentQueryService;
    }

    @Operation(summary = "查询科室列表")
    @GetMapping
    public ApiResponse<PageResponse<DepartmentItem>> list(
            @Parameter(description = "关键字搜索") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                departmentQueryService.list(new DepartmentListQuery(keyword, status, pageNo, pageSize))
        );
    }
}
