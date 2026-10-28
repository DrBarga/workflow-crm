export interface Profile {
  id: string;
  email: string;
  fullName: string;
  role: string;
  regionId: string | null;
  telegram: { linked: boolean; revision: string | null };
}
