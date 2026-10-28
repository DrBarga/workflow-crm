DROP TRIGGER object_comment_metadata ON object_comments;;
DROP FUNCTION maintain_object_comment();;
DROP INDEX ix_object_comments_page;;
DROP INDEX ux_object_comments_sequence;;
ALTER TABLE object_comments DROP COLUMN sequence;;
ALTER TABLE object_comments DROP COLUMN edited_at;;
