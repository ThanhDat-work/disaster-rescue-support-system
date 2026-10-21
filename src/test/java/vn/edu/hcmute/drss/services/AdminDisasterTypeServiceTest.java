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
import vn.edu.hcmute.drss.dtos.AdminDisasterTypeRequest;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.repositories.DisasterTypeRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDisasterTypeServiceTest {

    @Mock
    private DisasterTypeRepository repository;

    @InjectMocks
    private AdminDisasterTypeService service;

    @Test
    void searchNormalizesKeywordAndBuildsPageRequest() {
        Page<DisasterType> result = new PageImpl<>(List.of());
        when(repository.searchAdminDisasterTypes(
                any(),
                any(),
                any()
        )).thenReturn(result);

        assertThat(service.search("  flood  ", true, 1, 20))
                .isSameAs(result);

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(repository).searchAdminDisasterTypes(
                org.mockito.ArgumentMatchers.eq("flood"),
                org.mockito.ArgumentMatchers.eq(true),
                pageableCaptor.capture()
        );

        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void rejectsKeywordLongerThanOneHundredCharacters() {
        assertThatThrownBy(() -> service.search(
                "x".repeat(101),
                null,
                0,
                20
        ))
                .isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("100");
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatThrownBy(() -> service.search(null, null, -1, 20))
                .isInstanceOf(AdminDomainException.class);

        assertThatThrownBy(() -> service.search(null, null, 0, 101))
                .isInstanceOf(AdminDomainException.class);
    }

    @Test
    void createNormalizesCodeAndOptionalDescription() {
        when(repository.saveAndFlush(any(DisasterType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
DisasterType created = service.create(new AdminDisasterTypeRequest(
                "  dis-003  ",
                "  Động đất  ",
                "  Rung chấn mặt đất  "
        ));

        assertThat(created.getDisasterCode()).isEqualTo("DIS-003");
        assertThat(created.getDisasterName()).isEqualTo("Động đất");
        assertThat(created.getDescription())
                .isEqualTo("Rung chấn mặt đất");
        assertThat(created.getIsActive()).isTrue();
    }

    @Test
    void rejectsDuplicateCodeAndName() {
        when(repository.existsByDisasterCodeIgnoreCase("DIS-003"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new AdminDisasterTypeRequest(
                        "dis-003",
                        "Động đất",
                        null
                )
        ))
                .isInstanceOfSatisfying(
                        AdminDomainException.class,
                        exception -> assertThat(exception.getField())
                                .isEqualTo("code")
                );

        when(repository.existsByDisasterCodeIgnoreCase("DIS-004"))
                .thenReturn(false);
        when(repository.existsByDisasterNameIgnoreCase("Động đất"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new AdminDisasterTypeRequest(
                        "dis-004",
                        "Động đất",
                        null
                )
        ))
                .isInstanceOfSatisfying(
                        AdminDomainException.class,
                        exception -> assertThat(exception.getField())
                                .isEqualTo("name")
                );
    }

    @Test
    void updateRejectsChangingImmutableCode() {
        DisasterType existing = disaster(1L, "DIS-001", "Bão lũ");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(
                1L,
                new AdminDisasterTypeRequest(
                        "DIS-999",
                        "Bão lũ",
                        null
                )
        ))
                .isInstanceOfSatisfying(
                        AdminDomainException.class,
                        exception -> assertThat(exception.getField())
                                .isEqualTo("code")
                );
    }

    @Test
    void updateAndChangeStatusPersistChanges() {
        DisasterType existing = disaster(1L, "DIS-001", "Bão lũ");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(DisasterType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DisasterType updated = service.update(
                1L,
                new AdminDisasterTypeRequest(
                        "DIS-001",
"Bão và lũ",
                        "Cập nhật"
                )
        );

        assertThat(updated.getDisasterName()).isEqualTo("Bão và lũ");
        assertThat(service.changeStatus(1L, false).getIsActive())
                .isFalse();
    }

    @Test
    void convertsConstraintRaceToDomainError() {
        when(repository.saveAndFlush(any(DisasterType.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(
                new AdminDisasterTypeRequest(
                        "DIS-003",
                        "Động đất",
                        null
                )
        ))
                .isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("đã tồn tại");
    }

    private DisasterType disaster(Long id, String code, String name) {
        DisasterType disasterType = new DisasterType();
        disasterType.setId(id);
        disasterType.setDisasterCode(code);
        disasterType.setDisasterName(name);
        disasterType.setIsActive(true);
        return disasterType;
    }
}
