package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.edu.hcmute.drss.dtos.AdminRescueTeamRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.repositories.RescueTeamRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRescueTeamServiceTest {

    @Mock
    private RescueTeamRepository repository;

    @InjectMocks
    private AdminRescueTeamService service;

    @Test
    void searchNormalizesKeywordStatusAndPagination() {
        Page<RescueTeam> result = new PageImpl<>(List.of());
        when(repository.searchAdminTeams(any(), any(), any()))
                .thenReturn(result);

        assertThat(service.search(
                "  rapid  ",
                " available ",
                2,
                10
        )).isSameAs(result);

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(repository).searchAdminTeams(
                org.mockito.ArgumentMatchers.eq("rapid"),
                org.mockito.ArgumentMatchers.eq("AVAILABLE"),
                pageableCaptor.capture()
        );

        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void rejectsInvalidStatusAndPagination() {
        assertThatThrownBy(() -> service.search(
                null,
                "UNKNOWN",
                0,
                20
        )).isInstanceOf(AdminDomainException.class);

        assertThatThrownBy(() -> service.search(null, null, -1, 20))
                .isInstanceOf(AdminDomainException.class);

        assertThatThrownBy(() -> service.search(null, null, 0, 101))
                .isInstanceOf(AdminDomainException.class);
    }

    @Test
    void createNormalizesCodeStatusAndText() {
        when(repository.saveAndFlush(any(RescueTeam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RescueTeam created = service.create(request(
                " team-002 ",
                " Đội số 2 ",
                " available ",
                "0912345678"
));

        assertThat(created.getDisplayCode()).isEqualTo("TEAM-002");
        assertThat(created.getTeamName()).isEqualTo("Đội số 2");
        assertThat(created.getStatus()).isEqualTo("AVAILABLE");
        assertThat(created.getContactNumber()).isEqualTo("0912345678");
    }

    @Test
    void rejectsDuplicateCodeAndName() {
        when(repository.existsByDisplayCodeIgnoreCase("TEAM-002"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request(
                "TEAM-002",
                "Đội số 2",
                "AVAILABLE",
                "0912345678"
        )))
                .isInstanceOfSatisfying(
                        AdminDomainException.class,
                        exception -> assertThat(exception.getField())
                                .isEqualTo("code")
                );

        when(repository.existsByDisplayCodeIgnoreCase("TEAM-003"))
                .thenReturn(false);
        when(repository.existsByTeamNameIgnoreCase("Đội số 2"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request(
                "TEAM-003",
                "Đội số 2",
                "AVAILABLE",
                "0912345678"
        )))
                .isInstanceOfSatisfying(
                        AdminDomainException.class,
                        exception -> assertThat(exception.getField())
                                .isEqualTo("name")
                );
    }

    @Test
    void rejectsInvalidPhone() {
        assertThatThrownBy(() -> service.create(request(
                "TEAM-002",
                "Đội số 2",
                "AVAILABLE",
                "09A123"
        )))
                .isInstanceOfSatisfying(
                        AdminDomainException.class,
                        exception -> assertThat(exception.getField())
                                .isEqualTo("contactNumber")
                );
    }

    @Test
    void updateAndChangeStatusPersistChanges() {
        UUID id = UUID.randomUUID();
        RescueTeam existing = team(id);

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(RescueTeam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RescueTeam updated = service.update(
                id,
                request(
                        "TEAM-009",
                        "Đội cập nhật",
                        "BUSY",
                        "0987654321"
                )
        );

        assertThat(updated.getDisplayCode()).isEqualTo("TEAM-009");
        assertThat(updated.getTeamName()).isEqualTo("Đội cập nhật");
        assertThat(updated.getStatus()).isEqualTo("BUSY");

        assertThat(service.changeStatus(id, "offline").getStatus())
                .isEqualTo("OFFLINE");
    }

    @Test
    void convertsConstraintRaceToDomainError() {
when(repository.saveAndFlush(any(RescueTeam.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(request(
                "TEAM-002",
                "Đội số 2",
                "AVAILABLE",
                "0912345678"
        )))
                .isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("đã tồn tại");
    }

    private AdminRescueTeamRequest request(
            String code,
            String name,
            String status,
            String phone
    ) {
        return new AdminRescueTeamRequest(
                code,
                name,
                "Nguyễn Văn Đội Trưởng",
                phone,
                status
        );
    }

    private RescueTeam team(UUID id) {
        RescueTeam team = new RescueTeam();
        team.setId(id);
        team.setDisplayCode("TEAM-001");
        team.setTeamName("Đội hiện tại");
        team.setLeaderName("Đội trưởng");
        team.setContactNumber("0912345678");
        team.setStatus("AVAILABLE");
        return team;
    }
}
