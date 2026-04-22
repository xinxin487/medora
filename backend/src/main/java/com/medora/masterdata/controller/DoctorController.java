package com.medora.masterdata.controller;

import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import com.medora.masterdata.api.query.DoctorListQuery;
import com.medora.masterdata.api.response.DoctorDetailResponse;
import com.medora.masterdata.api.response.DoctorItem;
import com.medora.masterdata.service.DoctorQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctors")
public class DoctorController {

    private final DoctorQueryService doctorQueryService;

    public DoctorController(DoctorQueryService doctorQueryService) {
        this.doctorQueryService = doctorQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DoctorItem>> list(
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                doctorQueryService.list(new DoctorListQuery(departmentId, keyword, status, pageNo, pageSize))
        );
    }

    @GetMapping("/{doctorId}")
    public ApiResponse<DoctorDetailResponse> detail(@PathVariable String doctorId) {
        return ApiResponse.success(doctorQueryService.getDetail(doctorId));
    }
}
