package com.medora.appointment.service;

import com.medora.appointment.api.response.AppointmentCancelResponse;
import com.medora.appointment.api.response.AppointmentDetailResponse;
import com.medora.appointment.api.response.AppointmentItem;
import com.medora.appointment.api.response.AppointmentResponse;
import com.medora.appointment.domain.AppointmentEntity;

final class AppointmentResponseMapper {

    private AppointmentResponseMapper() {
    }

    static AppointmentResponse toResponse(AppointmentEntity entity) {
        return new AppointmentResponse(
                entity.getPublicId().toString(),
                entity.getAppointmentNo(),
                entity.getStatus(),
                entity.getBookedAt(),
                new AppointmentResponse.Schedule(
                        entity.getSlot().getPublicId().toString(),
                        entity.getScheduleDate().toString(),
                        entity.getPeriodCode()
                ),
                new AppointmentResponse.Department(
                        entity.getDepartment().getPublicId().toString(),
                        entity.getDepartment().getName()
                ),
                new AppointmentResponse.Doctor(
                        entity.getDoctor().getPublicId().toString(),
                        entity.getDoctor().getName()
                )
        );
    }

    static AppointmentCancelResponse toCancelResponse(AppointmentEntity entity) {
        return new AppointmentCancelResponse(
                entity.getPublicId().toString(),
                entity.getStatus(),
                entity.getCancelledAt(),
                entity.getCancelReason()
        );
    }

    static AppointmentItem toItem(AppointmentEntity entity) {
        return new AppointmentItem(
                entity.getPublicId().toString(),
                entity.getAppointmentNo(),
                entity.getStatus(),
                entity.getBookedAt(),
                entity.getScheduleDate().toString(),
                entity.getPeriodCode(),
                entity.getDepartment().getName(),
                entity.getDoctor().getName(),
                entity.getSlot().getFee()
        );
    }

    static AppointmentDetailResponse toDetail(AppointmentEntity entity) {
        return new AppointmentDetailResponse(
                entity.getPublicId().toString(),
                entity.getAppointmentNo(),
                entity.getStatus(),
                entity.getBookedAt(),
                entity.getCancelledAt(),
                entity.getCancelReason(),
                entity.getRemark(),
                new AppointmentDetailResponse.Schedule(
                        entity.getSlot().getPublicId().toString(),
                        entity.getScheduleDate().toString(),
                        entity.getPeriodCode(),
                        entity.getSlot().getStartTime() == null ? null : entity.getSlot().getStartTime().toString(),
                        entity.getSlot().getEndTime() == null ? null : entity.getSlot().getEndTime().toString()
                ),
                new AppointmentDetailResponse.Department(
                        entity.getDepartment().getPublicId().toString(),
                        entity.getDepartment().getName()
                ),
                new AppointmentDetailResponse.Doctor(
                        entity.getDoctor().getPublicId().toString(),
                        entity.getDoctor().getName(),
                        entity.getDoctor().getTitle()
                ),
                entity.getSlot().getFee()
        );
    }
}
