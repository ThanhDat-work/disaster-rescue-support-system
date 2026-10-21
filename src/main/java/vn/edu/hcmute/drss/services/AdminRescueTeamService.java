package vn.edu.hcmute.drss.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.AdminRescueTeamRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.repositories.RescueTeamRepository;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminRescueTeamService {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "AVAILABLE",
            "BUSY",
            "OFFLINE"
    );

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final RescueTeamRepository rescueTeamRepository;

    public AdminRescueTeamService(
            RescueTeamRepository rescueTeamRepository
    ) {
        this.rescueTeamRepository = rescueTeamRepository;
    }

    @Transactional(readOnly = true)
    public Page<RescueTeam> search(
            String keyword,
            String status,
            int page,
            int size
    ) {
        return rescueTeamRepository.searchAdminTeams(
                normalizeKeyword(keyword),
                normalizeOptionalStatus(status),
                pageRequest(page, size)
        );
    }

    @Transactional(readOnly = true)
    public RescueTeam get(UUID id) {
        return rescueTeamRepository.findById(id)
                .orElseThrow(() -> new AdminDomainException(
                        null,
                        "Không tìm thấy đội cứu hộ"
                ));
    }

    @Transactional
    public RescueTeam create(AdminRescueTeamRequest request) {
        String code = normalizeCode(request.code());
        String name = normalizeRequiredText(request.name(), "name");

        rejectDuplicateCode(code, null);
        rejectDuplicateName(name, null);

        RescueTeam team = new RescueTeam();
        apply(team, request, code, name);

        return save(team);
    }

    @Transactional
    public RescueTeam update(
            UUID id,
            AdminRescueTeamRequest request
    ) {
        RescueTeam team = get(id);
        String code = normalizeCode(request.code());
        String name = normalizeRequiredText(request.name(), "name");

        rejectDuplicateCode(code, id);
        rejectDuplicateName(name, id);
        apply(team, request, code, name);

        return save(team);
    }

    @Transactional
    public RescueTeam changeStatus(UUID id, String status) {
        RescueTeam team = get(id);
        team.setStatus(normalizeRequiredStatus(status));
        return save(team);
    }
private void apply(
            RescueTeam team,
            AdminRescueTeamRequest request,
            String code,
            String name
    ) {
        team.setDisplayCode(code);
        team.setTeamName(name);
        team.setLeaderName(normalizeRequiredText(
                request.leaderName(),
                "leaderName"
        ));
        team.setContactNumber(normalizePhone(
                request.contactNumber()
        ));
        team.setStatus(normalizeRequiredStatus(request.status()));
    }

    private RescueTeam save(RescueTeam team) {
        try {
            return rescueTeamRepository.saveAndFlush(team);
        } catch (DataIntegrityViolationException exception) {
            throw new AdminDomainException(
                    null,
                    "Mã hoặc tên đội cứu hộ đã tồn tại"
            );
        }
    }

    private void rejectDuplicateCode(String code, UUID excludedId) {
        boolean duplicate = excludedId == null
                ? rescueTeamRepository
                        .existsByDisplayCodeIgnoreCase(code)
                : rescueTeamRepository
                        .existsByDisplayCodeIgnoreCaseAndIdNot(
                                code,
                                excludedId
                        );

        if (duplicate) {
            throw new AdminDomainException(
                    "code",
                    "Mã đội cứu hộ đã tồn tại"
            );
        }
    }

    private void rejectDuplicateName(String name, UUID excludedId) {
        boolean duplicate = excludedId == null
                ? rescueTeamRepository
                        .existsByTeamNameIgnoreCase(name)
                : rescueTeamRepository
                        .existsByTeamNameIgnoreCaseAndIdNot(
                                name,
                                excludedId
                        );

        if (duplicate) {
            throw new AdminDomainException(
                    "name",
                    "Tên đội cứu hộ đã tồn tại"
            );
        }
    }

    private String normalizeCode(String value) {
        return normalizeRequiredText(value, "code")
                .toUpperCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
        String phone = normalizeRequiredText(value, "contactNumber");

        if (!phone.matches("[0-9]{10,15}")) {
            throw new AdminDomainException(
                    "contactNumber",
                    "Số điện thoại phải gồm 10 đến 15 chữ số"
            );
        }

        return phone;
    }

    private String normalizeRequiredStatus(String value) {
        String status = normalizeRequiredText(value, "status")
                .toUpperCase(Locale.ROOT);

        if (!ALLOWED_STATUSES.contains(status)) {
            throw new AdminDomainException(
                    "status",
"Trạng thái đội cứu hộ không hợp lệ"
            );
        }

        return status;
    }

    private String normalizeOptionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return normalizeRequiredStatus(value);
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
                        Sort.Order.asc("displayCode"),
                        Sort.Order.asc("id")
                )
        );
    }
}
