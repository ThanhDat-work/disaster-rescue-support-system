package vn.edu.hcmute.drss.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.AdminDisasterTypeRequest;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.repositories.DisasterTypeRepository;

import java.util.Locale;

@Service
public class AdminDisasterTypeService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final DisasterTypeRepository disasterTypeRepository;

    public AdminDisasterTypeService(
            DisasterTypeRepository disasterTypeRepository
    ) {
        this.disasterTypeRepository = disasterTypeRepository;
    }

    @Transactional(readOnly = true)
    public Page<DisasterType> search(
            String keyword,
            Boolean active,
            int page,
            int size
    ) {
        return disasterTypeRepository.searchAdminDisasterTypes(
                normalizeKeyword(keyword),
                active,
                pageRequest(page, size)
        );
    }

    @Transactional(readOnly = true)
    public DisasterType get(long id) {
        return disasterTypeRepository.findById(id)
                .orElseThrow(() -> new AdminDomainException(
                        null,
                        "Không tìm thấy loại thiên tai"
                ));
    }

    @Transactional
    public DisasterType create(AdminDisasterTypeRequest request) {
        String code = normalizeCode(request.code());
        String name = normalizeRequiredText(request.name(), "name");

        rejectDuplicateCode(code, null);
        rejectDuplicateName(name, null);

        DisasterType disasterType = new DisasterType();
        disasterType.setDisasterCode(code);
        disasterType.setDisasterName(name);
        disasterType.setDescription(normalizeOptionalText(
                request.description()
        ));
        disasterType.setIsActive(true);

        return save(disasterType);
    }

    @Transactional
    public DisasterType update(
            long id,
            AdminDisasterTypeRequest request
    ) {
        DisasterType disasterType = get(id);
        String requestedCode = normalizeCode(request.code());

        if (!disasterType.getDisasterCode().equals(requestedCode)) {
            throw new AdminDomainException(
                    "code",
                    "Mã loại thiên tai không được thay đổi sau khi tạo"
            );
        }

        String name = normalizeRequiredText(request.name(), "name");
        rejectDuplicateName(name, id);
disasterType.setDisasterName(name);
        disasterType.setDescription(normalizeOptionalText(
                request.description()
        ));

        return save(disasterType);
    }

    @Transactional
    public DisasterType changeStatus(long id, boolean active) {
        DisasterType disasterType = get(id);
        disasterType.setIsActive(active);
        return save(disasterType);
    }

    private DisasterType save(DisasterType disasterType) {
        try {
            return disasterTypeRepository.saveAndFlush(disasterType);
        } catch (DataIntegrityViolationException exception) {
            throw new AdminDomainException(
                    null,
                    "Mã hoặc tên loại thiên tai đã tồn tại"
            );
        }
    }

    private void rejectDuplicateCode(String code, Long excludedId) {
        boolean duplicate = excludedId == null
                ? disasterTypeRepository
                        .existsByDisasterCodeIgnoreCase(code)
                : disasterTypeRepository
                        .existsByDisasterCodeIgnoreCaseAndIdNot(
                                code,
                                excludedId
                        );

        if (duplicate) {
            throw new AdminDomainException(
                    "code",
                    "Mã loại thiên tai đã tồn tại"
            );
        }
    }

    private void rejectDuplicateName(String name, Long excludedId) {
        boolean duplicate = excludedId == null
                ? disasterTypeRepository
                        .existsByDisasterNameIgnoreCase(name)
                : disasterTypeRepository
                        .existsByDisasterNameIgnoreCaseAndIdNot(
                                name,
                                excludedId
                        );

        if (duplicate) {
            throw new AdminDomainException(
                    "name",
                    "Tên loại thiên tai đã tồn tại"
            );
        }
    }

    private String normalizeCode(String value) {
        return normalizeRequiredText(value, "code")
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeRequiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AdminDomainException(
                    field,
                    "Giá trị bắt buộc không được để trống"
            );
        }

        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String normalized = keyword.trim();

        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new AdminDomainException(
"keyword",
                    "Từ khóa tìm kiếm tối đa 100 ký tự"
            );
        }

        return normalized;
    }

    private Pageable pageRequest(int page, int size) {
        if (page < 0) {
            throw new AdminDomainException(
                    "page",
                    "Số trang không hợp lệ"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new AdminDomainException(
                    "size",
                    "Kích thước trang phải từ 1 đến 100"
            );
        }

        return PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.asc("disasterCode"),
                        Sort.Order.asc("id")
                )
        );
    }
}
