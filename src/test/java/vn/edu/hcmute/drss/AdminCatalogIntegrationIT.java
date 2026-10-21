package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.AdminDisasterTypeRequest;
import vn.edu.hcmute.drss.dtos.AdminRescueTeamRequest;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.services.AdminDisasterTypeService;
import vn.edu.hcmute.drss.services.AdminRescueTeamService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("it")
@Transactional
class AdminCatalogIntegrationIT {

    @Autowired
    private AdminDisasterTypeService disasterService;

    @Autowired
    private AdminRescueTeamService teamService;

    @Test
    void disasterCatalogPersistsSearchesUpdatesAndChangesStatus() {
        DisasterType created = disasterService.create(
                new AdminDisasterTypeRequest(
                        "it-dis-901",
                        "Loại thiên tai Integration 901",
                        "Fixture rollback sau test"
                )
        );

        assertThat(created.getDisasterCode()).isEqualTo("IT-DIS-901");
        assertThat(disasterService.search(
                "integration 901",
                true,
                0,
                20
        ).getContent()).extracting(DisasterType::getId)
                .contains(created.getId());

        DisasterType updated = disasterService.update(
                created.getId(),
                new AdminDisasterTypeRequest(
                        "IT-DIS-901",
                        "Loại thiên tai Integration đã sửa",
                        "Đã cập nhật"
                )
        );

        assertThat(updated.getDisasterName()).contains("đã sửa");
        assertThat(disasterService.changeStatus(
                created.getId(),
                false
        ).getIsActive()).isFalse();

        assertThatThrownBy(() -> disasterService.create(
                new AdminDisasterTypeRequest(
                        "IT-DIS-901",
                        "Tên khác",
                        null
                )
        )).isInstanceOf(AdminDomainException.class);
    }

    @Test
    void teamCatalogPersistsSearchesUpdatesAndChangesStatus() {
        RescueTeam created = teamService.create(
                new AdminRescueTeamRequest(
                        "it-team-901",
                        "Đội Integration 901",
                        "Đội trưởng Integration",
                        "0912345901",
                        "AVAILABLE"
                )
        );
assertThat(created.getDisplayCode()).isEqualTo("IT-TEAM-901");
        assertThat(teamService.search(
                "integration 901",
                "AVAILABLE",
                0,
                20
        ).getContent()).extracting(RescueTeam::getId)
                .contains(created.getId());

        RescueTeam updated = teamService.update(
                created.getId(),
                new AdminRescueTeamRequest(
                        "IT-TEAM-901",
                        "Đội Integration đã sửa",
                        "Đội trưởng mới",
                        "0987654901",
                        "BUSY"
                )
        );

        assertThat(updated.getStatus()).isEqualTo("BUSY");
        assertThat(teamService.changeStatus(
                created.getId(),
                "OFFLINE"
        ).getStatus()).isEqualTo("OFFLINE");

        assertThatThrownBy(() -> teamService.create(
                new AdminRescueTeamRequest(
                        "IT-TEAM-901",
                        "Đội khác",
                        "Đội trưởng khác",
                        "0900000901",
                        "AVAILABLE"
                )
        )).isInstanceOf(AdminDomainException.class);
    }

@Test
void disasterCatalogPaginatesSearchResults() {
    for (int index = 0; index < 11; index++) {
        disasterService.create(
                new AdminDisasterTypeRequest(
                        "IT-PAGE-DIS-" + index,
                        "Pagination Disaster " + index,
                        null
                )
        );
    }

    var firstPage = disasterService.search(
            "Pagination Disaster",
            true,
            0,
            10
    );

    var secondPage = disasterService.search(
            "Pagination Disaster",
            true,
            1,
            10
    );

    assertThat(firstPage.getTotalElements()).isEqualTo(11);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.getNumberOfElements()).isEqualTo(10);
    assertThat(secondPage.getNumberOfElements()).isEqualTo(1);
}

@Test
void teamCatalogPaginatesSearchResults() {
    for (int index = 0; index < 11; index++) {
        teamService.create(
                new AdminRescueTeamRequest(
                        "IT-PAGE-TEAM-" + index,
                        "Pagination Team " + index,
                        "Đội trưởng " + index,
                        "09123456" + String.format("%02d", index),
                        "AVAILABLE"
                )
        );
    }

    var firstPage = teamService.search(
            "Pagination Team",
            "AVAILABLE",
            0,
            10
    );

    var secondPage = teamService.search(
            "Pagination Team",
            "AVAILABLE",
            1,
            10
    );

    assertThat(firstPage.getTotalElements()).isEqualTo(11);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.getNumberOfElements()).isEqualTo(10);
    assertThat(secondPage.getNumberOfElements()).isEqualTo(1);
}

}
