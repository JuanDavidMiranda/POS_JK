export type UserRole = 'ADMIN' | 'CASHIER' | 'SUPERVISOR' | 'SUPER_ADMIN';

export interface AuthUser {
  id: string;
  tenantId: string | null;
  fullName: string;
  email: string;
  role: UserRole;
}

export interface AuthTenant {
  id: string;
  nit: string;
  businessName: string;
  tradeName?: string | null;
  planCode: string;
  subscriptionStatus: string;
  onboardingCompleted: boolean;
  onboardingStep: number;
  trialEndsAt?: string | null;
  subscriptionEndsAt?: string | null;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AuthUser;
  tenant: AuthTenant | null;
}

export interface MeResponse {
  user: AuthUser;
  tenant: AuthTenant | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  nit: string;
  businessName: string;
  tradeName: string;
  city?: string;
  phone?: string;
  adminName: string;
  adminEmail: string;
  password: string;
}

// Onboarding
export interface OnboardingTerminalInfo {
  id: string;
  name: string;
  dianPrefix: string;
  rangeFrom: number;
  rangeTo: number;
  creditNotePrefix: string;
  dianResolutionNumber?: string | null;
  dianResolutionDate?: string | null;
  dianValidFrom?: string | null;
  dianValidTo?: string | null;
  dianTechnicalKey?: string | null;
}

export interface OnboardingTenantInfo {
  id: string;
  nit: string;
  businessName: string;
  tradeName?: string | null;
  taxRegime?: string | null;
  fiscalResponsibilities?: string | null;
  city?: string | null;
  address?: string | null;
  phone?: string | null;
  email?: string | null;
  receiptFooterMessage?: string | null;
  planCode?: string | null;
  subscriptionStatus?: string | null;
  trialEndsAt?: string | null;
}

export interface OnboardingStatusResponse {
  onboardingCompleted: boolean;
  onboardingStep: number;
  tenant: OnboardingTenantInfo;
  mainTerminal: OnboardingTerminalInfo | null;
}

export interface FiscalStepRequest {
  taxRegime: string;
  fiscalResponsibilities: string;
  city?: string | null;
  address?: string | null;
  phone?: string | null;
  receiptFooterMessage?: string | null;
}

export interface DianStepRequest {
  dianPrefix: string;
  dianResolutionNumber?: string | null;
  dianResolutionDate?: string | null;
  dianValidFrom?: string | null;
  dianValidTo?: string | null;
  rangeFrom: number;
  rangeTo: number;
  dianTechnicalKey?: string | null;
  verify?: boolean;
}

export interface DianStepResponse {
  verified: boolean;
  verificationMode: string;
  verificationMessage: string;
  status: OnboardingStatusResponse;
}

export interface SeedCatalogRequest {
  action: 'SKIP' | 'SEED';
  niche?: string;
}

export interface SeedCatalogResponse {
  action: string;
  productsCreated: number;
  message: string;
  status: OnboardingStatusResponse;
}

// Super admin
export interface SuperAdminDashboard {
  totalCompanies: number;
  activeCompanies: number;
  trialCompanies: number;
  expiredOrSuspendedCompanies: number;
  totalSalesTodayCount: number;
  totalSalesTodayAmount: number;
  totalDianFoliosUsedThisPeriod: number;
}

export interface SuperAdminTenantItem {
  id: string;
  nit: string;
  businessName: string;
  tradeName?: string | null;
  planCode: string;
  subscriptionStatus: string;
  trialEndsAt?: string | null;
  subscriptionEndsAt?: string | null;
  electronicFoliosUsed: number;
  electronicFoliosLimit: number;
  branchCount: number;
  terminalCount: number;
  lastSyncAt?: string | null;
  createdAt?: string | null;
}

export interface SuperAdminTenantListResponse {
  content: SuperAdminTenantItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SuperAdminSubscriptionUpdate {
  planCode?: string;
  subscriptionStatus?: string;
  subscriptionEndsAt?: string | null;
  trialEndsAtDate?: string | null;
  electronicFoliosLimit?: number;
  addElectronicFolios?: number;
}

export interface SuperAdminTerminalHealth {
  id: string;
  name: string;
  branchName?: string | null;
  dianPrefix: string;
  active: boolean;
  updatedAt?: string | null;
}

export interface SuperAdminSyncHealth {
  lastSyncAt?: string | null;
  lastEntityType?: string | null;
}

export interface SuperAdminDianIssue {
  type: string;
  id: string;
  documentNumber?: string | null;
  dianStatus?: string | null;
  detail?: string | null;
  createdAt?: string | null;
}

export interface SuperAdminTenantHealth {
  tenantId: string;
  nit: string;
  businessName: string;
  terminals: SuperAdminTerminalHealth[];
  recentSyncs: SuperAdminSyncHealth[];
  dianPendingOrErrors: SuperAdminDianIssue[];
}
