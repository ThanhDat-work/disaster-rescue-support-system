ALTER TABLE rescue_teams
    ADD COLUMN leader_user_id BINARY(16) NULL AFTER leader_name;

UPDATE rescue_team_members
SET role = CASE
    WHEN UPPER(role) = 'LEADER' THEN 'LEADER'
    ELSE 'MEMBER'
END;

UPDATE rescue_teams team
JOIN rescue_team_members member
  ON member.team_id = team.id
 AND member.role = 'LEADER'
 AND member.left_at IS NULL
JOIN users leader
  ON leader.id = member.user_id
SET team.leader_user_id = leader.id,
    team.leader_name = leader.full_name;

ALTER TABLE rescue_teams
    ADD CONSTRAINT fk_rescue_team_leader_user
        FOREIGN KEY (leader_user_id) REFERENCES users(id);

CREATE INDEX idx_rescue_team_leader_user
    ON rescue_teams(leader_user_id);

CREATE INDEX idx_team_member_user_active
    ON rescue_team_members(user_id, left_at, team_id);