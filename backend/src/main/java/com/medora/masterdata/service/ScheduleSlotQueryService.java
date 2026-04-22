package com.medora.masterdata.service;

import com.medora.common.api.PageResponse;
import com.medora.masterdata.api.query.ScheduleSlotListQuery;
import com.medora.masterdata.api.response.ScheduleSlotItem;
import com.medora.masterdata.domain.ScheduleSlotEntity;
import com.medora.masterdata.repository.ScheduleSlotRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class ScheduleSlotQueryService {

    private static final String DEFAULT_STATUS = "OPEN";

    private final ScheduleSlotRepository scheduleSlotRepository;

    public ScheduleSlotQueryService(ScheduleSlotRepository scheduleSlotRepository) {
        this.scheduleSlotRepository = scheduleSlotRepository;
    }

    public PageResponse<ScheduleSlotItem> list(ScheduleSlotListQuery query) {
        if (!StringUtils.hasText(query.scheduleDate())) {
            throw new IllegalArgumentException("scheduleDate is required");
        }

        Pageable pageable = PageRequest.of(normalizePageNo(query.pageNo()), normalizePageSize(query.pageSize()));
        UUID departmentPublicId = StringUtils.hasText(query.departmentId()) ? UUID.fromString(query.departmentId()) : null;
        UUID doctorPublicId = StringUtils.hasText(query.doctorId()) ? UUID.fromString(query.doctorId()) : null;
        String status = StringUtils.hasText(query.status()) ? query.status() : DEFAULT_STATUS;
        String periodCode = StringUtils.hasText(query.periodCode()) ? query.periodCode() : null;
        LocalDate scheduleDate = LocalDate.parse(query.scheduleDate());

        Page<ScheduleSlotEntity> page = scheduleSlotRepository.search(
                departmentPublicId,
                doctorPublicId,
                scheduleDate,
                periodCode,
                status,
                pageable
        );

        return PageResponse.from(page.map(this::toItem));
    }

    private ScheduleSlotItem toItem(ScheduleSlotEntity entity) {
        return new ScheduleSlotItem(
                entity.getPublicId().toString(),
                entity.getScheduleDate().toString(),
                entity.getPeriodCode(),
                entity.getStartTime() == null ? null : entity.getStartTime().toString(),
                entity.getEndTime() == null ? null : entity.getEndTime().toString(),
                entity.getTotalQuota(),
                entity.getAvailableQuota(),
                entity.getFee(),
                entity.getStatus(),
                new ScheduleSlotItem.DepartmentSimple(
                        entity.getDepartment().getPublicId().toString(),
                        entity.getDepartment().getName()
                ),
                new ScheduleSlotItem.DoctorSimple(
                        entity.getDoctor().getPublicId().toString(),
                        entity.getDoctor().getName(),
                        entity.getDoctor().getTitle()
                )
        );
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 0 : pageNo - 1;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
