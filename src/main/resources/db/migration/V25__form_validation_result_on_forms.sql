-- Per-form message shown on Case details. Clerk validation stays on tec_case.form_validation_result.

alter table tec_case_te9
    add column form_validation_result varchar(200) not null default 'Form valid';

alter table tec_case_pe3
    add column form_validation_result varchar(200) not null default 'Form valid';

alter table tec_case_te7
    add column form_validation_result varchar(200) not null default 'Form valid';

alter table tec_case_pe2
    add column form_validation_result varchar(200) not null default 'Form valid';
