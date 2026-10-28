CREATE TEMPORARY TABLE task_seed_templates (
    id uuid PRIMARY KEY,
    status_code varchar(64) NOT NULL,
    contract_type_code varchar(64),
    UNIQUE NULLS NOT DISTINCT (status_code,contract_type_code)
) ON COMMIT DROP;

CREATE TEMPORARY TABLE task_seed_items (
    id uuid PRIMARY KEY,
    template_id uuid NOT NULL REFERENCES task_seed_templates(id),
    name varchar(250) NOT NULL CHECK(length(btrim(name))>0),
    days integer NOT NULL CHECK(days BETWEEN 0 AND 3650),
    position integer NOT NULL CHECK(position>=0),
    assignment varchar(24),
    assignee_id uuid,
    assignee_role varchar(64),
    needs_review boolean NOT NULL,
    recipient_role varchar(64),
    escalation_days integer CHECK(escalation_days BETWEEN 0 AND 3650),
    UNIQUE(template_id,position),
    UNIQUE(id,template_id)
) ON COMMIT DROP;

CREATE TEMPORARY TABLE task_seed_dependencies (
    template_id uuid NOT NULL,
    item_id uuid NOT NULL,
    predecessor_id uuid NOT NULL,
    PRIMARY KEY(item_id,predecessor_id),
    FOREIGN KEY(item_id,template_id) REFERENCES task_seed_items(id,template_id),
    FOREIGN KEY(predecessor_id,template_id) REFERENCES task_seed_items(id,template_id)
) ON COMMIT DROP;
