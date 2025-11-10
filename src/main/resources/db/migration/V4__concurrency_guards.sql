ALTER TABLE IF EXISTS coordinates
    ADD CONSTRAINT uq_coordinates_xy UNIQUE (x, y);

ALTER TABLE IF EXISTS study_group
    ADD CONSTRAINT uq_study_group_coordinates UNIQUE (coordinates_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_study_group_admin
    ON study_group (group_admin_id)
    WHERE group_admin_id IS NOT NULL;
