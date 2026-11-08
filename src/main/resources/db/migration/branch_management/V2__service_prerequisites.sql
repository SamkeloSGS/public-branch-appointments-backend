-- What customers need to bring or have done for a service, in the order shown (Service.prerequisites)
CREATE TABLE branch_management.service_prerequisite
(
    service_id UUID         NOT NULL REFERENCES branch_management.service (id) ON DELETE CASCADE,
    position   INT          NOT NULL CHECK (position >= 0),
    text       VARCHAR(200) NOT NULL,
    PRIMARY KEY (service_id, position)
);
