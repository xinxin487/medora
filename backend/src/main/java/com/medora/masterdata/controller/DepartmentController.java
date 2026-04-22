package com.medora.masterdata.controller;

import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import com.medora.masterdata.api.query.DepartmentListQuery;
import com.medora.masterdata.api.response.DepartmentItem;
import com.medora.masterdata.service.DepartmentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentQueryService departmentQueryService;

    public DepartmentController(DepartmentQueryService departmentQueryService) {
        this.departmentQueryService = departmentQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DepartmentItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                departmentQueryService.list(new DepartmentListQuery(keyword, status, pageNo, pageSize))
        );
    }
}
