INSERT INTO roles (id, name, description) VALUES
  (gen_random_uuid(), 'ADMIN',   'Full system access'),
  (gen_random_uuid(), 'MANAGER', 'Team management access'),
  (gen_random_uuid(), 'SUPPORT', 'Read-only support access'),
  (gen_random_uuid(), 'USER',    'Standard user access');

INSERT INTO permissions (id, name) VALUES
  (gen_random_uuid(), 'USER_CREATE'),
  (gen_random_uuid(), 'USER_READ'),
  (gen_random_uuid(), 'USER_UPDATE'),
  (gen_random_uuid(), 'USER_DELETE'),
  (gen_random_uuid(), 'ROLE_CREATE'),
  (gen_random_uuid(), 'ROLE_ASSIGN'),
  (gen_random_uuid(), 'AUDIT_READ');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MANAGER' AND p.name IN ('USER_READ', 'USER_UPDATE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SUPPORT' AND p.name IN ('USER_READ', 'AUDIT_READ');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'USER' AND p.name = 'USER_READ';
