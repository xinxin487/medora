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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping
    public ApiResponse<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request,
                                                   @RequestHeader(value = "Idempotency-Key", required = false)
                                                   String idempotencyKey) {
        return ApiResponse.success(appointmentCommandService.create(request, idempotencyKey));
    }

    @PostMapping("/{appointmentId}/cancel")
    public ApiResponse<AppointmentCancelResponse> cancel(@PathVariable String appointmentId,
                                                         @Valid @RequestBody CancelAppointmentRequest request) {
        return ApiResponse.success(appointmentCommandService.cancel(appointmentId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<AppointmentItem>> listMine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scheduleDateFrom,
            @RequestParam(required = false) String scheduleDateTo,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                appointmentQueryService.listMine(
                        new AppointmentListQuery(status, scheduleDateFrom, scheduleDateTo, pageNo, pageSize)
                )
        );
    }

    @GetMapping("/{appointmentId}")
    public ApiResponse<AppointmentDetailResponse> getMine(@PathVariable String appointmentId) {
        return ApiResponse.success(appointmentQueryService.getMine(appointmentId));
    }
}
