package com.medora.appointment.controller;

import com.medora.appointment.api.query.AppointmentListQuery;
import com.medora.appointment.api.request.CancelAppointmentRequest;
import com.medora.appointment.api.request.CreateAppointmentRequest;
import com.medora.appointment.api.response.AppointmentCancelResponse;
import com.medora.appointment.api.response.AppointmentDetailResponse;
import com.medora.appointment.api.response.AppointmentItem;
import com.medora.appointment.api.response.AppointmentResponse;
import com.medora.appointment.service.AppointmentCommandService;
import com.medora.appointment.service.AppointmentQueryService;
import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "预约挂号", description = "创建、取消、查询预约")
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentCommandService appointmentCommandService;
    private final AppointmentQueryService appointmentQueryService;

    public AppointmentController(AppointmentCommandService appointmentCommandService,
                                 AppointmentQueryService appointmentQueryService) {
        this.appointmentCommandService = appointmentCommandService;
        this.appointmentQueryService = appointmentQueryService;
    }

    @Operation(summary = "创建预约", description = "支持幂等键 Idempotency-Key 防止重复提交")
    @PostMapping
    public ApiResponse<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request,
                                                   @Parameter(description = "幂等键，用于防止重复提交")
                                                   @RequestHeader(value = "Idempotency-Key", required = false)
                                                   String idempotencyKey) {
        return ApiResponse.success(appointmentCommandService.create(request, idempotencyKey));
    }

    @Operation(summary = "取消预约")
    @PostMapping("/{appointmentId}/cancel")
    public ApiResponse<AppointmentCancelResponse> cancel(
            @Parameter(description = "预约 ID") @PathVariable String appointmentId,
            @Valid @RequestBody CancelAppointmentRequest request) {
        return ApiResponse.success(appointmentCommandService.cancel(appointmentId, request));
    }

    @Operation(summary = "查询当前用户的预约列表")
    @GetMapping
    public ApiResponse<PageResponse<AppointmentItem>> listMine(
            @Parameter(description = "预约状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "出诊日期起") @RequestParam(required = false) String scheduleDateFrom,
            @Parameter(description = "出诊日期止") @RequestParam(required = false) String scheduleDateTo,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                appointmentQueryService.listMine(
                        new AppointmentListQuery(status, scheduleDateFrom, scheduleDateTo, pageNo, pageSize)
                )
        );
    }

    @Operation(summary = "查询预约详情")
    @GetMapping("/{appointmentId}")
    public ApiResponse<AppointmentDetailResponse> getMine(
            @Parameter(description = "预约 ID") @PathVariable String appointmentId) {
        return ApiResponse.success(appointmentQueryService.getMine(appointmentId));
    }
}
