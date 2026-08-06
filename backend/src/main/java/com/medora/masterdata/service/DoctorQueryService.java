package com.medora.masterdata.service;

import com.medora.common.api.PageResponse;
import com.medora.masterdata.api.query.DoctorListQuery;
import com.medora.masterdata.api.response.DoctorDetailResponse;
import com.medora.masterdata.api.response.DoctorItem;
import com.medora.masterdata.domain.DoctorEntity;
import com.medora.masterdata.repository.DoctorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class DoctorQueryService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final DoctorRepository doctorRepository;

    public DoctorQueryService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorItem> list(DoctorListQuery query) {
        Pageable pageable = PageRequest.of(normalizePageNo(query.pageNo()), normalizePageSize(query.pageSize()));
        String status = StringUtils.hasText(query.status()) ? query.status() : DEFAULT_STATUS;

        UUID departmentPublicId = StringUtils.hasText(query.departmentId())
                ? UUID.fromString(query.departmentId())
                : null;

        String keyword = StringUtils.hasText(query.keyword()) ? query.keyword().trim() : null;

        Page<DoctorEntity> page = doctorRepository.search(departmentPublicId, status, keyword, pageable);
        return PageResponse.from(page.map(this::toItem));
    }

    @Transactional(readOnly = true)
    public DoctorDetailResponse getDetail(String doctorId) {
        DoctorEntity entity = doctorRepository.findByPublicId(UUID.fromString(doctorId))
                .orElseThrow(() -> new IllegalArgumentException("doctor not found"));

        return new DoctorDetailResponse(
                entity.getPublicId().toString(),
                entity.getCode(),
                entity.getName(),
                entity.getTitle(),
                entity.getSpecialty(),
                entity.getIntro(),
                entity.getStatus(),
                entity.getConsultationFee(),
                new DoctorItem.DepartmentSimple(
                        entity.getDepartment().getPublicId().toString(),
                        entity.getDepartment().getName()
                )
        );
    }

    private DoctorItem toItem(DoctorEntity entity) {
        return new DoctorItem(
                entity.getPublicId().toString(),
                entity.getCode(),
                entity.getName(),
                entity.getTitle(),
                entity.getSpecialty(),
                entity.getStatus(),
                new DoctorItem.DepartmentSimple(
                        entity.getDepartment().getPublicId().toString(),
                        entity.getDepartment().getName()
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
