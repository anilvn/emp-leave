-- FR44/AD-6: audit_events and access_log must be impossible to alter or erase, even by
-- someone with raw DB access running psql directly. One shared trigger function raises
-- on any UPDATE or DELETE attempt; both tables get their own BEFORE trigger using it.

CREATE FUNCTION reject_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is immutable: % is not permitted on %', TG_TABLE_NAME, TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_immutable
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();

CREATE TRIGGER access_log_immutable
    BEFORE UPDATE OR DELETE ON access_log
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
