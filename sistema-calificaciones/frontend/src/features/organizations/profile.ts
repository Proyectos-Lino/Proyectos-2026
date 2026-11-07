export interface OrganizationProfileInput {
  legalName: string; taxId: string; address: string; city: string; country: string; phone: string; email: string
  website: string; office: string; signatoryName: string; signatoryTitle: string
}
export interface OrganizationProfile extends OrganizationProfileInput { id: number; name: string; code: string; hasLogo: boolean }
export const emptyProfile: OrganizationProfileInput = {
  legalName: '', taxId: '', address: '', city: '', country: '', phone: '', email: '', website: '', office: '', signatoryName: '', signatoryTitle: '',
}
