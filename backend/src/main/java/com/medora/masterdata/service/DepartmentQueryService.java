package com.medora.masterdata.service;

import com.medora.common.api.PageResponse;
import com.medora.masterdata.api.query.DepartmentListQuery;
import com.medora.masterdata.api.response.DepartmentItem;
import com.medora.masterdata.domain.DepartmentEntity;
import com.medora.masterdata.repository.DepartmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DepartmentQueryService {

    private static final String DEFAULT_STATUS = "ACTIVE";

    private final DepartmentRepository departmentRepository;

    public DepartmentQueryService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public PageResponse<DepartmentItem> list(DepartmentListQuery query) {
        Pageable pageable = PageRequest.of(normalizePageNo(query.pageNo()), normalizePageSize(query.pageSize()));
        String status = StringUtils.hasText(query.status()) ? query.status() : DEFAULT_STATUS;

        Page<DepartmentEntity> page = StringUtils.hasText(query.keyword())
                ? departmentRepository.findByStatusAndNameContainingIgnoreCase(status, query.keyword().trim(), pageable)
                : departmentRepository.findByStatus(status, pageable);

        return PageResponse.from(page.map(this::toItem));
    }

    private DepartmentItem toItem(DepartmentEntity entity) {
        return new DepartmentItem(
                entity.getPublicId().toString(),
                entity.getCode(),
                entity.getName(),
                entity.getCategory(),
                entity.getDescription(),
                entity.getStatus()
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
