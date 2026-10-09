alter table tec_case_document
    add column general_application_id uuid references tec_case_general_application (id);

create index tec_case_document_general_application_id_idx
    on tec_case_document (general_application_id);
