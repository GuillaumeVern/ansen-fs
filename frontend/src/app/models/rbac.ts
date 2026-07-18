export interface PermissionSummary {
  id: number;
  name: string;
}

export interface RoleSummary {
  id: number;
  name: string;
  permissions: PermissionSummary[];
}

export interface UserSummary {
  id: number;
  username: string;
  roles: RoleSummary[];
}
