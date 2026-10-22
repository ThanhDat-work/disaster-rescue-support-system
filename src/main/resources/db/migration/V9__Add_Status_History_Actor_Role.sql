ALTER TABLE status_histories
    ADD COLUMN actor_role VARCHAR(32) NULL
        AFTER changed_by_user_id;

UPDATE status_histories
SET actor_role = 'SYSTEM'
WHERE actor_role IS NULL;

ALTER TABLE status_histories
    MODIFY COLUMN actor_role VARCHAR(32) NOT NULL;

ALTER TABLE status_histories
    ADD CONSTRAINT status_histories_chk_actor_role
        CHECK (actor_role IN (
            'ROLE_CITIZEN',
            'ROLE_COORDINATOR',
            'ROLE_RESCUER',
            'ROLE_ADMIN',
            'SYSTEM'
        ));